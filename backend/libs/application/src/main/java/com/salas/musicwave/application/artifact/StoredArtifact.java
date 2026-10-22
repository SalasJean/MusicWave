package com.salas.musicwave.application.artifact;

public record StoredArtifact(String storageKey,
                             String filename,
                             String contentType,
                             long sizeBytes,
                             String sha256) {

    public StoredArtifact {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey must not be blank");
        }
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType must not be blank");
        }
        if (sizeBytes < 1) {
            throw new IllegalArgumentException("sizeBytes must be positive");
        }
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must be lowercase hexadecimal");
        }
    }
}
