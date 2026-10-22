package com.salas.musicwave.application.artifact;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArtifactAccessRepository {

    Optional<OwnedArtifact> findOwned(UUID ownerId, UUID jobId);

    Optional<ArtifactDeliveryClaim> claimForDelivery(UUID ownerId,
                                                     UUID jobId,
                                                     UUID leaseToken,
                                                     Instant now,
                                                     Instant leaseUntil);

    boolean releaseDelivery(UUID jobId, UUID leaseToken);

    boolean markDeletionPending(UUID jobId,
                                UUID leaseToken,
                                ArtifactRemovalReason reason,
                                Instant leaseUntil);

    List<ArtifactDeletion> claimDeletions(UUID leaseToken,
                                         Instant now,
                                         Instant leaseUntil,
                                         int limit);

    boolean completeDeletion(ArtifactDeletion deletion, Instant now);

    void recordDeletionFailure(ArtifactDeletion deletion,
                               Instant retryAt,
                               String detail);
}
