# ADR-0002: Local owner bridge before JWT authentication

- Status: Superseded by ADR-0005
- Date: 2026-10-10

## Context

Persistent download jobs require ownership even in local development. Accepting an owner UUID in request content would create an insecure contract that the frontend could accidentally depend on, while production requires validated JWT authentication and cross-user enforcement.

## Decision

The API derives one owner from `MUSICWAVE_LOCAL_OWNER_ID` and `MUSICWAVE_LOCAL_OWNER_SUBJECT`. The adapter inserts that account when the first download batch is created and passes its identity to application use cases. No download endpoint accepts owner identity from a body, header or query parameter.

The repository includes `owner_id` in list and detail predicates. The JWT adapter replaces only the current-owner provider and reuses `external_subject`; download use cases and HTTP payloads remain unchanged.

## Consequences

- Local backend work can exercise real ownership and foreign keys before an identity provider is selected.
- The default configuration is deliberately a single-user development mode and must not be represented as production authentication.
- The final design retains this adapter only behind the explicit `local` mode and makes validated JWT mode the application default.
