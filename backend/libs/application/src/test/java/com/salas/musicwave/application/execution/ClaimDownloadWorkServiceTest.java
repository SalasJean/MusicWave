package com.salas.musicwave.application.execution;

import com.salas.musicwave.application.artifact.StoredArtifact;
import com.salas.musicwave.domain.download.DownloadStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClaimDownloadWorkServiceTest {

    @Test
    void passesThePerOwnerConcurrencyCapToTheAtomicClaim() {
        RecordingRepository repository = new RecordingRepository();
        Instant now = Instant.parse("2026-10-11T12:00:00Z");
        ClaimDownloadWorkService service = new ClaimDownloadWorkService(
                repository, Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(1), 2);

        service.claim("worker-1", 5);

        assertEquals(5, repository.limit);
        assertEquals(2, repository.maxPerOwner);
        assertEquals(now, repository.now);
    }

    @Test
    void rejectsAnInvalidPerOwnerCap() {
        assertThrows(IllegalArgumentException.class, () -> new ClaimDownloadWorkService(
                new RecordingRepository(), Clock.systemUTC(), Duration.ofMinutes(1), 0));
    }

    private static final class RecordingRepository implements DownloadExecutionRepository {
        private int limit;
        private int maxPerOwner;
        private Instant now;

        @Override
        public List<QueuedDownloadWork> claimReady(
                String workerId, Instant now, Duration leaseDuration, int limit, int maxPerOwner) {
            this.limit = limit;
            this.maxPerOwner = maxPerOwner;
            this.now = now;
            return List.of();
        }

        @Override public void renewLeases(String workerId, List<UUID> jobIds, Instant now, Duration leaseDuration) { }
        @Override public boolean markDownloading(UUID jobId, String workerId, Instant now) { return false; }
        @Override public boolean checkpointProgress(UUID jobId, BigDecimal progress, String speed, String eta, Instant now) { return false; }
        @Override public boolean markConverting(UUID jobId, Instant now) { return false; }
        @Override public void updateTitle(UUID jobId, String title, Instant now) { }
        @Override public boolean cancellationRequested(UUID jobId) { return false; }
        @Override public boolean markCompleted(UUID jobId, StoredArtifact artifact, Instant artifactExpiresAt, Instant now) { return false; }
        @Override public DownloadStatus markFailed(UUID jobId, String errorCode, String errorMessage, Instant now) { return DownloadStatus.FAILED; }
        @Override public boolean markCancelled(UUID jobId, String detail, Instant now) { return false; }
    }
}
