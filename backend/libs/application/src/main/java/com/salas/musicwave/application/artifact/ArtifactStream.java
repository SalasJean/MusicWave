package com.salas.musicwave.application.artifact;

import java.io.InputStream;
import java.util.Objects;

public record ArtifactStream(InputStream inputStream, long sizeBytes) {

    public ArtifactStream {
        inputStream = Objects.requireNonNull(inputStream, "inputStream");
        if (sizeBytes < 1) {
            throw new IllegalArgumentException("sizeBytes must be positive");
        }
    }
}
