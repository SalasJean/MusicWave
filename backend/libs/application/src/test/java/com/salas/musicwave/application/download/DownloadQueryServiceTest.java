package com.salas.musicwave.application.download;

import com.salas.musicwave.domain.download.DownloadJob;
import com.salas.musicwave.domain.download.SourceUrl;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DownloadQueryServiceTest {

    @Test
    void detailLookupUsesBothJobAndOwnerIdentifiers() {
        UUID owner = UUID.randomUUID();
        UUID anotherOwner = UUID.randomUUID();
        DownloadJob job = DownloadJob.pending(
                UUID.randomUUID(), owner, UUID.randomUUID(), 0,
                SourceUrl.parse("https://example.com/audio"), Instant.now());
        RecordingRepository repository = new RecordingRepository(job);
        DownloadQueryService service = new DownloadQueryService(repository);

        assertEquals(job, service.get(owner, job.id()));
        assertThrows(DownloadNotFoundException.class, () -> service.get(anotherOwner, job.id()));
        assertEquals(anotherOwner, repository.lastOwner);
    }

    private static final class RecordingRepository implements DownloadJobRepository {

        private final DownloadJob job;
        private UUID lastOwner;

        private RecordingRepository(DownloadJob job) {
            this.job = job;
        }

        @Override
        public PersistedDownloadBatch create(NewDownloadBatch batch) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DownloadJobPage findByOwner(UUID ownerId, int page, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<DownloadJob> findByIdAndOwner(UUID jobId, UUID ownerId) {
            lastOwner = ownerId;
            return job.id().equals(jobId) && job.ownerId().equals(ownerId)
                    ? Optional.of(job)
                    : Optional.empty();
        }

        @Override
        public List<PendingTitleResolution> findPendingTitleResolutions(Instant now, int limit) {
            return List.of();
        }

        @Override
        public void markTitleResolved(UUID jobId, String title, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void recordTitleFailure(UUID jobId, String detail, boolean terminal,
                                       Instant now, Duration retryDelay) {
            throw new UnsupportedOperationException();
        }
    }
}
