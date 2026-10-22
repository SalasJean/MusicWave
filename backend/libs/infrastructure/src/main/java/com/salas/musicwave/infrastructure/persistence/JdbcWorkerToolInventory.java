package com.salas.musicwave.infrastructure.persistence;

import com.salas.musicwave.application.tools.MediaTool;
import com.salas.musicwave.application.tools.ToolInspection;
import com.salas.musicwave.application.tools.WorkerToolInventory;
import com.salas.musicwave.application.tools.WorkerToolSnapshot;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkerToolInventory implements WorkerToolInventory {

    private static final String UPSERT = """
            INSERT INTO worker_node (
                worker_id, started_at, last_seen_at,
                yt_dlp_available, yt_dlp_executable, yt_dlp_version, yt_dlp_detail,
                ffmpeg_available, ffmpeg_executable, ffmpeg_version, ffmpeg_detail
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (worker_id) DO UPDATE SET
                started_at = EXCLUDED.started_at,
                last_seen_at = EXCLUDED.last_seen_at,
                yt_dlp_available = EXCLUDED.yt_dlp_available,
                yt_dlp_executable = EXCLUDED.yt_dlp_executable,
                yt_dlp_version = EXCLUDED.yt_dlp_version,
                yt_dlp_detail = EXCLUDED.yt_dlp_detail,
                ffmpeg_available = EXCLUDED.ffmpeg_available,
                ffmpeg_executable = EXCLUDED.ffmpeg_executable,
                ffmpeg_version = EXCLUDED.ffmpeg_version,
                ffmpeg_detail = EXCLUDED.ffmpeg_detail
            """;

    private static final String FIND_ACTIVE = """
            SELECT worker_id, started_at, last_seen_at,
                   yt_dlp_available, yt_dlp_executable, yt_dlp_version, yt_dlp_detail,
                   ffmpeg_available, ffmpeg_executable, ffmpeg_version, ffmpeg_detail
            FROM worker_node
            WHERE last_seen_at >= ?
            ORDER BY worker_id
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkerToolInventory(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(WorkerToolSnapshot snapshot) {
        ToolInspection ytDlp = inspection(snapshot, MediaTool.YT_DLP);
        ToolInspection ffmpeg = inspection(snapshot, MediaTool.FFMPEG);
        jdbcTemplate.update(
                UPSERT,
                snapshot.workerId(),
                Timestamp.from(snapshot.startedAt()),
                Timestamp.from(snapshot.lastSeenAt()),
                ytDlp.available(), ytDlp.executable(), ytDlp.version(), ytDlp.detail(),
                ffmpeg.available(), ffmpeg.executable(), ffmpeg.version(), ffmpeg.detail());
    }

    @Override
    public List<WorkerToolSnapshot> findSeenSince(Instant cutoff) {
        return jdbcTemplate.query(FIND_ACTIVE, this::mapSnapshot, Timestamp.from(cutoff));
    }

    private WorkerToolSnapshot mapSnapshot(ResultSet resultSet, int rowNumber) throws SQLException {
        return new WorkerToolSnapshot(
                resultSet.getString("worker_id"),
                resultSet.getTimestamp("started_at").toInstant(),
                resultSet.getTimestamp("last_seen_at").toInstant(),
                List.of(
                        new ToolInspection(
                                MediaTool.YT_DLP,
                                resultSet.getBoolean("yt_dlp_available"),
                                resultSet.getString("yt_dlp_executable"),
                                resultSet.getString("yt_dlp_version"),
                                resultSet.getString("yt_dlp_detail")),
                        new ToolInspection(
                                MediaTool.FFMPEG,
                                resultSet.getBoolean("ffmpeg_available"),
                                resultSet.getString("ffmpeg_executable"),
                                resultSet.getString("ffmpeg_version"),
                                resultSet.getString("ffmpeg_detail"))));
    }

    private ToolInspection inspection(WorkerToolSnapshot snapshot, MediaTool tool) {
        return snapshot.tools().stream()
                .filter(candidate -> candidate.tool() == tool)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing report for " + tool));
    }
}
