package com.salas.musicwave.application.artifact;

import java.util.Objects;
import java.util.UUID;

public record ArtifactDeliveryClaim(UUID jobId,
                                    UUID leaseToken,
                                    StoredArtifact artifact) {

    public ArtifactDeliveryClaim {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(leaseToken, "leaseToken");
        Objects.requireNonNull(artifact, "artifact");
    }
}
