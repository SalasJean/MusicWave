CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    external_subject VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_settings (
    user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    max_concurrency INTEGER NOT NULL DEFAULT 5 CHECK (max_concurrency BETWEEN 1 AND 10),
    audio_quality VARCHAR(16) NOT NULL DEFAULT '192K',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE download_job (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_user(id),
    source_url TEXT NOT NULL,
    title TEXT,
    status VARCHAR(24) NOT NULL,
    progress NUMERIC(5, 2) NOT NULL DEFAULT 0 CHECK (progress BETWEEN 0 AND 100),
    speed VARCHAR(64),
    eta VARCHAR(64),
    error_code VARCHAR(64),
    error_message TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    CONSTRAINT download_job_status_check CHECK (
        status IN ('PENDING', 'QUEUED', 'DOWNLOADING', 'CONVERTING', 'COMPLETED', 'FAILED', 'CANCELLED')
    )
);

CREATE INDEX download_job_owner_created_idx ON download_job(owner_id, created_at DESC);
CREATE INDEX download_job_status_created_idx ON download_job(status, created_at);

CREATE TABLE download_artifact (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL UNIQUE REFERENCES download_job(id) ON DELETE CASCADE,
    storage_key TEXT NOT NULL UNIQUE,
    original_filename TEXT NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ
);

CREATE INDEX outbox_event_unpublished_idx ON outbox_event(created_at) WHERE published_at IS NULL;
