package com.salas.musicwave.application.artifact;

import com.salas.musicwave.domain.download.DownloadStatus;

import java.util.Objects;
import java.time.Instant;
import java.util.UUID;

public record OwnedArtifact(UUID jobId,
                            DownloadStatus status,
                            StoredArtifact artifact,
                            ArtifactDeliveryState deliveryState,
                            Instant expiresAt,
                            ArtifactRemovalReason removalReason) {

    public OwnedArtifact {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(status, "status");
    }

    public OwnedArtifact(UUID jobId, DownloadStatus status, StoredArtifact artifact) {
        this(jobId,
                status,
                artifact,
                artifact == null ? null : ArtifactDeliveryState.AVAILABLE,
                artifact == null ? null : Instant.MAX,
                null);
    }
}
