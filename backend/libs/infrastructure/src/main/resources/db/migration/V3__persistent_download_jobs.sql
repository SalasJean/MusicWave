CREATE TABLE download_request (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_user(id),
    request_type VARCHAR(16) NOT NULL,
    idempotency_key VARCHAR(255),
    request_fingerprint CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT download_request_type_check CHECK (request_type IN ('SINGLE', 'BULK', 'IMPORT'))
);

CREATE UNIQUE INDEX download_request_idempotency_idx
    ON download_request(owner_id, request_type, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

INSERT INTO download_request (id, owner_id, request_type, request_fingerprint, created_at)
SELECT id, owner_id, 'SINGLE', repeat('0', 64), created_at
FROM download_job;

ALTER TABLE download_job
    ADD COLUMN request_id UUID REFERENCES download_request(id),
    ADD COLUMN request_index INTEGER,
    ADD COLUMN title_status VARCHAR(24),
    ADD COLUMN title_lookup_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN title_lookup_detail TEXT,
    ADD COLUMN next_title_lookup_at TIMESTAMPTZ;

UPDATE download_job
SET request_id = id,
    request_index = 0,
    title_status = CASE WHEN title IS NULL THEN 'NOT_APPLICABLE' ELSE 'RESOLVED' END,
    title = COALESCE(title, CASE
        WHEN length(source_url) > 60 THEN substring(source_url FROM 1 FOR 57) || '...'
        ELSE source_url
    END);

ALTER TABLE download_job
    ALTER COLUMN request_id SET NOT NULL,
    ALTER COLUMN request_index SET NOT NULL,
    ALTER COLUMN title SET NOT NULL,
    ALTER COLUMN title_status SET NOT NULL,
    ADD CONSTRAINT download_job_request_index_check CHECK (request_index >= 0),
    ADD CONSTRAINT download_job_title_status_check CHECK (
        title_status IN ('NOT_APPLICABLE', 'PENDING', 'RESOLVED', 'UNAVAILABLE')
    ),
    ADD CONSTRAINT download_job_title_attempts_check CHECK (title_lookup_attempts >= 0);

CREATE UNIQUE INDEX download_job_request_position_idx
    ON download_job(request_id, request_index);

CREATE INDEX download_job_title_lookup_idx
    ON download_job(next_title_lookup_at, created_at)
    WHERE title_status = 'PENDING';
