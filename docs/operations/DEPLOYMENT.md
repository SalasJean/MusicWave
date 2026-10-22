# Deployment runbook

## Preconditions

- Linux host with Docker Engine and Compose v2, persistent disk, DNS and TLS reverse proxy.
- Copy `.env.example` to a root-readable deployment environment file and replace every development credential.
- Configure JWT issuer, audience and JWK URL (or a 32-byte-or-longer HS256 secret); never deploy with `MUSICWAVE_SECURITY_MODE=local`.
- Restrict PostgreSQL, RabbitMQ, Redis, object storage, Actuator, Grafana and OTLP ports to the private host/network. Only the TLS proxy may expose the API.

## Release

1. Back up PostgreSQL and object storage using `BACKUP_RESTORE.md`.
2. Build and test: `cd backend && ./gradlew clean test :apps:api:bootJar :apps:worker:bootJar --no-daemon`.
3. Validate configuration: `docker compose -f infrastructure/compose.yaml config --quiet`.
4. Pull pinned images, then build API/worker images.
5. Start dependencies, API and worker with Compose. Flyway runs before traffic is accepted.
6. Require `UP` from both readiness endpoints and verify `/api/v1/tools/status`, `/v3/api-docs`, Prometheus targets and RabbitMQ queue depth.
7. Confirm that `infrastructure/yt-dlp.conf` is mounted read-only in the worker and run the non-downloading provider probe from `YOUTUBE.md`.
8. Run `scripts/verify-openapi.sh` against the release.

## Rollback

Stop API/worker traffic, retain all volumes, deploy the previous application images and restart. Do not roll back a database migration by editing Flyway history. If the previous binary cannot read the new schema, restore the coordinated PostgreSQL/object-store backup into a separate recovery stack and switch traffic only after validation.

## Post-deploy checks

Create one idempotent test request, start it, observe correlated API/outbox/worker logs and trace, retrieve the MP3, then clear the fixture. Include a known YouTube music fixture to exercise the Android-first player selection documented in `YOUTUBE.md`. Confirm zero unexpected DLQ messages and a non-growing outbox backlog.
