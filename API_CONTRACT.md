# MusicWave API contract

Base path: `/api/v1`

This is the stable v1 contract. Its generated OpenAPI 3.1 snapshot is `docs/openapi/musicwave-api-v1.json`; `scripts/verify-openapi.sh` checks the live API, normalized snapshot and complete 17-operation set.

## Authentication and shared-capacity controls

Production defaults to OAuth 2.0 resource-server mode. Every endpoint except `/system/status`, `/tools/status`, `/actuator/health/**` and `/error` requires `Authorization: Bearer <JWT>`. The API validates the token signature, expiration/not-before time, configured issuer and audience, and a nonblank subject. The internal owner is resolved from the validated `iss|sub` pair in PostgreSQL; client headers, parameters and bodies can never select an owner.

Docker development explicitly selects `MUSICWAVE_SECURITY_MODE=local`, which retains one configured development owner. This mode is not suitable for a public deployment. JWT verification supports a configured JWK Set URI or an HS256 secret of at least 32 bytes; MusicWave validates tokens but does not issue them.

Missing or invalid credentials return HTTP 401 with `AUTHENTICATION_REQUIRED`. Authenticated access denied by policy returns HTTP 403 with `ACCESS_DENIED`. Owner-scoped resources deliberately return `DOWNLOAD_NOT_FOUND` for foreign identifiers so their existence is not disclosed.

Every mutating download, queue or settings request consumes an atomic per-owner Redis allowance. Responses include `X-RateLimit-Limit` and `X-RateLimit-Remaining`; exhausted capacity returns HTTP 429 with `RATE_LIMIT_EXCEEDED` and `Retry-After`. Admission fails closed with HTTP 503 and `RATE_LIMIT_UNAVAILABLE` when Redis is unavailable. Creating work beyond the configured active-job quota returns HTTP 409 with `DOWNLOAD_QUOTA_EXCEEDED`; an exact idempotent replay is resolved before quota admission and does not consume another job slot.

## System

| Method | Path | Purpose |
|---|---|---|
| GET | `/system/status` | Service identity and readiness summary |
| GET | `/tools/status` | yt-dlp and FFmpeg availability/version |

### Tool status response

`GET /tools/status` returns HTTP 200 even when no capable worker is active. Its top-level `status` is:

- `READY` when every active worker reports both tools available.
- `DEGRADED` when at least one active worker is ready and another is not.
- `UNAVAILABLE` when there is no active ready worker.

Only worker heartbeats inside the configured freshness window are included. `workers[].tools` contains `name`, `available`, `executable`, `version` and a diagnostic `detail`. Tool names are `ytDlp` and `ffmpeg`.

## Downloads

| Method | Path | Purpose |
|---|---|---|
| POST | `/downloads` | Create one pending job |
| POST | `/downloads/bulk` | Create jobs from a URL list |
| POST | `/downloads/import` | Create jobs from a TXT upload |
| GET | `/downloads` | Paginated jobs owned by the current user |
| GET | `/downloads/{id}` | One owned job |
| POST | `/downloads/{id}/cancel` | Cancel one owned job |
| POST | `/downloads/{id}/retry` | Reset one owned failed job to pending |
| GET | `/downloads/{id}/artifact` | Stream the completed MP3 |

### Download creation

`POST /downloads` accepts:

```json
{"url":"https://www.youtube.com/watch?v=..."}
```

`POST /downloads/bulk` accepts `{"urls":[...]}` with at most 100 entries. `POST /downloads/import` accepts multipart form data with a `file` part containing at most 256 KiB of valid UTF-8 text and one URL per line. A UTF-8 BOM and blank lines are ignored.

All three endpoints accept an optional `Idempotency-Key` of at most 255 characters. A new request returns HTTP 201. Replaying the same operation, key and canonical payload returns the original request/jobs with HTTP 200 and `replayed=true`. Reusing that key with different content returns HTTP 409 and code `IDEMPOTENCY_KEY_REUSED`.

Single creation rejects an invalid URL. Bulk and TXT creation preserve desktop behavior by creating every valid HTTP(S) entry while returning invalid nonblank entries in `rejected` with their one-based position, stable code and message. If no valid entry remains, the whole request is rejected.

