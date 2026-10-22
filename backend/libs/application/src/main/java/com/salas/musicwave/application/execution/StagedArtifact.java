package com.salas.musicwave.application.execution;

import java.nio.file.Path;

public record StagedArtifact(Path path,
                             String filename,
                             long sizeBytes) {

    public StagedArtifact {
        path = java.util.Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        if (sizeBytes < 1) {
            throw new IllegalArgumentException("sizeBytes must be positive");
        }
    }
}
