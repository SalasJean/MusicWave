# Backup and restore runbook

PostgreSQL and the artifact bucket form one recovery set. RabbitMQ transports work and Redis is ephemeral; neither replaces the authoritative database backup.

## Backup

1. Record release version and UTC timestamp; pause new mutations at the reverse proxy.
2. Allow active workers to finish or cancel them cleanly and verify no `PROCESSING` inbox leases remain.
3. Run a compressed `pg_dump --format=custom` of the `musicwave` database.
4. Snapshot/export the complete configured artifact bucket and record object count/bytes.
5. Hash both outputs, encrypt them, copy them off-host and resume traffic.
6. Retain backups according to the operator's legal and storage policy; routinely test restoration.

## Restore drill

1. Create isolated empty PostgreSQL and object-storage volumes using the same or newer compatible versions.
2. Restore the database with `pg_restore --clean --if-exists --no-owner`, then restore the bucket.
3. Start one API and one worker with RabbitMQ/Redis empty. Flyway must report the expected schema version.
4. Compare row counts, terminal artifact metadata and bucket objects; retrieve and hash a sample of completed artifacts.
5. Jobs left queued are republished from durable outbox state. Expired worker leases are reclaimable; duplicate inbox delivery is harmless.
6. Record recovery time and recovery point. Never promote a drill environment without rotating all credentials.
