package com.salas.musicwave.application.artifact;

import com.salas.musicwave.application.execution.StagedArtifact;

import java.util.UUID;

public interface ArtifactStorage {

    StoredArtifact store(UUID ownerId, UUID jobId, StagedArtifact stagedArtifact);

    ArtifactStream open(StoredArtifact artifact);

    void delete(String storageKey);
}
