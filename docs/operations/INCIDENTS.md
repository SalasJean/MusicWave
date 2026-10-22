# Incident and dependency recovery

| Failure | Expected behavior | Recovery check |
|---|---|---|
| PostgreSQL unavailable | API readiness fails; no work is accepted as durable; workers stop authoritative transitions. | Restore DB, require Flyway/readiness `UP`, inspect active leases/outbox. |
| RabbitMQ unavailable | Accepted jobs/outbox rows remain in PostgreSQL; publisher retries with backoff. | Restore broker and verify outbox drains exactly once into inbox. |
| Worker stopped/restarted | Queued work remains durable; expired processing leases are reclaimable. | Start worker, watch inbox leases and terminal transitions. |
| Redis unavailable | Mutations fail closed because distributed rate limiting is unavailable; already accepted execution continues and only ephemeral SSE publication/replay is lost. | Restore Redis, reconnect SSE and refresh REST state. |
| Object storage unavailable | Execution fails with `ARTIFACT_STORAGE_FAILED`; no false `COMPLETED` state is committed. | Restore storage and retry the owned failed job. |
| Collector/Loki/Tempo unavailable | Business work continues; telemetry export/log ingestion recovers independently. | Restore telemetry and verify new correlated signal; do not infer lost business state from a telemetry gap. |

For every incident: establish UTC start/end, affected owners/jobs, last known PostgreSQL state and whether an outbox/inbox lease is active. Avoid manually changing job state while a live worker owns its lease. Escalate DLQ items only after classifying the original failure, then use the public retry flow where possible.
