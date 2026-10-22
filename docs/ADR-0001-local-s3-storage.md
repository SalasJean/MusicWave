# ADR-0001: Vendor-neutral local S3 storage

- Status: accepted
- Date: 2026-10-09

## Context

MusicWave needs object storage semantics for generated audio while remaining portable to a future VPS. The initially planned MinIO community images could not be fetched from the available registry during the infrastructure evaluation.

## Decision

Keep the application boundary vendor-neutral through a future `ArtifactStorage` port and an S3 adapter. Use SeaweedFS 4.47 as the pinned local S3-compatible service in Docker Compose.

Do not expose SeaweedFS-specific types to domain or application modules. A future S3 provider can replace the local service through configuration without changing the HTTP contract or use cases.

## Consequences

- Local Docker validation is reproducible and does not depend on the unavailable registry.
- Storage integration tests must target S3 behavior rather than vendor administration APIs.
- The implementation provisions the bucket idempotently and supports configurable S3 credentials; the pinned local SeaweedFS runtime remains anonymous inside the development network.
- Destructive retention was deferred here; [ADR-0006](ADR-0006-one-shot-ephemeral-artifacts.md) now defines one-shot delivery and mandatory expiry.
