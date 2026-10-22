package com.salas.musicwave.infrastructure.download;

import com.salas.musicwave.application.download.DownloadJobPage;
import com.salas.musicwave.application.download.DownloadJobRepository;
import com.salas.musicwave.application.download.DownloadQuotaExceededException;
import com.salas.musicwave.application.download.IdempotencyConflictException;
import com.salas.musicwave.application.download.NewDownloadBatch;
import com.salas.musicwave.application.download.PendingTitleResolution;
import com.salas.musicwave.application.download.PersistedDownloadBatch;
import com.salas.musicwave.domain.download.DownloadJob;
import com.salas.musicwave.domain.download.DownloadStatus;
import com.salas.musicwave.domain.download.TitleStatus;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcDownloadJobRepository implements DownloadJobRepository {

    private static final String JOB_COLUMNS = """
            id, owner_id, request_id, request_index, source_url, title, title_status,
            status, progress, speed, eta, error_code, error_message, retry_count,
            version, created_at, updated_at, started_at, completed_at
            """;

    private static final String INSERT_REQUEST = """
            INSERT INTO download_request (
                id, owner_id, request_type, idempotency_key, request_fingerprint, created_at
            ) VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (owner_id, request_type, idempotency_key)
                WHERE idempotency_key IS NOT NULL
            DO NOTHING
            """;

    private static final String INSERT_JOB = """
            INSERT INTO download_job (
                id, owner_id, request_id, request_index, source_url, title, title_status,
                status, progress, speed, eta, error_code, error_message, retry_count,
                version, created_at, updated_at, started_at, completed_at,
                next_title_lookup_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final int maxActiveJobsPerOwner;

    public JdbcDownloadJobRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, 1_000);
    }

    @Autowired
    public JdbcDownloadJobRepository(
            JdbcTemplate jdbcTemplate,
            @Value("${musicwave.security.max-active-jobs-per-owner:1000}") int maxActiveJobsPerOwner) {
        this.jdbcTemplate = jdbcTemplate;
        if (maxActiveJobsPerOwner < 1) {
            throw new IllegalArgumentException("max-active-jobs-per-owner must be positive");
        }
        this.maxActiveJobsPerOwner = maxActiveJobsPerOwner;
    }

    @Override
    @Transactional
    public PersistedDownloadBatch create(NewDownloadBatch batch) {
        ensureOwner(batch);
        int inserted = jdbcTemplate.update(
                INSERT_REQUEST,
                batch.requestId(),
                batch.owner().id(),
                batch.requestType().name(),
                batch.idempotencyKey(),
                batch.requestFingerprint(),
                Timestamp.from(batch.createdAt()));

        if (inserted == 0) {
            RequestRecord existing = findRequestByIdempotencyKey(batch)
                    .orElseThrow(() -> new IllegalStateException("Idempotent request disappeared"));
            if (!existing.fingerprint().equals(batch.requestFingerprint())) {
                throw new IdempotencyConflictException();
            }
            return new PersistedDownloadBatch(
                    existing.id(), true, findByRequestId(existing.id()));
        }

        lockOwnerAndEnforceQuota(batch);

        batch.jobs().forEach(this::insertJob);
        return new PersistedDownloadBatch(batch.requestId(), false, batch.jobs());
    }

    private void lockOwnerAndEnforceQuota(NewDownloadBatch batch) {
        jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE id = ? FOR UPDATE",
                UUID.class,
                batch.owner().id());
        Integer activeJobs = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM download_job
                WHERE owner_id = ?
                  AND status IN ('PENDING', 'QUEUED', 'DOWNLOADING', 'CONVERTING')
                """,
                Integer.class,
                batch.owner().id());
        int active = activeJobs == null ? 0 : activeJobs;
        if (active + batch.jobs().size() > maxActiveJobsPerOwner) {
            throw new DownloadQuotaExceededException(maxActiveJobsPerOwner);
        }
    }

    @Override
    public DownloadJobPage findByOwner(UUID ownerId, int page, int size) {
        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM download_job WHERE owner_id = ? AND visible", Long.class, ownerId);
        List<DownloadJob> items = jdbcTemplate.query(
                "SELECT " + JOB_COLUMNS + " FROM download_job "
                        + "WHERE owner_id = ? AND visible ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                this::mapJob,
                ownerId,
                size,
                (long) page * size);
        return new DownloadJobPage(items, page, size, total == null ? 0 : total);
    }

    @Override
    public Optional<DownloadJob> findByIdAndOwner(UUID jobId, UUID ownerId) {
        List<DownloadJob> jobs = jdbcTemplate.query(
                "SELECT " + JOB_COLUMNS + " FROM download_job WHERE id = ? AND owner_id = ? AND visible",
                this::mapJob,
                jobId,
                ownerId);
        return jobs.stream().findFirst();
    }

    @Override
    public List<PendingTitleResolution> findPendingTitleResolutions(Instant now, int limit) {
        return jdbcTemplate.query(
                """
                SELECT id, source_url, title_lookup_attempts
                FROM download_job
                WHERE title_status = 'PENDING'
                  AND (next_title_lookup_at IS NULL OR next_title_lookup_at <= ?)
                ORDER BY created_at, id
                LIMIT ?
                """,
                (resultSet, rowNumber) -> new PendingTitleResolution(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("source_url"),
                        resultSet.getInt("title_lookup_attempts")),
                Timestamp.from(now),
                limit);
    }

    @Override
    public void markTitleResolved(UUID jobId, String title, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE download_job
                SET title = ?, title_status = 'RESOLVED', title_lookup_detail = NULL,
                    next_title_lookup_at = NULL, updated_at = ?, version = version + 1
                WHERE id = ? AND title_status = 'PENDING'
                """,
                title,
                Timestamp.from(now),
                jobId);
    }

    @Override
    public void recordTitleFailure(UUID jobId,
                                   String detail,
                                   boolean terminal,
                                   Instant now,
                                   Duration retryDelay) {
        String safeDetail = detail == null ? "Title lookup failed" : detail;
        if (safeDetail.length() > 1_000) {
            safeDetail = safeDetail.substring(0, 1_000);
        }
        jdbcTemplate.update(
                """
                UPDATE download_job
                SET title_status = ?, title_lookup_attempts = title_lookup_attempts + 1,
                    title_lookup_detail = ?, next_title_lookup_at = ?,
                    updated_at = ?, version = version + 1
                WHERE id = ? AND title_status = 'PENDING'
                """,
                terminal ? TitleStatus.UNAVAILABLE.name() : TitleStatus.PENDING.name(),
                safeDetail,
                terminal ? null : Timestamp.from(now.plus(retryDelay)),
                Timestamp.from(now),
                jobId);
    }

    private void ensureOwner(NewDownloadBatch batch) {
        jdbcTemplate.update(
                """
                INSERT INTO app_user (id, external_subject, created_at)
                VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING
                """,
                batch.owner().id(),
                batch.owner().externalSubject(),
                Timestamp.from(batch.createdAt()));
    }

    private Optional<RequestRecord> findRequestByIdempotencyKey(NewDownloadBatch batch) {
        List<RequestRecord> requests = jdbcTemplate.query(
                """
                SELECT id, request_fingerprint
                FROM download_request
                WHERE owner_id = ? AND request_type = ? AND idempotency_key = ?
                """,
                (resultSet, rowNumber) -> new RequestRecord(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("request_fingerprint")),
                batch.owner().id(),
                batch.requestType().name(),
                batch.idempotencyKey());
        return requests.stream().findFirst();
    }

    private List<DownloadJob> findByRequestId(UUID requestId) {
        return jdbcTemplate.query(
                "SELECT " + JOB_COLUMNS + " FROM download_job "
                        + "WHERE request_id = ? ORDER BY request_index",
                this::mapJob,
                requestId);
    }

    private void insertJob(DownloadJob job) {
        jdbcTemplate.update(
                INSERT_JOB,
                job.id(),
                job.ownerId(),
                job.requestId(),
                job.requestIndex(),
                job.sourceUrl(),
                job.title(),
                job.titleStatus().name(),
                job.status().name(),
                job.progress(),
                job.speed(),
                job.eta(),
                job.errorCode(),
                job.errorMessage(),
                job.retryCount(),
                job.version(),
                Timestamp.from(job.createdAt()),
                Timestamp.from(job.updatedAt()),
                timestamp(job.startedAt()),
                timestamp(job.completedAt()),
                job.titleStatus() == TitleStatus.PENDING ? Timestamp.from(job.createdAt()) : null);
    }

    private DownloadJob mapJob(ResultSet resultSet, int rowNumber) throws SQLException {
        return new DownloadJob(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("owner_id", UUID.class),
                resultSet.getObject("request_id", UUID.class),
                resultSet.getInt("request_index"),
                resultSet.getString("source_url"),
                resultSet.getString("title"),
                TitleStatus.valueOf(resultSet.getString("title_status")),
                DownloadStatus.valueOf(resultSet.getString("status")),
                resultSet.getBigDecimal("progress"),
                resultSet.getString("speed"),
                resultSet.getString("eta"),
                resultSet.getString("error_code"),
                resultSet.getString("error_message"),
                resultSet.getInt("retry_count"),
                resultSet.getLong("version"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                instant(resultSet.getTimestamp("started_at")),
                instant(resultSet.getTimestamp("completed_at")));
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private record RequestRecord(UUID id, String fingerprint) {
    }
}
