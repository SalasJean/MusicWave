package com.salas.musicwave.application.artifact;

public final class ArtifactMissingException extends RuntimeException {

    public ArtifactMissingException(String message) {
        super(message);
    }

    public ArtifactMissingException(String message, Throwable cause) {
        super(message, cause);
    }
}
