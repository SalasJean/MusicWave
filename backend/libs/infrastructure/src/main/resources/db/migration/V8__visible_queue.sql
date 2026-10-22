ALTER TABLE download_job
    ADD COLUMN visible BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX download_job_owner_visible_created_idx
    ON download_job(owner_id, visible, created_at DESC, id DESC);
