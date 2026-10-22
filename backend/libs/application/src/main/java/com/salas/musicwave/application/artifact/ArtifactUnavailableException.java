package com.salas.musicwave.application.artifact;

public final class ArtifactUnavailableException extends RuntimeException {

    private final String code;

    public ArtifactUnavailableException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
