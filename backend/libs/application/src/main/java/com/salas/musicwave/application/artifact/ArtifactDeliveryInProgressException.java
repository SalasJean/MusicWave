package com.salas.musicwave.application.artifact;

public final class ArtifactDeliveryInProgressException extends RuntimeException {

    public ArtifactDeliveryInProgressException() {
        super("The MP3 artifact is already being delivered");
    }
}