URL hosts are resolved before acceptance. Local names and any answer containing loopback, private, link-local, multicast, unspecified or reserved address space are rejected with `URL_DESTINATION_NOT_ALLOWED`; unresolvable names use `URL_HOST_UNRESOLVED`. The worker repeats this check immediately before external execution to reduce DNS-rebinding exposure. Deployment egress policy remains a required second boundary.

Creation responses contain `requestId`, `replayed`, counts, `jobs` and `rejected`. New jobs have `status=PENDING`. Their `titleStatus` is `PENDING` for recognized YouTube hosts and `NOT_APPLICABLE` otherwise; title lookup later changes it to `RESOLVED` or `UNAVAILABLE` while retaining the abbreviated source URL as fallback.

### Download queries

`GET /downloads?page=0&size=20` accepts page sizes from 1 through 100 and returns `items`, `page`, `size`, `totalElements` and `totalPages`. Ordering is always `createdAt DESC, id DESC`.

`GET /downloads/{id}` returns HTTP 404 with code `DOWNLOAD_NOT_FOUND` when the identifier does not belong to the current owner. Ownership is always supplied by the server-side principal adapter, never a payload or query parameter.

### Download execution controls

`POST /downloads/{id}/cancel` is owner-scoped. A `PENDING` or `QUEUED` job becomes `CANCELLED` immediately and returns HTTP 200. For `DOWNLOADING` or `CONVERTING`, the API durably records the cancellation request and returns HTTP 202 while the worker terminates the process tree and removes partial files. Repeating cancellation is harmless.

`POST /downloads/{id}/retry` accepts only an owned `FAILED` job. It clears execution error/checkpoint state, increments `retryCount` and returns the job to `PENDING`; `POST /queue/start` dispatches it again. Other states return HTTP 409 with code `DOWNLOAD_NOT_RETRYABLE`.

Both responses contain `id`, `status` and `cancellationRequested`. A successful execution reaches `COMPLETED` with progress 100 only after the MP3 is stored and its durable artifact metadata is committed.

### Artifact retrieval

`GET /downloads/{id}/artifact` first resolves the server-derived owner and verifies the owned job and its authoritative PostgreSQL artifact metadata. A completed MP3 is a temporary, one-shot artifact. The first request atomically leases it and streams it as `audio/mpeg` with its byte length, a safe UTF-8 attachment filename, `X-Content-Type-Options: nosniff` and no-store cache headers; storage keys, filesystem paths and provider credentials are never exposed. A complete HTTP transfer immediately schedules durable object deletion and removes the artifact metadata row. The job retains only its removal time/reason for audit.

Every artifact has a mandatory expiry instant (one hour by default). A reconciler deletes expired objects, abandoned delivery leases and failed deletions in bounded batches. An unknown or cross-owner identifier returns HTTP 404 `DOWNLOAD_NOT_FOUND`. An owned job that is not completed, or whose artifact metadata is not yet committed, returns HTTP 409 `ARTIFACT_NOT_READY`. A concurrent delivery returns HTTP 409 `ARTIFACT_DELIVERY_IN_PROGRESS`. A second request after successful transfer returns HTTP 410 `ARTIFACT_ALREADY_DOWNLOADED`; expiry returns HTTP 410 `ARTIFACT_EXPIRED`. Missing object data returns HTTP 410 `ARTIFACT_MISSING`; a transient storage failure returns HTTP 503 `ARTIFACT_STORAGE_UNAVAILABLE`.

## Queue

| Method | Path | Purpose |
|---|---|---|
| POST | `/queue/start` | Queue the current user's pending jobs |
| POST | `/queue/cancel` | Cancel the current user's active jobs |
| DELETE | `/queue/completed` | Remove completed jobs from the visible queue |
| DELETE | `/queue` | Cancel active jobs and clear the visible queue |

### Queue start

`POST /queue/start` has no request body. Ownership comes from the server-side principal adapter. In one PostgreSQL transaction it changes every current-owner `PENDING` job to `QUEUED` and creates one outbox event per changed job.

A call that queues work returns HTTP 202:

