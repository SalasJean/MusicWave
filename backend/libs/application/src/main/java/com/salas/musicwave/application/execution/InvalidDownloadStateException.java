package com.salas.musicwave.application.execution;

public final class InvalidDownloadStateException extends RuntimeException {

    private final String code;

    public InvalidDownloadStateException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
