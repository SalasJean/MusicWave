package com.salas.musicwave.application.artifact;

import java.util.Objects;
import java.util.UUID;

public record ArtifactDeletion(UUID jobId,
                               UUID leaseToken,
                               String storageKey,
                               ArtifactRemovalReason reason) {

    public ArtifactDeletion {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(leaseToken, "leaseToken");
        Objects.requireNonNull(storageKey, "storageKey");
        Objects.requireNonNull(reason, "reason");
    }
}
