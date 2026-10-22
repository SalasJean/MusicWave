package com.salas.musicwave.application.artifact;

import com.salas.musicwave.application.download.DownloadNotFoundException;
import com.salas.musicwave.application.download.IdentifierGenerator;
import com.salas.musicwave.domain.download.DownloadStatus;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class GetArtifactService {

    private static final Duration DEFAULT_DELIVERY_LEASE = Duration.ofMinutes(15);
    private static final Duration DEFAULT_DELETION_LEASE = Duration.ofMinutes(1);
    private static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(30);
    private static final int DEFAULT_CLEANUP_BATCH_SIZE = 50;

    private final ArtifactAccessRepository repository;
    private final ArtifactStorage storage;
    private final IdentifierGenerator identifiers;
    private final Clock clock;
    private final Duration deliveryLease;
    private final Duration deletionLease;
    private final Duration deletionRetryDelay;
    private final int cleanupBatchSize;

    public GetArtifactService(ArtifactAccessRepository repository, ArtifactStorage storage) {
        this(repository,
                storage,
                UUID::randomUUID,
                Clock.systemUTC(),
                DEFAULT_DELIVERY_LEASE,
                DEFAULT_DELETION_LEASE,
                DEFAULT_RETRY_DELAY,
                DEFAULT_CLEANUP_BATCH_SIZE);
    }

    public GetArtifactService(ArtifactAccessRepository repository,
                              ArtifactStorage storage,
                              IdentifierGenerator identifiers,
                              Clock clock,
                              Duration deliveryLease,
                              Duration deletionLease,
                              Duration deletionRetryDelay,
                              int cleanupBatchSize) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.identifiers = Objects.requireNonNull(identifiers, "identifiers");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.deliveryLease = positive(deliveryLease, "deliveryLease");
        this.deletionLease = positive(deletionLease, "deletionLease");
        this.deletionRetryDelay = positive(deletionRetryDelay, "deletionRetryDelay");
        if (cleanupBatchSize < 1 || cleanupBatchSize > 1_000) {
            throw new IllegalArgumentException("cleanupBatchSize must be between 1 and 1000");
        }
        this.cleanupBatchSize = cleanupBatchSize;
    }

    public ArtifactDownload get(UUID ownerId, UUID jobId) {
        Instant now = clock.instant();
        UUID leaseToken = identifiers.next();
        ArtifactDeliveryClaim claim = repository.claimForDelivery(
                        ownerId, jobId, leaseToken, now, now.plus(deliveryLease))
                .orElseGet(() -> unavailable(ownerId, jobId, now));

        ArtifactStream stream;
        try {
            stream = storage.open(claim.artifact());
        } catch (RuntimeException exception) {
            repository.releaseDelivery(jobId, leaseToken);
            throw exception;
        }
        if (stream.sizeBytes() != claim.artifact().sizeBytes()) {
            closeQuietly(stream);
            repository.releaseDelivery(jobId, leaseToken);
            throw new ArtifactMissingException("The stored artifact does not match its metadata");
        }
        return new ArtifactDownload(
                claim.jobId(),
                claim.leaseToken(),
                claim.artifact().storageKey(),
                claim.artifact().filename(),
                claim.artifact().contentType(),
                claim.artifact().sizeBytes(),
                claim.artifact().sha256(),
                stream.inputStream());
    }

    public boolean completeDelivery(ArtifactDownload download) {
        requireManaged(download);
        Instant now = clock.instant();
        if (!repository.markDeletionPending(
                download.jobId(),
                download.leaseToken(),
                ArtifactRemovalReason.DOWNLOADED,
                now.plus(deletionLease))) {
            return false;
        }
        return delete(new ArtifactDeletion(
                download.jobId(),
                download.leaseToken(),
                download.storageKey(),
                ArtifactRemovalReason.DOWNLOADED));
    }

    public void abortDelivery(ArtifactDownload download) {
        requireManaged(download);
        repository.releaseDelivery(download.jobId(), download.leaseToken());
    }

    public ArtifactCleanupResult cleanupExpired() {
        Instant now = clock.instant();
        UUID leaseToken = identifiers.next();
        List<ArtifactDeletion> deletions = repository.claimDeletions(
                leaseToken, now, now.plus(deletionLease), cleanupBatchSize);
        int deleted = 0;
        int failed = 0;
        for (ArtifactDeletion deletion : deletions) {
            if (delete(deletion)) {
                deleted++;
            } else {
                failed++;
            }
        }
        return new ArtifactCleanupResult(deletions.size(), deleted, failed);
    }

    private ArtifactDeliveryClaim unavailable(UUID ownerId, UUID jobId, Instant now) {
        OwnedArtifact owned = repository.findOwned(ownerId, jobId)
                .orElseThrow(() -> new DownloadNotFoundException(jobId));
        if (owned.removalReason() == ArtifactRemovalReason.DOWNLOADED) {
            throw new ArtifactUnavailableException(
                    "ARTIFACT_ALREADY_DOWNLOADED",
                    "The MP3 artifact was removed after its successful delivery");
        }
        if (owned.removalReason() == ArtifactRemovalReason.EXPIRED) {
            throw new ArtifactUnavailableException(
                    "ARTIFACT_EXPIRED",
                    "The temporary MP3 artifact expired before it was downloaded");
        }
        if (owned.status() != DownloadStatus.COMPLETED || owned.artifact() == null) {
            throw new ArtifactNotReadyException();
        }
        if (owned.expiresAt() != null && !owned.expiresAt().isAfter(now)) {
            throw new ArtifactUnavailableException(
                    "ARTIFACT_EXPIRED",
                    "The temporary MP3 artifact expired before it was downloaded");
        }
        if (owned.deliveryState() == ArtifactDeliveryState.DELIVERING) {
            throw new ArtifactDeliveryInProgressException();
        }
        if (owned.deliveryState() == ArtifactDeliveryState.DELETE_PENDING) {
            String code = owned.removalReason() == ArtifactRemovalReason.DOWNLOADED
                    ? "ARTIFACT_ALREADY_DOWNLOADED"
                    : "ARTIFACT_EXPIRED";
            throw new ArtifactUnavailableException(
                    code,
                    "The temporary MP3 artifact is no longer available");
        }
        throw new ArtifactDeliveryInProgressException();
    }

    private boolean delete(ArtifactDeletion deletion) {
        try {
            storage.delete(deletion.storageKey());
            return repository.completeDeletion(deletion, clock.instant());
        } catch (RuntimeException exception) {
            repository.recordDeletionFailure(
                    deletion,
                    clock.instant().plus(deletionRetryDelay),
                    safe(exception.getMessage()));
            return false;
        }
    }

    private void requireManaged(ArtifactDownload download) {
        Objects.requireNonNull(download, "download");
        if (download.jobId() == null
                || download.leaseToken() == null
                || download.storageKey() == null) {
            throw new IllegalArgumentException("download is not managed by an artifact delivery lease");
        }
    }

    private void closeQuietly(ArtifactStream stream) {
        try {
            stream.inputStream().close();
        } catch (IOException ignored) {
            // The authoritative failure is the object/metadata size mismatch.
        }
    }

    private Duration positive(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private String safe(String value) {
        String normalized = value == null || value.isBlank()
                ? "Artifact deletion failed"
                : value.strip();
        return normalized.length() > 1_000
                ? normalized.substring(0, 1_000)
                : normalized;
    }
}
