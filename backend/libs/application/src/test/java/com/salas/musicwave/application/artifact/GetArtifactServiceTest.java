package com.salas.musicwave.application.artifact;

import com.salas.musicwave.application.download.DownloadNotFoundException;
import com.salas.musicwave.domain.download.DownloadStatus;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GetArtifactServiceTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID JOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID LEASE_ID = UUID.fromString("00000000-0000-0000-0000-000000000021");
    private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");
    private static final StoredArtifact ARTIFACT = new StoredArtifact(
            "owners/owner/jobs/job/artifact.mp3", "song.mp3", "audio/mpeg", 3, "a".repeat(64));

    @Test
    void claimsStreamsAndPermanentlyDeletesAfterSuccessfulDelivery() throws Exception {
        FakeRepository repository = availableRepository();
        FakeStorage storage = new FakeStorage(new byte[]{1, 2, 3});
        GetArtifactService service = service(repository, storage);

        ArtifactDownload result = service.get(OWNER_ID, JOB_ID);

        assertEquals("song.mp3", result.filename());
        assertArrayEquals(new byte[]{1, 2, 3}, result.inputStream().readAllBytes());
        assertTrue(service.completeDelivery(result));
        assertEquals(ARTIFACT.storageKey(), storage.deletedKey);
        assertTrue(repository.completedDeletion);
        assertEquals(ArtifactRemovalReason.DOWNLOADED, repository.deletion.reason());
    }

    @Test
    void releasesClaimWhenTransferIsAborted() {
        FakeRepository repository = availableRepository();
        GetArtifactService service = service(repository, new FakeStorage(new byte[]{1, 2, 3}));

        ArtifactDownload result = service.get(OWNER_ID, JOB_ID);
        service.abortDelivery(result);

        assertTrue(repository.released);
        assertFalse(repository.completedDeletion);
    }

    @Test
    void hidesUnknownAndCrossOwnerJobsAsNotFound() {
        FakeRepository repository = new FakeRepository(null);
        GetArtifactService service = service(repository, new FakeStorage(new byte[]{1}));

        assertThrows(DownloadNotFoundException.class, () -> service.get(OWNER_ID, JOB_ID));
    }

    @Test
    void rejectsAnOwnedJobBeforeArtifactCommit() {
        FakeRepository repository = new FakeRepository(
                new OwnedArtifact(JOB_ID, DownloadStatus.DOWNLOADING, null));
        GetArtifactService service = service(repository, new FakeStorage(new byte[]{1}));

        assertThrows(ArtifactNotReadyException.class, () -> service.get(OWNER_ID, JOB_ID));
    }

    @Test
    void detectsObjectSizeDriftAndReleasesDeliveryClaim() {
        FakeRepository repository = availableRepository();
        GetArtifactService service = service(repository, new FakeStorage(new byte[]{1, 2}));

        assertThrows(ArtifactMissingException.class, () -> service.get(OWNER_ID, JOB_ID));
        assertTrue(repository.released);
    }

    @Test
    void distinguishesConcurrentConsumedAndExpiredArtifacts() {
        FakeRepository delivering = new FakeRepository(new OwnedArtifact(
                JOB_ID, DownloadStatus.COMPLETED, ARTIFACT,
                ArtifactDeliveryState.DELIVERING, NOW.plusSeconds(60), null));
        assertThrows(ArtifactDeliveryInProgressException.class,
                () -> service(delivering, new FakeStorage(new byte[]{1, 2, 3})).get(OWNER_ID, JOB_ID));

        FakeRepository consumed = new FakeRepository(new OwnedArtifact(
                JOB_ID, DownloadStatus.COMPLETED, null, null, null,
                ArtifactRemovalReason.DOWNLOADED));
        ArtifactUnavailableException consumedError = assertThrows(ArtifactUnavailableException.class,
                () -> service(consumed, new FakeStorage(new byte[]{1})).get(OWNER_ID, JOB_ID));
        assertEquals("ARTIFACT_ALREADY_DOWNLOADED", consumedError.code());

        FakeRepository expired = new FakeRepository(new OwnedArtifact(
                JOB_ID, DownloadStatus.COMPLETED, ARTIFACT,
                ArtifactDeliveryState.AVAILABLE, NOW.minusSeconds(1), null));
        ArtifactUnavailableException expiredError = assertThrows(ArtifactUnavailableException.class,
                () -> service(expired, new FakeStorage(new byte[]{1, 2, 3})).get(OWNER_ID, JOB_ID));
        assertEquals("ARTIFACT_EXPIRED", expiredError.code());
    }

    @Test
    void reconcilesExpiredAndPreviouslyFailedDeletion() {
        FakeRepository repository = availableRepository();
        repository.cleanupDeletion = new ArtifactDeletion(
                JOB_ID, LEASE_ID, ARTIFACT.storageKey(), ArtifactRemovalReason.EXPIRED);
        FakeStorage storage = new FakeStorage(new byte[]{1, 2, 3});

        ArtifactCleanupResult result = service(repository, storage).cleanupExpired();

        assertEquals(new ArtifactCleanupResult(1, 1, 0), result);
        assertTrue(repository.completedDeletion);
        assertEquals(ARTIFACT.storageKey(), storage.deletedKey);
    }

    @Test
    void deletionFailureIsPersistedForRetry() {
        FakeRepository repository = availableRepository();
        FakeStorage storage = new FakeStorage(new byte[]{1, 2, 3});
        storage.failDelete = true;
        GetArtifactService service = service(repository, storage);

        ArtifactDownload result = service.get(OWNER_ID, JOB_ID);

        assertFalse(service.completeDelivery(result));
        assertTrue(repository.deletionFailureRecorded);
        assertFalse(repository.completedDeletion);
    }

    private GetArtifactService service(FakeRepository repository, FakeStorage storage) {
        return new GetArtifactService(
                repository,
                storage,
                () -> LEASE_ID,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(30),
                Duration.ofMinutes(1),
                Duration.ofSeconds(30),
                50);
    }

    private FakeRepository availableRepository() {
        return new FakeRepository(new OwnedArtifact(
                JOB_ID,
                DownloadStatus.COMPLETED,
                ARTIFACT,
                ArtifactDeliveryState.AVAILABLE,
                NOW.plusSeconds(3_600),
                null));
    }

    private static final class FakeRepository implements ArtifactAccessRepository {
        private OwnedArtifact owned;
        private boolean claimed;
        private boolean released;
        private boolean completedDeletion;
        private boolean deletionFailureRecorded;
        private ArtifactDeletion deletion;
        private ArtifactDeletion cleanupDeletion;

        private FakeRepository(OwnedArtifact owned) {
            this.owned = owned;
        }

        @Override
        public Optional<OwnedArtifact> findOwned(UUID ownerId, UUID jobId) {
            return Optional.ofNullable(owned);
        }

        @Override
        public Optional<ArtifactDeliveryClaim> claimForDelivery(UUID ownerId,
                                                                UUID jobId,
                                                                UUID leaseToken,
                                                                Instant now,
                                                                Instant leaseUntil) {
            if (owned == null
                    || owned.status() != DownloadStatus.COMPLETED
                    || owned.artifact() == null
                    || owned.deliveryState() != ArtifactDeliveryState.AVAILABLE
                    || !owned.expiresAt().isAfter(now)
                    || claimed) {
                return Optional.empty();
            }
            claimed = true;
            owned = new OwnedArtifact(
                    owned.jobId(), owned.status(), owned.artifact(),
                    ArtifactDeliveryState.DELIVERING, owned.expiresAt(), null);
            return Optional.of(new ArtifactDeliveryClaim(jobId, leaseToken, owned.artifact()));
        }

        @Override
        public boolean releaseDelivery(UUID jobId, UUID leaseToken) {
            released = true;
            claimed = false;
            if (owned != null && owned.artifact() != null) {
                owned = new OwnedArtifact(
                        owned.jobId(), owned.status(), owned.artifact(),
                        ArtifactDeliveryState.AVAILABLE, owned.expiresAt(), null);
            }
            return true;
        }

        @Override
        public boolean markDeletionPending(UUID jobId,
                                           UUID leaseToken,
                                           ArtifactRemovalReason reason,
                                           Instant leaseUntil) {
            deletion = new ArtifactDeletion(jobId, leaseToken, owned.artifact().storageKey(), reason);
            return claimed;
        }

        @Override
        public List<ArtifactDeletion> claimDeletions(UUID leaseToken,
                                                     Instant now,
                                                     Instant leaseUntil,
                                                     int limit) {
            return cleanupDeletion == null ? List.of() : List.of(cleanupDeletion);
        }

        @Override
        public boolean completeDeletion(ArtifactDeletion deletion, Instant now) {
            this.deletion = deletion;
            completedDeletion = true;
            return true;
        }

        @Override
        public void recordDeletionFailure(ArtifactDeletion deletion,
                                          Instant retryAt,
                                          String detail) {
            this.deletion = deletion;
            deletionFailureRecorded = true;
        }
    }

    private static final class FakeStorage implements ArtifactStorage {
        private final byte[] content;
        private String deletedKey;
        private boolean failDelete;

        private FakeStorage(byte[] content) {
            this.content = content;
        }

        @Override
        public StoredArtifact store(UUID ownerId,
                                    UUID jobId,
                                    com.salas.musicwave.application.execution.StagedArtifact artifact) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ArtifactStream open(StoredArtifact artifact) {
            return new ArtifactStream(new ByteArrayInputStream(content), content.length);
        }

        @Override
        public void delete(String storageKey) {
            if (failDelete) {
                throw new ArtifactStorageUnavailableException(
                        "offline", new IllegalStateException("offline"));
            }
            deletedKey = storageKey;
        }
    }
}
