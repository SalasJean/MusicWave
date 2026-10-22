package com.salas.musicwave.infrastructure.artifact;

import com.salas.musicwave.application.artifact.ArtifactDeletion;
import com.salas.musicwave.application.artifact.ArtifactDeliveryClaim;
import com.salas.musicwave.application.artifact.ArtifactRemovalReason;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "MUSICWAVE_INTEGRATION_DB_URL", matches = ".+")
class JdbcArtifactAccessRepositoryIntegrationTest {

    @Test
    void exclusivelyClaimsAndFinalizesOneShotDelivery() {
        Fixture fixture = fixture(Instant.now().plusSeconds(3_600));
        JdbcArtifactAccessRepository repository = new JdbcArtifactAccessRepository(fixture.jdbc());
        UUID deliveryToken = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            ArtifactDeliveryClaim claim = repository.claimForDelivery(
                            fixture.ownerId(), fixture.jobId(), deliveryToken,
                            now, now.plusSeconds(60))
                    .orElseThrow();

            assertTrue(repository.claimForDelivery(
                    fixture.ownerId(), fixture.jobId(), UUID.randomUUID(),
                    now, now.plusSeconds(60)).isEmpty());
            assertTrue(repository.markDeletionPending(
                    fixture.jobId(), deliveryToken,
                    ArtifactRemovalReason.DOWNLOADED, now.plusSeconds(60)));
            ArtifactDeletion deletion = new ArtifactDeletion(
                    fixture.jobId(), deliveryToken, claim.artifact().storageKey(),
                    ArtifactRemovalReason.DOWNLOADED);
            assertTrue(repository.completeDeletion(deletion, now.plusSeconds(1)));

            assertEquals(0, fixture.jdbc().queryForObject(
                    "SELECT count(*) FROM download_artifact WHERE job_id = ?",
                    Integer.class, fixture.jobId()));
            assertEquals("DOWNLOADED", fixture.jdbc().queryForObject(
                    "SELECT artifact_removal_reason FROM download_job WHERE id = ?",
                    String.class, fixture.jobId()));
        } finally {
            fixture.cleanup();
        }
    }

    @Test
    void claimsExpiredAndAbandonedArtifactsForIdempotentCleanup() {
        Fixture fixture = fixture(Instant.now().minusSeconds(1));
        JdbcArtifactAccessRepository repository = new JdbcArtifactAccessRepository(fixture.jdbc());
        UUID cleanupToken = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            List<ArtifactDeletion> claimed = repository.claimDeletions(
                    cleanupToken, now, now.plusSeconds(60), 10);

            assertEquals(1, claimed.size());
            assertEquals(ArtifactRemovalReason.EXPIRED, claimed.get(0).reason());
            assertTrue(repository.completeDeletion(claimed.get(0), now));
            assertEquals("EXPIRED", fixture.jdbc().queryForObject(
                    "SELECT artifact_removal_reason FROM download_job WHERE id = ?",
                    String.class, fixture.jobId()));
        } finally {
            fixture.cleanup();
        }
    }

    private Fixture fixture(Instant expiresAt) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource());
        UUID ownerId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO app_user (id, external_subject, created_at) VALUES (?, ?, ?)",
                ownerId, "artifact-" + ownerId, Timestamp.from(now));
        jdbc.update("""
                INSERT INTO download_request
                    (id, owner_id, request_type, request_fingerprint, created_at)
                VALUES (?, ?, 'SINGLE', ?, ?)
                """, requestId, ownerId, "a".repeat(64), Timestamp.from(now));
        jdbc.update("""
                INSERT INTO download_job
                    (id, owner_id, source_url, title, status, progress, request_id,
                     request_index, title_status, created_at, updated_at, completed_at)
                VALUES (?, ?, 'https://example.com/audio', 'audio', 'COMPLETED', 100,
                        ?, 0, 'NOT_APPLICABLE', ?, ?, ?)
                """, jobId, ownerId, requestId,
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
        jdbc.update("""
                INSERT INTO download_artifact
                    (id, job_id, storage_key, original_filename, content_type,
                     size_bytes, sha256, created_at, expires_at)
                VALUES (?, ?, ?, 'song.mp3', 'audio/mpeg', 3, ?, ?, ?)
                """, UUID.randomUUID(), jobId,
                "owners/" + ownerId + "/jobs/" + jobId + "/artifact.mp3",
                "b".repeat(64), Timestamp.from(now), Timestamp.from(expiresAt));
        return new Fixture(jdbc, ownerId, requestId, jobId);
    }

    private DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(System.getenv("MUSICWAVE_INTEGRATION_DB_URL"));
        dataSource.setUsername(System.getenv().getOrDefault(
                "MUSICWAVE_INTEGRATION_DB_USER", "musicwave"));
        dataSource.setPassword(System.getenv().getOrDefault(
                "MUSICWAVE_INTEGRATION_DB_PASSWORD", "musicwave"));
        return dataSource;
    }

    private record Fixture(JdbcTemplate jdbc, UUID ownerId, UUID requestId, UUID jobId) {

        void cleanup() {
            jdbc.update("DELETE FROM download_artifact WHERE job_id = ?", jobId);
            jdbc.update("DELETE FROM download_job WHERE id = ?", jobId);
            jdbc.update("DELETE FROM download_request WHERE id = ?", requestId);
            jdbc.update("DELETE FROM app_user WHERE id = ?", ownerId);
        }
    }
}
