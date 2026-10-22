ALTER TABLE download_job
    ADD COLUMN cancellation_requested_at TIMESTAMPTZ;

ALTER TABLE worker_inbox
    ADD COLUMN processing_status VARCHAR(24) NOT NULL DEFAULT 'RECEIVED',
    ADD COLUMN claimed_by VARCHAR(255),
    ADD COLUMN claim_until TIMESTAMPTZ,
    ADD COLUMN processing_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN completed_at TIMESTAMPTZ,
    ADD COLUMN last_error TEXT,
    ADD CONSTRAINT worker_inbox_processing_status_check CHECK (
        processing_status IN ('RECEIVED', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED')
    ),
    ADD CONSTRAINT worker_inbox_processing_attempts_check CHECK (processing_attempts >= 0);

CREATE INDEX worker_inbox_claim_idx
    ON worker_inbox(processing_status, claim_until, received_at)
    WHERE processing_status IN ('RECEIVED', 'PROCESSING');
