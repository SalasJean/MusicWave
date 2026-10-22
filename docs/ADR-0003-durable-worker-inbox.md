# ADR-0003: Durable inbox before long-running media execution

Status: Accepted

## Context

Media downloads may run much longer than a RabbitMQ consumer timeout. Holding the broker delivery unacknowledged for the full yt-dlp process also couples redelivery behavior to external-process duration and makes controlled worker recovery difficult.

## Decision

The RabbitMQ consumer commits an idempotent `worker_inbox` receipt before manual ACK. A separate bounded worker executor claims inbox rows with renewable PostgreSQL leases. An unfinished `PROCESSING` row becomes eligible again after its lease expires.

Each execution uses a UUID-isolated staging directory. Completion, artifact metadata and inbox completion are committed together. Cancellation is a durable job signal that the process runner polls before terminating the complete process tree.

## Consequences

- Broker delivery remains short and duplicate delivery is harmless.
- Worker restarts recover accepted work from PostgreSQL without requiring RabbitMQ to retain a long-lived unacknowledged message.
- Execution leases must be renewed while work is active.
- A crash can cause safe re-execution, so workspace preparation and artifact persistence must remain idempotent.
- The local staging artifact is not an API storage contract; retrieval and lifecycle details remain behind storage adapters.
