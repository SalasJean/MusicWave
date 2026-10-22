# ADR-0005: JWT ownership, shared-capacity admission and outbound URL security

- Status: Accepted
- Date: 2026-10-11

## Context

The local-owner bridge cannot safely identify multiple Internet users. Horizontal API and worker instances also need shared quota, rate and scheduling decisions, and user-provided media URLs create an outbound-request boundary.

## Decision

MusicWave is an OAuth 2.0 resource server. Production-default `jwt` mode validates token signature, issuer, audience, lifetime and subject through Spring Security. A JWK Set URI is preferred for asymmetric deployment; an HS256 secret of at least 32 bytes is supported for controlled environments. The validated `iss|sub` pair resolves idempotently to one PostgreSQL `app_user`. Docker Compose selects the isolated `local` adapter explicitly for development.

Active-job quota is enforced inside the creation transaction after idempotency resolution. PostgreSQL locks the owner row before counting active states, serializing competing API instances. Mutation rate admission uses one Redis Lua operation per owner and returns a bounded retry delay. Worker claims rank work per owner and cap each owner's live leases before applying the global worker limit.

The API and worker share a framework-neutral URL guard. It resolves every hostname and accepts it only when every answer is publicly routable, rejecting local names and unsafe IPv4/IPv6 ranges. Validation runs during ingestion and again immediately before `yt-dlp` starts.

## Consequences

- MusicWave trusts no client-selected owner value and foreign resources retain indistinguishable 404 responses.
- Token issuance, registration and password storage remain the identity provider's responsibility.
- Redis availability is required to admit mutations; reads and durable processing are unaffected by limiter failure.
- Owner-row locking favors correctness over maximum same-owner creation throughput.
- DNS checks reduce SSRF and rebinding exposure but do not replace host-level egress filtering or a restrictive deployment network policy.
