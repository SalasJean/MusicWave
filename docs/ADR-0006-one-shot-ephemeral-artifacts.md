# ADR-0006: One-shot ephemeral MP3 delivery

- Status: accepted
- Date: 2026-10-12

## Context

Generated MP3 files must not accumulate on the MusicWave server. Browser-only yt-dlp/FFmpeg execution would break the durable asynchronous queue, retry, cancellation and provider-compatibility boundaries, so a bounded server-side workspace remains necessary while a job runs. Retaining completed media indefinitely is not necessary once the browser has received it.

An HTTP server can prove that it finished writing the response, but it cannot prove that a browser ultimately committed the file to the user's filesystem. The strongest portable server-side boundary is therefore successful HTTP transfer plus a mandatory expiry fallback.

## Decision

Treat every completed MP3 as a temporary, one-shot artifact:

1. The worker uploads the validated MP3 before committing `COMPLETED`, as before.
2. PostgreSQL records an `AVAILABLE` artifact with a mandatory `expires_at` instant. The default TTL is one hour.
3. The first owner-scoped retrieval atomically leases it as `DELIVERING`. Concurrent retrieval returns `ARTIFACT_DELIVERY_IN_PROGRESS`.
4. A complete HTTP stream changes the artifact to `DELETE_PENDING`, deletes the S3 object and removes the artifact row. The job retains only `artifact_removed_at` and reason `DOWNLOADED`.
5. An interrupted stream releases its lease so it can be retried before expiry.
6. A scheduled reconciler claims expired, abandoned or previously failed deletions with `FOR UPDATE SKIP LOCKED`. Object deletion is idempotent; a failure is durably delayed and retried.
7. Clearing completed/all visible work makes available artifacts immediately eligible for deletion. An in-flight delivery is allowed to finish; an abort then becomes immediately eligible.
8. Responses use `Cache-Control: no-store` and related headers so intermediaries are instructed not to retain user media.

Artifact TTL, delivery/deletion leases, retry delay, cleanup interval and batch size are deployment configuration. S3 remains an internal temporary buffer, never a permanent music library.

## Consequences

- A successful artifact URL is intentionally single-use. Repeating it returns HTTP 410 `ARTIFACT_ALREADY_DOWNLOADED`.
- An artifact not retrieved before its deadline returns HTTP 410 `ARTIFACT_EXPIRED` and is removed asynchronously.
- A browser that blocks an automatic download never starts the HTTP transfer; the artifact remains available until the user retries or TTL removes it.
- A process crash cannot create indefinite retention: delivery and deletion leases are recovered by the reconciler.
- The Astro client must react to `COMPLETED` SSE events, initiate the artifact request immediately, explain the browser's multiple-download permission and expose a manual retry action while the artifact remains available.
- Job/audit rows remain durable, but completed MP3 bytes and their storage row do not.
