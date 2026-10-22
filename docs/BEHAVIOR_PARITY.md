# Backend behavior-parity matrix

| Legacy behavior | Backend equivalent | Evidence |
|---|---|---|
| Add one URL to the tray | `POST /api/v1/downloads` | Validation/idempotency/controller tests |
| Paste/import many URLs | `POST /downloads/bulk` and `/downloads/import` | Ordered partial acceptance and UTF-8 import tests |
| Display title and progress | Paginated REST state plus owner-scoped SSE | Title resolver, checkpoint and replay tests |
| Start queued downloads | `POST /queue/start` plus durable outbox/RabbitMQ | Transaction and publisher/consumer tests |
| Bound parallel downloads | Global worker pool plus persisted per-owner preference/ceiling | Fair-claim integration test |
| Select MP3 quality | `GET/PUT /settings`; quality included in claimed work | Settings and command-factory tests |
| Cancel one or all | Per-job cancel and `/queue/cancel` | Process-tree/cancellation tests |
| Retry a failure | Per-job retry followed by queue start | State/outbox tests |
| Remove completed/all tray rows | Safe `DELETE /queue/completed` and `DELETE /queue` visibility operations | Controller/repository integration tests |
| Obtain completed MP3 | Owner-scoped streamed artifact endpoint | Storage, ownership and checksum tests |
| Restart without losing accepted work | PostgreSQL outbox/inbox leases and deterministic artifact key | Recovery tests/runbook |

Desktop-only window placement, native file chooser, tray icons and notifications belong to the future Astro/UI host, not the backend. The backend exposes all data and controls needed to reproduce those interactions without embedding desktop paths or credentials.
