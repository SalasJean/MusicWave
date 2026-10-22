# ADR-0004: Bounded Redis Stream with SSE replay

Status: Accepted

## Context

Download progress changes frequently and must reach a future browser without turning PostgreSQL into a high-frequency fan-out mechanism. Realtime delivery may be interrupted by network changes, API restarts or a temporarily unavailable Redis service. It must not become authoritative or make a durable download fail.

## Decision

PostgreSQL remains the source of truth. After a PostgreSQL lifecycle transition succeeds, the API or worker makes a best-effort append to one bounded Redis Stream. The entry includes owner and job IDs, state, a progress snapshot and its UTC occurrence time. The existing one-second/one-percent execution checkpoint gate also limits progress publication.

The API exposes an owner-scoped SSE endpoint. Redis record IDs become SSE IDs and `Last-Event-ID` resumes strictly after the acknowledged record. Connections without a cursor begin at the stream tail. Every global record advances the server cursor, but only records matching the server-derived owner are sent.

Exact maximum-length trimming bounds memory. When a requested cursor predates retained history, the API sends `stream-reset` and moves to the current tail; the client must first reload durable REST state. Blocking readers, connection count, batch size, heartbeat and timeouts are configurable and bounded.

## Consequences

- Redis loss can create a realtime gap but cannot invalidate PostgreSQL state or media execution.
- Reconnection normally replays ordered retained events without duplicating the acknowledged event.
- A client offline beyond retention receives an explicit recovery signal rather than silently assuming continuity.
- One global stream keeps topology simple, while server-side filtering preserves the current ownership boundary.
- One bounded blocking task is used per SSE connection. Capacity testing established the configured 100-connection boundary on the local machine; `docs/PERFORMANCE.md` retains a conservative local envelope and requires a target-VPS test before production claims.