```json
{
  "queuedCount": 2,
  "jobIds": ["...", "..."]
}
```

Repeating the call after those jobs are already queued returns HTTP 200 with `queuedCount=0`; it never creates duplicate dispatch events. Publication and execution are asynchronous. A worker moves durably received work through `DOWNLOADING`, optionally `CONVERTING`, and a terminal state.

`POST /queue/cancel` has no body. It immediately cancels owned pending/queued work and records cancellation requests for owned downloading/converting work. The response contains `cancelledCount` and `cancellationRequestedCount`.

`DELETE /queue/completed` hides the current owner's completed jobs from list/detail/artifact views and makes their temporary artifacts immediately eligible for deletion. `DELETE /queue` does the same for every visible artifact, immediately cancels pending/queued work and durably requests cancellation of downloading/converting work. Both operations are idempotent and return `clearedCount`, `cancelledCount` and `cancellationRequestedCount`. Job/audit rows remain durable, while MP3 bytes are destructively removed; an already-running hidden job can still finish cancellation safely.

## Settings and events

| Method | Path | Purpose |
|---|---|---|
| GET | `/settings` | Read current user settings |
| PUT | `/settings` | Update concurrency and MP3 quality preferences |
| GET | `/events` | SSE stream for owned job events |

### User settings

`GET /settings` creates and returns the current owner's defaults when no preference row exists. `PUT /settings` accepts `maxConcurrency` from 1 through 10 and `audioQuality` as one of `64K`, `96K`, `128K`, `160K`, `192K`, `256K` or `320K`. Quality is normalized to uppercase. The worker applies the persisted quality to newly claimed executions and treats `maxConcurrency` as a per-owner preference bounded by the deployment-wide safety ceiling. The local profile exposes ten global/per-owner execution slots, so an owner selecting 10 can run ten independent jobs while later jobs remain durably queued; production values still require VPS-specific capacity measurement.

Object-storage selection is intentionally deployment-owned rather than user-controlled: accepting storage endpoints or credentials from a client would violate the backend trust boundary.

### Realtime events

`GET /events` produces `text/event-stream`. Ownership comes exclusively from the server-side principal adapter; the endpoint accepts no owner query parameter or payload. A connection without `Last-Event-ID` starts at the current Redis Stream tail and receives new events. A reconnect with a valid Redis ID such as `1720000000000-4` resumes strictly after that event, so the acknowledged event is not duplicated.

Lifecycle messages use event name `download`. The SSE `id` is also present as `eventId` in the JSON data, followed by `jobId`, `status`, `progress`, nullable `speed`, `eta`, `title` and `errorCode`, and the UTC `timestamp`. Events cover `QUEUED`, retry to `PENDING`, `DOWNLOADING`, throttled progress checkpoints, `CONVERTING`, `COMPLETED`, `FAILED` and `CANCELLED`.

Redis history is intentionally bounded and ephemeral. If `Last-Event-ID` predates the oldest retained entry, the API emits a `stream-reset` event with code `EVENT_HISTORY_EXPIRED` and a `resumeAt` cursor. The client must refresh `GET /downloads` from authoritative PostgreSQL state before applying later events. A cursor ahead of the stream produces the same recovery flow with code `EVENT_CURSOR_AHEAD`. Malformed IDs return HTTP 400 with code `INVALID_EVENT_CURSOR`.

Idle connections receive SSE comments as heartbeats. Connection capacity is bounded; a connection rejected before streaming starts returns HTTP 503 with code `REALTIME_CAPACITY_EXHAUSTED`. Redis publication occurs only after the durable PostgreSQL transition and is best effort, so clients must always treat REST state as authoritative.

## Contract rules

- JSON uses UTC ISO-8601 timestamps and UUID identifiers.
- Mutation endpoints accept an `Idempotency-Key` when duplicates are possible.
- Errors use RFC 9457 problem details with a stable application error code.
- List endpoints are paginated and have deterministic ordering.
- SSE events contain an event ID, job ID, state, progress snapshot and timestamp.
- Ownership comes from the authenticated principal, never request payloads.
- Production defaults to validated JWT mode; local-owner mode must be selected explicitly for development.
