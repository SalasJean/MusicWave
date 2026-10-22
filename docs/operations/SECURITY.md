# Security runbook

- Production is JWT-only. Validate TLS, issuer, audience, token lifetime and key rotation before exposure.
- Store database, broker, object-storage, Grafana and JWT secrets outside source control; rotate after suspected disclosure.
- Deny public access to infrastructure ports and enforce host/firewall egress policy in addition to MusicWave's DNS/IP URL guard.
- Keep the reverse proxy request-size and timeout limits aligned with the 256 KiB TXT import and long-lived SSE endpoint.
- Preserve owner-derived authorization: no client header, query parameter or body field may select an owner.
- Review `dependencyUpdates`, base-image releases and CVE scans before each release; patch in a tested branch and rebuild immutable images.
- Scrub logs and traces for tokens, credentials and source URLs. Job IDs are correlation data, not authentication data.
- Rate-limit mutation endpoints and monitor authentication failures, 429s, DLQ depth, outbox backlog and unusual artifact downloads.

Incident response: isolate public traffic, preserve logs/database/object snapshots, rotate affected credentials, determine the first affected release and owner scope, restore only from verified backups, and document remediation before reopening.
