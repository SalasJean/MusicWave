package com.salas.musicwave.domain.download;

public class InvalidSourceUrlException extends IllegalArgumentException {

    private final String reason;

    public InvalidSourceUrlException(String reason) {
        super(reason);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
