# Recovery evidence

Validated 2026-10-11 against the Docker stack and Flyway V8.

## Broker and worker restart

RabbitMQ and the worker were stopped before a download was created and queued. The API returned the accepted job while PostgreSQL showed `QUEUED`, an unpublished outbox row and three failed publication attempts. After RabbitMQ started, its execution queue contained one message and the same outbox row became published after six attempts. Starting the worker consumed the message and produced the expected terminal `FAILED/YT_DLP_EXIT_CODE` result for the deliberately non-media URL. Queue, inbox and outbox fixtures were then removed.

This proves the intended boundary: broker outage delayed transport but did not lose the accepted PostgreSQL state. Worker startup briefly classified tools as unavailable, scheduled a durable retry, and then completed that retry after its tool heartbeat became ready.

## Duplicate and lease behavior

Environment-gated PostgreSQL tests passed for duplicate Rabbit delivery (one `worker_inbox` receipt), expired-lease fair claiming, owner quota serialization, persisted concurrency/quality preferences and clearing a running visible job. The clearing test retained the hidden row, stored the cancellation request and allowed the worker to commit `CANCELLED` afterward.

Local artifact tests replace the deterministic `owners/{owner}/jobs/{job}/artifact.mp3` key atomically and verify the final bytes/SHA-256, so redelivery cannot create a second object key. Completion metadata uses `ON CONFLICT (job_id) DO UPDATE`, while a lost completion race compensates by deleting the deterministic object.

## Controlled dependency outcomes

- PostgreSQL remains the authority; unavailable DB prevents readiness and durable acceptance.
- RabbitMQ outage retains unpublished outbox work and recovers without manual state edits (live evidence above).
- Redis publication failure is covered by application tests and cannot roll back durable transitions; mutation rate limiting intentionally fails closed.
- Storage failure maps to `ARTIFACT_STORAGE_FAILED` and never commits false completion; storage adapter tests cover idempotent replacement.
- The collector outage test remains valid: accepted work survived while OTLP export was unavailable.

See `operations/INCIDENTS.md` and `operations/BACKUP_RESTORE.md` for operator actions.
