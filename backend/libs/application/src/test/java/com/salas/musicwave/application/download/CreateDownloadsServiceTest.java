package com.salas.musicwave.application.download;

import com.salas.musicwave.domain.download.DownloadJob;
import com.salas.musicwave.domain.download.TitleStatus;
import com.salas.musicwave.application.security.UnsafeSourceUrlException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateDownloadsServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final OwnerIdentity OWNER = new OwnerIdentity(
            UUID.fromString("00000000-0000-0000-0000-000000000001"), "local-user");

    private final InMemoryRepository repository = new InMemoryRepository();
    private final AtomicLong ids = new AtomicLong(10);
    private final CreateDownloadsService service = new CreateDownloadsService(
            repository,
            () -> new UUID(0, ids.getAndIncrement()),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createsValidBulkEntriesAndReportsInvalidOnes() {
        CreateDownloadsResult result = service.create(new CreateDownloadsCommand(
                OWNER,
                DownloadRequestType.BULK,
                "bulk-1",
                List.of(
                        new RawUrlInput(1, "https://youtu.be/abc"),
                        new RawUrlInput(2, "ftp://example.com/song"),
                        new RawUrlInput(3, "   "),
                        new RawUrlInput(4, "https://example.com/audio"))));

        assertFalse(result.replayed());
        assertEquals(2, result.jobs().size());
        assertEquals(1, result.rejected().size());
        assertEquals(2, result.rejected().get(0).position());
        assertEquals("URL_SCHEME_NOT_ALLOWED", result.rejected().get(0).code());
        assertEquals(TitleStatus.PENDING, result.jobs().get(0).titleStatus());
        assertEquals(TitleStatus.NOT_APPLICABLE, result.jobs().get(1).titleStatus());
    }

    @Test
    void replaysTheOriginalJobsForTheSameIdempotentRequest() {
        CreateDownloadsCommand command = new CreateDownloadsCommand(
                OWNER,
                DownloadRequestType.SINGLE,
                "one-1",
                List.of(new RawUrlInput(1, "https://example.com/audio")));

        CreateDownloadsResult first = service.create(command);
        CreateDownloadsResult replay = service.create(command);

        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertEquals(first.requestId(), replay.requestId());
        assertEquals(first.jobs().get(0).id(), replay.jobs().get(0).id());
    }

    @Test
    void rejectsReuseOfAnIdempotencyKeyWithDifferentInput() {
        service.create(new CreateDownloadsCommand(
                OWNER, DownloadRequestType.SINGLE, "same-key",
                List.of(new RawUrlInput(1, "https://example.com/one"))));

        assertThrows(IdempotencyConflictException.class, () -> service.create(
                new CreateDownloadsCommand(
                        OWNER, DownloadRequestType.SINGLE, "same-key",
                        List.of(new RawUrlInput(1, "https://example.com/two")))));
    }

    @Test
    void aSingleInvalidUrlFailsInsteadOfProducingAPartialResponse() {
        InvalidDownloadRequestException exception = assertThrows(
                InvalidDownloadRequestException.class,
                () -> service.create(new CreateDownloadsCommand(
                        OWNER, DownloadRequestType.SINGLE, null,
                        List.of(new RawUrlInput(1, "javascript:alert(1)")))));

        assertEquals("URL_SCHEME_NOT_ALLOWED", exception.code());
    }

    @Test
    void capsTheNumberOfEntriesInOneRequest() {
        List<RawUrlInput> inputs = java.util.stream.IntStream
                .rangeClosed(1, CreateDownloadsService.MAX_URLS_PER_REQUEST + 1)
                .mapToObj(position -> new RawUrlInput(position, "https://example.com/" + position))
                .toList();

        InvalidDownloadRequestException exception = assertThrows(
                InvalidDownloadRequestException.class,
                () -> service.create(new CreateDownloadsCommand(
                        OWNER, DownloadRequestType.BULK, null, inputs)));

        assertEquals("TOO_MANY_URLS", exception.code());
    }

    @Test
    void rejectsAUrlWhenTheDestinationGuardMarksItUnsafe() {
        CreateDownloadsService guarded = new CreateDownloadsService(
                repository,
                () -> new UUID(0, ids.getAndIncrement()),
                Clock.fixed(NOW, ZoneOffset.UTC),
                sourceUrl -> { throw new UnsafeSourceUrlException("URL_DESTINATION_NOT_ALLOWED"); });

        InvalidDownloadRequestException exception = assertThrows(
                InvalidDownloadRequestException.class,
                () -> guarded.create(new CreateDownloadsCommand(
                        OWNER, DownloadRequestType.SINGLE, null,
                        List.of(new RawUrlInput(1, "http://127.0.0.1/private")))));

        assertEquals("URL_DESTINATION_NOT_ALLOWED", exception.code());
    }

    private static final class InMemoryRepository implements DownloadJobRepository {

        private final Map<String, StoredBatch> idempotent = new HashMap<>();
        private final List<DownloadJob> jobs = new ArrayList<>();

        @Override
        public PersistedDownloadBatch create(NewDownloadBatch batch) {
            String key = batch.idempotencyKey() == null
                    ? null
                    : batch.owner().id() + ":" + batch.requestType() + ":" + batch.idempotencyKey();
            if (key != null && idempotent.containsKey(key)) {
                StoredBatch stored = idempotent.get(key);
                if (!stored.fingerprint.equals(batch.requestFingerprint())) {
                    throw new IdempotencyConflictException();
                }
                return new PersistedDownloadBatch(stored.requestId, true, stored.jobs);
            }
            jobs.addAll(batch.jobs());
            if (key != null) {
                idempotent.put(key, new StoredBatch(
                        batch.requestId(), batch.requestFingerprint(), batch.jobs()));
            }
            return new PersistedDownloadBatch(batch.requestId(), false, batch.jobs());
        }

        @Override
        public DownloadJobPage findByOwner(UUID ownerId, int page, int size) {
            List<DownloadJob> owned = jobs.stream()
                    .filter(job -> job.ownerId().equals(ownerId))
                    .toList();
            return new DownloadJobPage(owned, page, size, owned.size());
        }

        @Override
        public Optional<DownloadJob> findByIdAndOwner(UUID jobId, UUID ownerId) {
            return jobs.stream()
                    .filter(job -> job.id().equals(jobId) && job.ownerId().equals(ownerId))
                    .findFirst();
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

        private record StoredBatch(UUID requestId, String fingerprint, List<DownloadJob> jobs) {
            private StoredBatch {
                jobs = List.copyOf(jobs);
            }
        }
    }
}
