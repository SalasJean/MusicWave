package com.salas.musicwave.application.download;

import com.salas.musicwave.domain.download.DownloadJob;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolveDownloadTitlesServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

    @Test
    void persistsResolvedTitles() {
        RecordingRepository repository = new RecordingRepository(List.of(
                new PendingTitleResolution(UUID.fromString("00000000-0000-0000-0000-000000000010"),
                        "https://youtu.be/abc", 0)));
        ResolveDownloadTitlesService service = service(
                repository, ignored -> TitleResolution.resolved("A real title"));

        ResolveDownloadTitlesService.ResolutionBatchReport report = service.resolveNext(10);

        assertEquals(1, report.resolved());
        assertEquals("A real title", repository.resolvedTitle);
        assertEquals(NOW, repository.updatedAt);
    }

    @Test
    void retriesTransientFailuresThenStopsAtTheConfiguredAttemptLimit() {
        RecordingRepository repository = new RecordingRepository(List.of(
                new PendingTitleResolution(UUID.randomUUID(), "https://youtu.be/one", 0),
                new PendingTitleResolution(UUID.randomUUID(), "https://youtu.be/two", 2)));
        ResolveDownloadTitlesService service = service(
                repository, ignored -> TitleResolution.retryableFailure("temporary"));

        ResolveDownloadTitlesService.ResolutionBatchReport report = service.resolveNext(10);

        assertEquals(1, report.retrying());
        assertEquals(1, report.unavailable());
        assertFalse(repository.failures.get(0).terminal());
        assertEquals(Duration.ofSeconds(10), repository.failures.get(0).delay());
        assertTrue(repository.failures.get(1).terminal());
    }

    private ResolveDownloadTitlesService service(
            RecordingRepository repository, MediaTitleResolver resolver) {
        return new ResolveDownloadTitlesService(
                repository,
                resolver,
                Clock.fixed(NOW, ZoneOffset.UTC),
                3,
                Duration.ofSeconds(10));
    }

    private static final class RecordingRepository implements DownloadJobRepository {

        private final List<PendingTitleResolution> pending;
        private final List<Failure> failures = new ArrayList<>();
        private String resolvedTitle;
        private Instant updatedAt;

        private RecordingRepository(List<PendingTitleResolution> pending) {
            this.pending = pending;
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
            return Optional.empty();
        }

        @Override
        public List<PendingTitleResolution> findPendingTitleResolutions(Instant now, int limit) {
            return pending;
        }

        @Override
        public void markTitleResolved(UUID jobId, String title, Instant now) {
            resolvedTitle = title;
            updatedAt = now;
        }

        @Override
        public void recordTitleFailure(UUID jobId, String detail, boolean terminal,
                                       Instant now, Duration retryDelay) {
            failures.add(new Failure(jobId, terminal, retryDelay));
        }
    }

    private record Failure(UUID jobId, boolean terminal, Duration delay) {
    }
}
