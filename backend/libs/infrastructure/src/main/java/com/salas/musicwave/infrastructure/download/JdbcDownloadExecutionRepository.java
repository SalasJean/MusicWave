package com.salas.musicwave.infrastructure.download;

import com.salas.musicwave.application.execution.DownloadControlRepository;
import com.salas.musicwave.application.execution.DownloadControlResult;
import com.salas.musicwave.application.execution.DownloadExecutionRepository;
import com.salas.musicwave.application.execution.InvalidDownloadStateException;
import com.salas.musicwave.application.execution.QueueCancellationResult;
import com.salas.musicwave.application.execution.QueueClearResult;
import com.salas.musicwave.application.execution.QueuedDownloadWork;
import com.salas.musicwave.application.artifact.StoredArtifact;
import com.salas.musicwave.domain.download.DownloadStatus;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcDownloadExecutionRepository
        implements DownloadExecutionRepository, DownloadControlRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDownloadExecutionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public List<QueuedDownloadWork> claimReady(String workerId,
                                               Instant now,
                                               Duration leaseDuration,
                                               int limit,
                                               int maxPerOwner) {
        return jdbcTemplate.query(
                """
                WITH active_by_owner AS (
                    SELECT job.owner_id, COUNT(*) AS active_count
                    FROM worker_inbox inbox
                    JOIN download_job job ON job.id = inbox.job_id
                    WHERE inbox.processing_status = 'PROCESSING' AND inbox.claim_until > ?
                    GROUP BY job.owner_id
                ), ranked AS (
                    SELECT inbox.message_id, inbox.job_id, job.owner_id, job.source_url, job.title,
                           inbox.trace_id, inbox.span_id,
                           COALESCE(settings.audio_quality, '192K') AS audio_quality,
                           LEAST(?, COALESCE(settings.max_concurrency, ?)) AS owner_limit
                         , ROW_NUMBER() OVER (
                               PARTITION BY job.owner_id
                               ORDER BY inbox.received_at, inbox.message_id
                           ) AS owner_position
                         , COALESCE(active.active_count, 0) AS active_count
                    FROM worker_inbox inbox
                    JOIN download_job job ON job.id = inbox.job_id
                    LEFT JOIN active_by_owner active ON active.owner_id = job.owner_id
                    LEFT JOIN user_settings settings ON settings.user_id = job.owner_id
                    WHERE (
                        inbox.processing_status = 'RECEIVED'
                        OR (inbox.processing_status = 'PROCESSING' AND inbox.claim_until <= ?)
                    )
                      AND job.status IN ('QUEUED', 'DOWNLOADING', 'CONVERTING')
                      AND job.cancellation_requested_at IS NULL
                ), candidate_ids AS (
                    SELECT message_id, owner_position
                    FROM ranked
                    WHERE owner_position <= GREATEST(owner_limit - active_count, 0)
                    ORDER BY owner_position, message_id
                    LIMIT ?
                ), candidates AS (
                    SELECT inbox.message_id, inbox.job_id, job.owner_id, job.source_url, job.title,
                           inbox.trace_id, inbox.span_id, ranked.audio_quality,
                           candidate_ids.owner_position
                    FROM candidate_ids
                    JOIN ranked ON ranked.message_id = candidate_ids.message_id
                    JOIN worker_inbox inbox ON inbox.message_id = candidate_ids.message_id
                    JOIN download_job job ON job.id = inbox.job_id
                    ORDER BY candidate_ids.owner_position, inbox.received_at, inbox.message_id
                    FOR UPDATE OF inbox SKIP LOCKED
                )
                UPDATE worker_inbox inbox
                SET processing_status = 'PROCESSING', claimed_by = ?, claim_until = ?,
                    processing_attempts = processing_attempts + 1, last_error = NULL
                FROM candidates
                WHERE inbox.message_id = candidates.message_id
                RETURNING inbox.message_id, inbox.job_id, candidates.source_url,
                          candidates.owner_id, candidates.title, inbox.processing_attempts,
                          candidates.audio_quality, candidates.trace_id, candidates.span_id
                """,
                (resultSet, rowNumber) -> new QueuedDownloadWork(
                        resultSet.getObject("message_id", UUID.class),
                        resultSet.getObject("job_id", UUID.class),
                        resultSet.getObject("owner_id", UUID.class),
                        resultSet.getString("source_url"),
                        resultSet.getString("title"),
                        resultSet.getInt("processing_attempts"),
                        resultSet.getString("audio_quality"),
                        resultSet.getString("trace_id"),
                        resultSet.getString("span_id")),
                Timestamp.from(now),
                maxPerOwner,
                maxPerOwner,
                Timestamp.from(now),
                limit,
                workerId,
                Timestamp.from(now.plus(leaseDuration)));
    }

    @Override
    @Transactional
    public void renewLeases(String workerId,
                            List<UUID> jobIds,
                            Instant now,
                            Duration leaseDuration) {
        for (UUID jobId : jobIds) {
            jdbcTemplate.update(
                    """
                    UPDATE worker_inbox
                    SET claim_until = ?
                    WHERE job_id = ? AND claimed_by = ? AND processing_status = 'PROCESSING'
                    """,
                    Timestamp.from(now.plus(leaseDuration)), jobId, workerId);
        }
    }

    @Override
    @Transactional
    public boolean markDownloading(UUID jobId, String workerId, Instant now) {
        int updated = jdbcTemplate.update(
                """
                UPDATE download_job job
                SET status = 'DOWNLOADING', progress = 0, speed = NULL, eta = NULL,
                    error_code = NULL, error_message = NULL,
                    started_at = COALESCE(started_at, ?), completed_at = NULL,
                    updated_at = ?, version = version + 1
                WHERE job.id = ?
                  AND job.status IN ('QUEUED', 'DOWNLOADING', 'CONVERTING')
                  AND job.cancellation_requested_at IS NULL
                  AND EXISTS (
                      SELECT 1 FROM worker_inbox inbox
                      WHERE inbox.job_id = job.id
                        AND inbox.processing_status = 'PROCESSING'
                        AND inbox.claimed_by = ?
                  )
                """,
                Timestamp.from(now), Timestamp.from(now), jobId, workerId);
        return updated == 1;
    }

    @Override
    public boolean checkpointProgress(UUID jobId,
                                      BigDecimal progress,
                                      String speed,
                                      String eta,
                                      Instant now) {
        return jdbcTemplate.update(
                """
                UPDATE download_job
                SET progress = ?, speed = COALESCE(?, speed), eta = COALESCE(?, eta),
                    updated_at = ?, version = version + 1
                WHERE id = ? AND status = 'DOWNLOADING' AND cancellation_requested_at IS NULL
                """,
                progress, speed, eta, Timestamp.from(now), jobId) == 1;
    }

    @Override
    public boolean markConverting(UUID jobId, Instant now) {
        return jdbcTemplate.update(
                """
                UPDATE download_job
                SET status = 'CONVERTING', speed = NULL, eta = NULL,
                    updated_at = ?, version = version + 1
                WHERE id = ? AND status IN ('DOWNLOADING', 'CONVERTING')
                  AND cancellation_requested_at IS NULL
                """,
                Timestamp.from(now), jobId) == 1;
    }

    @Override
    public void updateTitle(UUID jobId, String title, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE download_job
                SET title = ?, updated_at = ?, version = version + 1
                WHERE id = ? AND status IN ('DOWNLOADING', 'CONVERTING')
                """,
                safe(title, 1_000), Timestamp.from(now), jobId);
    }

    @Override
    public boolean cancellationRequested(UUID jobId) {
        Boolean requested = jdbcTemplate.queryForObject(
                "SELECT cancellation_requested_at IS NOT NULL FROM download_job WHERE id = ?",
                Boolean.class,
                jobId);
        return Boolean.TRUE.equals(requested);
    }

    @Override
    @Transactional
    public boolean markCompleted(UUID jobId,
                                 StoredArtifact artifact,
                                 Instant artifactExpiresAt,
                                 Instant now) {
        LockedJob job = lockJob(jobId);
        if (job.cancellationRequested()) {
            markCancelledLocked(jobId, "Cancelled by the user", now);
            return false;
        }
        int updated = jdbcTemplate.update(
                """
                UPDATE download_job
                SET status = 'COMPLETED', progress = 100, speed = NULL, eta = NULL,
                    error_code = NULL, error_message = NULL,
                    artifact_removed_at = NULL, artifact_removal_reason = NULL,
                    completed_at = ?, updated_at = ?, version = version + 1
                WHERE id = ? AND status IN ('DOWNLOADING', 'CONVERTING')
                """,
                Timestamp.from(now), Timestamp.from(now), jobId);
        if (updated != 1) {
            throw new IllegalStateException("Download was no longer executable at completion");
        }
        jdbcTemplate.update(
                """
                INSERT INTO download_artifact (
                    id, job_id, storage_key, original_filename, content_type, size_bytes,
                    sha256, created_at, delivery_state, expires_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'AVAILABLE', ?)
                ON CONFLICT (job_id) DO UPDATE
                SET storage_key = EXCLUDED.storage_key,
                    original_filename = EXCLUDED.original_filename,
                    content_type = EXCLUDED.content_type,
                    size_bytes = EXCLUDED.size_bytes,
                    sha256 = EXCLUDED.sha256,
                    created_at = EXCLUDED.created_at,
                    delivery_state = 'AVAILABLE',
                    expires_at = EXCLUDED.expires_at,
                    lease_token = NULL,
                    lease_until = NULL,
                    removal_reason = NULL,
                    deletion_attempts = 0,
                    last_deletion_error = NULL
                """,
                UUID.randomUUID(),
                jobId,
                artifact.storageKey(),
                artifact.filename(),
                artifact.contentType(),
                artifact.sizeBytes(),
                artifact.sha256(),
                Timestamp.from(now),
                Timestamp.from(artifactExpiresAt));
        finishInbox(jobId, "COMPLETED", null, now);
        return true;
    }

    @Override
    @Transactional
    public DownloadStatus markFailed(UUID jobId,
                                     String errorCode,
                                     String errorMessage,
                                     Instant now) {
        LockedJob job = lockJob(jobId);
        if (job.cancellationRequested()) {
            markCancelledLocked(jobId, "Cancelled by the user", now);
            return DownloadStatus.CANCELLED;
        }
        jdbcTemplate.update(
                """
                UPDATE download_job
                SET status = 'FAILED', speed = NULL, eta = NULL,
                    error_code = ?, error_message = ?, completed_at = ?, updated_at = ?,
                    version = version + 1
                WHERE id = ? AND status IN ('QUEUED', 'DOWNLOADING', 'CONVERTING')
                """,
                safe(errorCode, 64),
                safe(errorMessage, 2_000),
                Timestamp.from(now),
                Timestamp.from(now),
                jobId);
        finishInbox(jobId, "FAILED", safe(errorMessage, 2_000), now);
        return DownloadStatus.FAILED;
    }

    @Override
    @Transactional
    public boolean markCancelled(UUID jobId, String detail, Instant now) {
        lockJob(jobId);
        return markCancelledLocked(jobId, detail, now);
    }

    @Override
    @Transactional
    public Optional<DownloadControlResult> cancel(UUID ownerId, UUID jobId, Instant now) {
        Optional<OwnedJob> found = lockOwnedJob(ownerId, jobId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(cancelLocked(found.get(), now));
    }

    @Override
    @Transactional
    public Optional<DownloadControlResult> retryFailed(UUID ownerId, UUID jobId, Instant now) {
        Optional<OwnedJob> found = lockOwnedJob(ownerId, jobId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        if (found.get().status() != DownloadStatus.FAILED) {
            throw new InvalidDownloadStateException(
                    "DOWNLOAD_NOT_RETRYABLE", "Only failed downloads can be retried");
        }
        jdbcTemplate.update("DELETE FROM worker_inbox WHERE job_id = ?", jobId);
        jdbcTemplate.update("DELETE FROM download_artifact WHERE job_id = ?", jobId);
        jdbcTemplate.update(
                """
                UPDATE download_job
                SET status = 'PENDING', progress = 0, speed = NULL, eta = NULL,
                    error_code = NULL, error_message = NULL,
                    retry_count = retry_count + 1, dispatched_at = NULL, dispatch_attempts = 0,
                    cancellation_requested_at = NULL, started_at = NULL, completed_at = NULL,
                    updated_at = ?, version = version + 1
                WHERE id = ?
                """,
                Timestamp.from(now), jobId);
        return Optional.of(new DownloadControlResult(jobId, DownloadStatus.PENDING, false));
    }

    @Override
    @Transactional
    public QueueCancellationResult cancelActive(UUID ownerId, Instant now) {
        List<OwnedJob> jobs = jdbcTemplate.query(
                """
                SELECT id, status, cancellation_requested_at IS NOT NULL AS cancellation_requested
                FROM download_job
                WHERE owner_id = ?
                  AND visible
                  AND status IN ('PENDING', 'QUEUED', 'DOWNLOADING', 'CONVERTING')
                ORDER BY created_at, id
                FOR UPDATE
                """,
                (resultSet, rowNumber) -> new OwnedJob(
                        resultSet.getObject("id", UUID.class),
                        DownloadStatus.valueOf(resultSet.getString("status")),
                        resultSet.getBoolean("cancellation_requested")),
                ownerId);
        int cancelled = 0;
        int requested = 0;
        List<DownloadControlResult> transitions = new java.util.ArrayList<>();
        for (OwnedJob job : jobs) {
            DownloadControlResult result = cancelLocked(job, now);
            transitions.add(result);
            if (result.status() == DownloadStatus.CANCELLED) {
                cancelled++;
            } else if (result.cancellationRequested()) {
                requested++;
            }
        }
        return new QueueCancellationResult(cancelled, requested, transitions);
    }

    @Override
    @Transactional
    public QueueClearResult clearCompleted(UUID ownerId, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE download_artifact artifact
                SET delivery_state = CASE
                        WHEN artifact.delivery_state = 'AVAILABLE' THEN 'DELETE_PENDING'
                        ELSE artifact.delivery_state
                    END,
                    expires_at = LEAST(artifact.expires_at, ?),
                    removal_reason = COALESCE(artifact.removal_reason, 'EXPIRED'),
                    lease_token = CASE
                        WHEN artifact.delivery_state = 'AVAILABLE' THEN NULL
                        ELSE artifact.lease_token
                    END,
                    lease_until = CASE
                        WHEN artifact.delivery_state = 'AVAILABLE' THEN ?
                        ELSE artifact.lease_until
                    END
                FROM download_job job
                WHERE artifact.job_id = job.id
                  AND job.owner_id = ? AND job.visible AND job.status = 'COMPLETED'
                  AND artifact.delivery_state IN ('AVAILABLE', 'DELIVERING')
                """,
                Timestamp.from(now), Timestamp.from(now), ownerId);
        int cleared = jdbcTemplate.update(
                """
                UPDATE download_job
                SET visible = FALSE, updated_at = ?, version = version + 1
                WHERE owner_id = ? AND visible AND status = 'COMPLETED'
                """,
                Timestamp.from(now), ownerId);
        return new QueueClearResult(cleared, 0, 0);
    }

    @Override
    @Transactional
    public QueueClearResult clearAll(UUID ownerId, Instant now) {
        List<OwnedJob> jobs = jdbcTemplate.query(
                """
                SELECT id, status, cancellation_requested_at IS NOT NULL AS cancellation_requested
                FROM download_job
                WHERE owner_id = ? AND visible
                ORDER BY created_at, id
                FOR UPDATE
                """,
                (resultSet, rowNumber) -> new OwnedJob(
                        resultSet.getObject("id", UUID.class),
                        DownloadStatus.valueOf(resultSet.getString("status")),
                        resultSet.getBoolean("cancellation_requested")),
                ownerId);
        int cancelled = 0;
        int requested = 0;
        List<DownloadControlResult> transitions = new java.util.ArrayList<>();
        for (OwnedJob job : jobs) {
            if (!job.status().isTerminal()) {
                DownloadControlResult transition = cancelLocked(job, now);
                transitions.add(transition);
                if (transition.status() == DownloadStatus.CANCELLED) {
                    cancelled++;
                } else if (transition.cancellationRequested()) {
                    requested++;
                }
            }
        }
        if (!jobs.isEmpty()) {
            jdbcTemplate.update(
                    """
                    UPDATE download_artifact artifact
                    SET delivery_state = CASE
                            WHEN artifact.delivery_state = 'AVAILABLE' THEN 'DELETE_PENDING'
                            ELSE artifact.delivery_state
                        END,
                        expires_at = LEAST(artifact.expires_at, ?),
                        removal_reason = COALESCE(artifact.removal_reason, 'EXPIRED'),
                        lease_token = CASE
                            WHEN artifact.delivery_state = 'AVAILABLE' THEN NULL
                            ELSE artifact.lease_token
                        END,
                        lease_until = CASE
                            WHEN artifact.delivery_state = 'AVAILABLE' THEN ?
                            ELSE artifact.lease_until
                        END
                    FROM download_job job
                    WHERE artifact.job_id = job.id
                      AND job.owner_id = ? AND job.visible
                      AND artifact.delivery_state IN ('AVAILABLE', 'DELIVERING')
                    """,
                    Timestamp.from(now), Timestamp.from(now), ownerId);
            jdbcTemplate.update(
                    """
                    UPDATE download_job
                    SET visible = FALSE, updated_at = ?, version = version + 1
                    WHERE owner_id = ? AND visible
                    """,
                    Timestamp.from(now), ownerId);
        }
        return new QueueClearResult(jobs.size(), cancelled, requested, transitions);
    }

    private DownloadControlResult cancelLocked(OwnedJob job, Instant now) {
        if (job.status() == DownloadStatus.PENDING || job.status() == DownloadStatus.QUEUED) {
            jdbcTemplate.update(
                    """
                    UPDATE download_job
                    SET status = 'CANCELLED', cancellation_requested_at = ?, completed_at = ?,
                        speed = NULL, eta = NULL, error_code = NULL,
                        error_message = 'Cancelled by the user', updated_at = ?, version = version + 1
                    WHERE id = ?
                    """,
                    Timestamp.from(now), Timestamp.from(now), Timestamp.from(now), job.id());
            finishInbox(job.id(), "CANCELLED", "Cancelled before execution", now);
            return new DownloadControlResult(job.id(), DownloadStatus.CANCELLED, true);
        }
        if (job.status() == DownloadStatus.DOWNLOADING || job.status() == DownloadStatus.CONVERTING) {
            jdbcTemplate.update(
                    """
                    UPDATE download_job
                    SET cancellation_requested_at = COALESCE(cancellation_requested_at, ?),
                        updated_at = ?, version = version + 1
                    WHERE id = ?
                    """,
                    Timestamp.from(now), Timestamp.from(now), job.id());
            return new DownloadControlResult(job.id(), job.status(), true);
        }
        return new DownloadControlResult(job.id(), job.status(), job.cancellationRequested());
    }

    private boolean markCancelledLocked(UUID jobId, String detail, Instant now) {
        int updated = jdbcTemplate.update(
                """
                UPDATE download_job
                SET status = 'CANCELLED', cancellation_requested_at = COALESCE(cancellation_requested_at, ?),
                    speed = NULL, eta = NULL, error_code = NULL, error_message = ?,
                    completed_at = ?, updated_at = ?, version = version + 1
                WHERE id = ? AND status <> 'COMPLETED'
                """,
                Timestamp.from(now), safe(detail, 2_000), Timestamp.from(now), Timestamp.from(now), jobId);
        finishInbox(jobId, "CANCELLED", safe(detail, 2_000), now);
        return updated == 1;
    }

    private void finishInbox(UUID jobId, String status, String detail, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE worker_inbox
                SET processing_status = ?, claim_until = NULL, completed_at = ?, last_error = ?
                WHERE job_id = ?
                """,
                status, Timestamp.from(now), detail, jobId);
    }

    private LockedJob lockJob(UUID jobId) {
        List<LockedJob> jobs = jdbcTemplate.query(
                """
                SELECT cancellation_requested_at IS NOT NULL AS cancellation_requested
                FROM download_job WHERE id = ? FOR UPDATE
                """,
                (resultSet, rowNumber) -> new LockedJob(
                        resultSet.getBoolean("cancellation_requested")),
                jobId);
        if (jobs.isEmpty()) {
            throw new IllegalStateException("Download job disappeared during execution");
        }
        return jobs.get(0);
    }

    private Optional<OwnedJob> lockOwnedJob(UUID ownerId, UUID jobId) {
        List<OwnedJob> jobs = jdbcTemplate.query(
                """
                SELECT id, status, cancellation_requested_at IS NOT NULL AS cancellation_requested
                FROM download_job WHERE id = ? AND owner_id = ? AND visible FOR UPDATE
                """,
                (resultSet, rowNumber) -> new OwnedJob(
                        resultSet.getObject("id", UUID.class),
                        DownloadStatus.valueOf(resultSet.getString("status")),
                        resultSet.getBoolean("cancellation_requested")),
                jobId, ownerId);
        return jobs.stream().findFirst();
    }

    private String safe(String value, int maxLength) {
        String normalized = value == null || value.isBlank() ? "Download execution failed" : value.strip();
        return normalized.length() > maxLength
                ? normalized.substring(normalized.length() - maxLength)
                : normalized;
    }

    private record LockedJob(boolean cancellationRequested) {
    }

    private record OwnedJob(UUID id, DownloadStatus status, boolean cancellationRequested) {
    }
}
