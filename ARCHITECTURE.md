# MusicWave backend architecture

## Objective

Migrate every observable capability of the desktop downloader into a backend that runs locally through Docker Engine and can later be deployed without redesigning its core boundaries.

## Runtime topology

```text
Astro client
        |
        | REST + SSE
        v
MusicWave API ---------> PostgreSQL
        |                    ^
        | outbox             | durable state
        v                    |
     RabbitMQ ----------> MusicWave Worker
                             |      |
                             |      +--> yt-dlp + Deno/EJS + FFmpeg
                             +---------> ArtifactStorage

Worker -- progress/cancel --> Redis Streams --> API SSE
```

## Ownership

- **API:** validates commands, enforces ownership and quotas, persists jobs, publishes outbox messages, exposes REST/SSE and OpenAPI.
- **Worker:** consumes durable commands, claims jobs idempotently, controls external processes, persists transitions, stores artifacts and emits progress.
- **PostgreSQL:** authoritative jobs, users, settings, artifacts and outbox records.
- **RabbitMQ:** durable work delivery with publisher confirms, manual acknowledgements and dead-letter handling. Classified delayed retries are represented as PostgreSQL outbox rows with `available_at`, avoiding broker plug-in coupling.
- **Redis:** ephemeral progress stream, distributed cancellation signals, rate limits and short-lived coordination.
- **ArtifactStorage:** local filesystem adapter for local compatibility and an S3 adapter. SeaweedFS provides the local S3 service without coupling the application to a storage vendor.

## Module boundaries

- `libs:domain`: entities, value objects, states and domain rules; no framework dependencies.
- `libs:application`: use cases and ports; depends only on domain.
- `libs:infrastructure`: adapters for persistence, messaging, Redis, storage and external tools.
- `apps:api`: HTTP/SSE composition root.
- `apps:worker`: asynchronous worker composition root.

External media commands are passed to `ProcessBuilder` as an argument vector, never interpolated into a shell command. Captured output is bounded, every invocation has a deadline, and timeout handling terminates the process tree. The worker periodically probes its locally installed tools and upserts a heartbeat in PostgreSQL; the API reports only fresh worker inventories, so its tool status represents actual execution capacity rather than the API container.

Download ingestion is transactional. Each single, bulk or TXT command creates a `download_request` batch and its accepted `download_job` rows in one PostgreSQL transaction. A partial unique index on owner, operation and `Idempotency-Key` makes retries return the original rows and rejects key reuse with a changed fingerprint. Invalid bulk lines never erase valid entries, and all reads include the server-derived owner in their predicate.

YouTube jobs begin with the abbreviated URL as a usable fallback title. A bounded API scheduler reads durable pending title work and calls the fixed YouTube oEmbed endpoint outside the creation transaction. Success, retry schedule, attempt count and terminal unavailability are persisted, making lookup restart-safe. Duplicate lookup by multiple API instances is harmless because updates are conditional on `title_status=PENDING`; a leasing optimization can be added if measurement shows unnecessary traffic.

Queue start locks only the current owner's pending rows, changes them to `QUEUED` and inserts one outbox event per job in the same PostgreSQL transaction. API and worker instances lease ready outbox rows with `FOR UPDATE SKIP LOCKED`, publish persistent messages with correlated confirms, and mark publication only after broker acknowledgement. A crash after broker confirmation but before the database update may redeliver the event; the worker inbox has unique message and job keys, so that case is harmless.

The RabbitMQ consumer uses manual acknowledgements. It commits a durable shared inbox receipt and dispatch checkpoint before ACK, allowing executors to claim the work from PostgreSQL without losing it on a worker restart. If worker tools are unavailable, the consumer writes a delayed retry outbox event with bounded exponential backoff before ACK. Exhausted or permanent failures mark the job failed and create the DLQ outbox event transactionally. Malformed messages without a trustworthy job identity are rejected directly into the broker DLQ.

Each worker has a fixed-size execution pool (ten slots in the current local profile). Free slots atomically claim inbox rows with `FOR UPDATE SKIP LOCKED`; a partitioned owner rank interleaves eligible owners and a configurable per-owner in-flight ceiling, together with the persisted 1–10 owner preference, bounds admission. Active leases are renewed and an expired lease makes interrupted work recoverable by another worker. Rabbit delivery and long-running execution are deliberately separated, so broker consumer timeouts do not bound media duration. Ten is a functional limit, not an unmeasured VPS capacity claim.

Every execution owns `<media-root>/staging/<job-uuid>`. The worker invokes yt-dlp as an argument vector with the pinned Deno JavaScript runtime, `bestaudio/best`, FFmpeg MP3 extraction and `192K` default quality. yt-dlp and Deno downloads are version-pinned and checksum-verified for amd64/arm64 while the image is built. Output is streamed line by line for progress, speed, ETA and conversion checkpoints. Reported paths are accepted only after canonical containment, regular-file, `.mp3` and non-empty checks; partial files are removed after cancellation/failure. YouTube HTTP 429 and bot-authentication challenges are exposed as stable `YOUTUBE_RATE_LIMITED` and `YOUTUBE_AUTH_REQUIRED` business errors instead of one opaque process exit code.

After validation, the worker stores the MP3 through a framework-free artifact port under a deterministic `owners/<owner>/jobs/<job>/artifact.mp3` key. The local adapter uses contained paths and atomic replacement; the S3 adapter uses the same logical key against any compatible endpoint. Only after upload succeeds does one database transaction persist checksum/metadata, a mandatory expiry instant and `AVAILABLE` state before marking the job `COMPLETED`. Staging is then removed. A lost cancellation race triggers compensating object deletion, and deterministic replacement makes a crash-before-commit retry harmless.

