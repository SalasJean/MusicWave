ALTER TABLE download_artifact
    ADD COLUMN sha256 VARCHAR(64);

UPDATE download_artifact
SET sha256 = repeat('0', 64)
WHERE sha256 IS NULL;

ALTER TABLE download_artifact
    ALTER COLUMN sha256 SET NOT NULL,
    ADD CONSTRAINT download_artifact_sha256_check
        CHECK (sha256 ~ '^[0-9a-f]{64}$');
