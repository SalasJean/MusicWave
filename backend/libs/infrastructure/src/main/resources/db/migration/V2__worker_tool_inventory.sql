CREATE TABLE worker_node (
    worker_id VARCHAR(255) PRIMARY KEY,
    started_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    yt_dlp_available BOOLEAN NOT NULL,
    yt_dlp_executable TEXT NOT NULL,
    yt_dlp_version TEXT NOT NULL,
    yt_dlp_detail TEXT NOT NULL,
    ffmpeg_available BOOLEAN NOT NULL,
    ffmpeg_executable TEXT NOT NULL,
    ffmpeg_version TEXT NOT NULL,
    ffmpeg_detail TEXT NOT NULL,
    CONSTRAINT worker_node_time_check CHECK (last_seen_at >= started_at)
);

CREATE INDEX worker_node_last_seen_idx ON worker_node(last_seen_at DESC);
