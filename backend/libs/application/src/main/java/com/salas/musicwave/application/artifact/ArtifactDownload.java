package com.salas.musicwave.application.artifact;

import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;

public record ArtifactDownload(UUID jobId,
                               UUID leaseToken,
                               String storageKey,
                               String filename,
                               String contentType,
                               long sizeBytes,
                               String sha256,
                               InputStream inputStream) {

    public ArtifactDownload {
        Objects.requireNonNull(filename, "filename");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(sha256, "sha256");
        Objects.requireNonNull(inputStream, "inputStream");
    }

    public ArtifactDownload(String filename,
                            String contentType,
                            long sizeBytes,
                            String sha256,
                            InputStream inputStream) {
        this(null, null, null, filename, contentType, sizeBytes, sha256, inputStream);
    }
}