Retrieval is owner-scoped and one-shot. The API atomically leases `AVAILABLE` as `DELIVERING`, streams with no-store headers, then persists `DELETE_PENDING` before removing the object and artifact row. Aborted responses release the lease. A bounded reconciler uses leased `FOR UPDATE SKIP LOCKED` claims to delete expired, abandoned or previously failed artifacts; a durable removal reason on the job distinguishes `DOWNLOADED` from `EXPIRED`. This preserves crash recovery without retaining user media indefinitely. See [ADR-0006](docs/ADR-0006-one-shot-ephemeral-artifacts.md).

Cancellation is persisted first. The running process polls that database signal, terminates its descendants and parent, cleans partial workspace content, then records `CANCELLED`. A worker shutdown kills its local process but leaves the inbox lease recoverable instead of falsely recording user cancellation.

Realtime delivery is deliberately downstream of persistence. API queue/control use cases and worker execution publish lifecycle snapshots to one bounded Redis Stream only after their PostgreSQL operation succeeds. A Redis outage is logged but never rolls back queueing, cancellation or media execution. Progress publication reuses the durable checkpoint throttle instead of emitting every yt-dlp line.

The API tails the global stream with bounded blocking readers and filters every record using the server-derived owner before SSE delivery. Redis IDs are exposed as SSE IDs for ordered replay through `Last-Event-ID`. New connections begin at the current tail; reconnects resume after the acknowledged ID. If retention has removed the requested history, `stream-reset` instructs the client to rebuild from the authoritative paginated download API. Heartbeats, a fixed connection pool and deterministic release bound local resource use. Capacity testing verified the configured 100-connection local boundary and 503 rejection beyond it; `docs/PERFORMANCE.md` deliberately limits that evidence to the measured machine.

Production defaults to stateless JWT resource-server mode. Spring Security validates signature, issuer, audience, lifetime and subject, then a framework-neutral current-owner port resolves the `iss|sub` identity to a stable PostgreSQL user. Docker development explicitly selects a local single-owner adapter; neither mode accepts client-selected owner identifiers. See [ADR-0005](docs/ADR-0005-jwt-capacity-and-outbound-security.md).

Active-job admission is serialized with a PostgreSQL owner-row lock, so concurrent API instances cannot exceed the configured owner quota and idempotent replay is checked first. Mutations consume a per-owner fixed-window allowance through one atomic Redis script and fail closed if admission state is unavailable.

Outbound media URLs pass a DNS-aware guard both at ingestion and immediately before worker execution. Every resolved address must be publicly routable; local names, mixed public/private answers, loopback, private, link-local, multicast, unspecified and reserved ranges are rejected. This narrows SSRF and DNS-rebinding risk, while VPS egress filtering remains the stronger deployment boundary.

## Non-negotiable behavior parity

- One URL, multiple URLs and TXT import.
- YouTube title lookup before or during queueing.
- Pending, queued, downloading, converting, completed, failed and cancelled states.
- Configurable bounded concurrency.
- Start, cancel, retry and clear operations.
- Progress, speed and ETA updates.
- MP3 output at the configured quality.
- Tool availability and version reporting.

## Scalability boundaries

API instances and worker instances scale independently. Worker process concurrency remains bounded even when queue depth is high. Per-user quotas and a dispatcher prevent a single user from monopolizing capacity.

Docker Compose is the local and single-host runtime. It is not a multi-host high-availability guarantee. Capacity claims require load-test evidence covering API latency, SSE connections, queue delay, worker throughput, storage and network use.

## Observability

- API and worker write one ECS JSON record per standard-output line. A bounded, sanitized `X-Request-ID` is echoed to clients and placed in MDC; job and message IDs are scoped only to relevant lifecycle logs. The collector reads Docker's local JSON log files through a read-only mount and forwards their ECS bodies to Loki.
- Spring Boot Actuator exposes health, readiness, liveness and Prometheus endpoints. Prometheus scrapes both applications and RabbitMQ; MusicWave gauges and counters use only bounded status, state and outcome labels. Owner, request, message and job IDs never become metric dimensions.
- Queueing stores the active trace/span context in the transactional outbox. Publication rehydrates that context, RabbitTemplate observations inject it into AMQP headers, the listener persists its consumer context in the inbox, and worker execution resumes it. This preserves one Tempo lifecycle trace across transactions, threads, broker delivery and restarts.
- OTLP trace export is best effort through the OpenTelemetry Collector. API and worker have no readiness dependency on the collector, Loki, Tempo, Prometheus or Grafana; short exporter timeouts ensure telemetry degradation cannot roll back durable PostgreSQL work.
- Grafana provisions Prometheus, Loki and Tempo data sources, a MusicWave overview dashboard and ECS-log-to-Tempo links. Prometheus loads alerts for application availability, sustained server errors, outbox backlog and prolonged worker saturation. The measured local envelope and the requirement to recalibrate on the target VPS are recorded in `docs/PERFORMANCE.md`.

## Security boundaries

Validate allowed URL schemes and hosts, sanitize filenames, isolate artifacts by owner, reject client-provided ownership, cap request/job/file sizes, apply per-user rate and concurrency limits, and keep secrets outside Git.
