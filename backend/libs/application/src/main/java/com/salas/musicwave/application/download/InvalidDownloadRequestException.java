package com.salas.musicwave.application.download;

public class InvalidDownloadRequestException extends RuntimeException {

    private final String code;

    public InvalidDownloadRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
