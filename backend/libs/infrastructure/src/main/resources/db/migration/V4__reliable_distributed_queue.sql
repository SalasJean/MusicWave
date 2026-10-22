ALTER TABLE outbox_event
    ADD COLUMN routing_key VARCHAR(128) NOT NULL DEFAULT 'download.execute',
    ADD COLUMN available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN publish_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_error TEXT,
    ADD COLUMN locked_by VARCHAR(255),
    ADD COLUMN locked_until TIMESTAMPTZ,
    ADD COLUMN deduplication_key VARCHAR(255),
    ADD CONSTRAINT outbox_event_publish_attempts_check CHECK (publish_attempts >= 0);

CREATE UNIQUE INDEX outbox_event_deduplication_idx
    ON outbox_event(deduplication_key)
    WHERE deduplication_key IS NOT NULL;

DROP INDEX outbox_event_unpublished_idx;

CREATE INDEX outbox_event_available_idx
    ON outbox_event(available_at, created_at)
    WHERE published_at IS NULL;

ALTER TABLE download_job
    ADD COLUMN dispatched_at TIMESTAMPTZ,
    ADD COLUMN dispatch_attempts INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT download_job_dispatch_attempts_check CHECK (dispatch_attempts >= 0);

CREATE TABLE worker_inbox (
    message_id UUID PRIMARY KEY,
    job_id UUID NOT NULL UNIQUE REFERENCES download_job(id) ON DELETE CASCADE,
    delivery_attempt INTEGER NOT NULL CHECK (delivery_attempt >= 0),
    first_received_by VARCHAR(255) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX worker_inbox_received_idx ON worker_inbox(received_at, message_id);
