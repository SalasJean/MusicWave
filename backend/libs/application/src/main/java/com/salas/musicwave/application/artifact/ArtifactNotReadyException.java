package com.salas.musicwave.application.artifact;

public final class ArtifactNotReadyException extends RuntimeException {

    public ArtifactNotReadyException() {
        super("The download artifact is not ready");
    }
}
