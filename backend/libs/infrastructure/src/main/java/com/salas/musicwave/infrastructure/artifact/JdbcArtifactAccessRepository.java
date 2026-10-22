package com.salas.musicwave.infrastructure.artifact;

import com.salas.musicwave.application.artifact.ArtifactAccessRepository;
import com.salas.musicwave.application.artifact.ArtifactDeletion;
import com.salas.musicwave.application.artifact.ArtifactDeliveryClaim;
import com.salas.musicwave.application.artifact.ArtifactDeliveryState;
import com.salas.musicwave.application.artifact.ArtifactRemovalReason;
import com.salas.musicwave.application.artifact.OwnedArtifact;
import com.salas.musicwave.application.artifact.StoredArtifact;
import com.salas.musicwave.domain.download.DownloadStatus;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcArtifactAccessRepository implements ArtifactAccessRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcArtifactAccessRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<OwnedArtifact> findOwned(UUID ownerId, UUID jobId) {
        List<OwnedArtifact> results = jdbcTemplate.query(
                """
                SELECT j.id, j.status, j.artifact_removal_reason,
                       a.storage_key, a.original_filename, a.content_type, a.size_bytes,
                       a.sha256, a.delivery_state, a.expires_at, a.removal_reason
                FROM download_job j
                LEFT JOIN download_artifact a ON a.job_id = j.id
                WHERE j.owner_id = ? AND j.id = ? AND j.visible
                """,
                (resultSet, rowNumber) -> {
                    String storageKey = resultSet.getString("storage_key");
                    StoredArtifact artifact = storageKey == null ? null : new StoredArtifact(
                            storageKey,
                            resultSet.getString("original_filename"),
                            resultSet.getString("content_type"),
                            resultSet.getLong("size_bytes"),
                            resultSet.getString("sha256"));
                    return new OwnedArtifact(
                            resultSet.getObject("id", UUID.class),
                            DownloadStatus.valueOf(resultSet.getString("status")),
                            artifact,
                            nullableState(resultSet.getString("delivery_state")),
                            nullableInstant(resultSet.getTimestamp("expires_at")),
                            nullableReason(resultSet.getString("removal_reason") == null
                                    ? resultSet.getString("artifact_removal_reason")
                                    : resultSet.getString("removal_reason")));
                },
                ownerId,
                jobId);
        return results.stream().findFirst();
    }

    @Override
    @Transactional
    public Optional<ArtifactDeliveryClaim> claimForDelivery(UUID ownerId,
                                                            UUID jobId,
                                                            UUID leaseToken,
                                                            Instant now,
                                                            Instant leaseUntil) {
        List<ArtifactDeliveryClaim> results = jdbcTemplate.query(
                """
                UPDATE download_artifact artifact
                SET delivery_state = 'DELIVERING', lease_token = ?, lease_until = ?,
                    last_deletion_error = NULL
                FROM download_job job
                WHERE artifact.job_id = job.id
                  AND job.owner_id = ? AND job.id = ? AND job.visible
                  AND job.status = 'COMPLETED'
                  AND artifact.delivery_state = 'AVAILABLE'
                  AND artifact.expires_at > ?
                RETURNING artifact.job_id, artifact.storage_key, artifact.original_filename,
                          artifact.content_type, artifact.size_bytes, artifact.sha256
                """,
                (resultSet, rowNumber) -> new ArtifactDeliveryClaim(
                        resultSet.getObject("job_id", UUID.class),
                        leaseToken,
                        new StoredArtifact(
                                resultSet.getString("storage_key"),
                                resultSet.getString("original_filename"),
                                resultSet.getString("content_type"),
                                resultSet.getLong("size_bytes"),
                                resultSet.getString("sha256"))),
                leaseToken,
                Timestamp.from(leaseUntil),
                ownerId,
                jobId,
                Timestamp.from(now));
        return results.stream().findFirst();
    }

    @Override
    public boolean releaseDelivery(UUID jobId, UUID leaseToken) {
        return jdbcTemplate.update(
                """
                UPDATE download_artifact
                SET delivery_state = 'AVAILABLE', lease_token = NULL, lease_until = NULL
                WHERE job_id = ? AND delivery_state = 'DELIVERING' AND lease_token = ?
                """,
                jobId,
                leaseToken) == 1;
    }

    @Override
    public boolean markDeletionPending(UUID jobId,
                                       UUID leaseToken,
                                       ArtifactRemovalReason reason,
                                       Instant leaseUntil) {
        return jdbcTemplate.update(
                """
                UPDATE download_artifact
                SET delivery_state = 'DELETE_PENDING', removal_reason = ?, lease_until = ?,
                    deletion_attempts = deletion_attempts + 1, last_deletion_error = NULL
                WHERE job_id = ? AND delivery_state = 'DELIVERING' AND lease_token = ?
                """,
                reason.name(),
                Timestamp.from(leaseUntil),
                jobId,
                leaseToken) == 1;
    }

    @Override
    @Transactional
    public List<ArtifactDeletion> claimDeletions(UUID leaseToken,
                                                 Instant now,
                                                 Instant leaseUntil,
                                                 int limit) {
        return jdbcTemplate.query(
                """
                WITH candidates AS (
                    SELECT artifact.job_id
                    FROM download_artifact artifact
                    WHERE (artifact.delivery_state = 'AVAILABLE' AND artifact.expires_at <= ?)
                       OR (artifact.delivery_state IN ('DELIVERING', 'DELETE_PENDING')
                           AND artifact.lease_until <= ?)
                    ORDER BY artifact.expires_at, artifact.job_id
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE download_artifact artifact
                SET delivery_state = 'DELETE_PENDING',
                    removal_reason = COALESCE(artifact.removal_reason, 'EXPIRED'),
                    lease_token = ?, lease_until = ?,
                    deletion_attempts = artifact.deletion_attempts + 1,
                    last_deletion_error = NULL
                FROM candidates
                WHERE artifact.job_id = candidates.job_id
                RETURNING artifact.job_id, artifact.storage_key, artifact.removal_reason
                """,
                (resultSet, rowNumber) -> new ArtifactDeletion(
                        resultSet.getObject("job_id", UUID.class),
                        leaseToken,
                        resultSet.getString("storage_key"),
                        ArtifactRemovalReason.valueOf(resultSet.getString("removal_reason"))),
                Timestamp.from(now),
                Timestamp.from(now),
                limit,
                leaseToken,
                Timestamp.from(leaseUntil));
    }

    @Override
    @Transactional
    public boolean completeDeletion(ArtifactDeletion deletion, Instant now) {
        int updated = jdbcTemplate.update(
                """
                WITH removed AS (
                    DELETE FROM download_artifact
                    WHERE job_id = ? AND delivery_state = 'DELETE_PENDING' AND lease_token = ?
                    RETURNING job_id, removal_reason
                )
                UPDATE download_job job
                SET artifact_removed_at = ?, artifact_removal_reason = removed.removal_reason,
                    updated_at = ?, version = version + 1
                FROM removed
                WHERE job.id = removed.job_id
                """,
                deletion.jobId(),
                deletion.leaseToken(),
                Timestamp.from(now),
                Timestamp.from(now));
        return updated == 1;
    }

    @Override
    public void recordDeletionFailure(ArtifactDeletion deletion,
                                      Instant retryAt,
                                      String detail) {
        jdbcTemplate.update(
                """
                UPDATE download_artifact
                SET delivery_state = 'DELETE_PENDING', lease_token = NULL, lease_until = ?,
                    last_deletion_error = ?
                WHERE job_id = ? AND delivery_state = 'DELETE_PENDING' AND lease_token = ?
                """,
                Timestamp.from(retryAt),
                detail,
                deletion.jobId(),
                deletion.leaseToken());
    }

    private static ArtifactDeliveryState nullableState(String value) {
        return value == null ? null : ArtifactDeliveryState.valueOf(value);
    }

    private static ArtifactRemovalReason nullableReason(String value) {
        return value == null ? null : ArtifactRemovalReason.valueOf(value);
    }

    private static Instant nullableInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
