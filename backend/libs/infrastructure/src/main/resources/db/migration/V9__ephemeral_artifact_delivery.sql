ALTER TABLE download_job
    ADD COLUMN artifact_removed_at TIMESTAMPTZ,
    ADD COLUMN artifact_removal_reason VARCHAR(32),
    ADD CONSTRAINT download_job_artifact_removal_reason_check CHECK (
        artifact_removal_reason IS NULL
        OR artifact_removal_reason IN ('DOWNLOADED', 'EXPIRED')
    );

ALTER TABLE download_artifact
    ADD COLUMN delivery_state VARCHAR(24) NOT NULL DEFAULT 'AVAILABLE',
    ADD COLUMN expires_at TIMESTAMPTZ,
    ADD COLUMN lease_token UUID,
    ADD COLUMN lease_until TIMESTAMPTZ,
    ADD COLUMN removal_reason VARCHAR(32),
    ADD COLUMN deletion_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_deletion_error TEXT,
    ADD CONSTRAINT download_artifact_delivery_state_check CHECK (
        delivery_state IN ('AVAILABLE', 'DELIVERING', 'DELETE_PENDING')
    ),
    ADD CONSTRAINT download_artifact_removal_reason_check CHECK (
        removal_reason IS NULL OR removal_reason IN ('DOWNLOADED', 'EXPIRED')
    ),
    ADD CONSTRAINT download_artifact_deletion_attempts_check CHECK (deletion_attempts >= 0);

UPDATE download_artifact
SET expires_at = created_at + INTERVAL '1 hour'
WHERE expires_at IS NULL;

ALTER TABLE download_artifact
    ALTER COLUMN expires_at SET NOT NULL;

CREATE INDEX download_artifact_cleanup_idx
    ON download_artifact (delivery_state, expires_at, lease_until);
