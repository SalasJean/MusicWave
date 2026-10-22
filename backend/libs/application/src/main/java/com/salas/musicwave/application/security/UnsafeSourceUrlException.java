package com.salas.musicwave.application.security;

public final class UnsafeSourceUrlException extends IllegalArgumentException {

    private final String reason;

    public UnsafeSourceUrlException(String reason) {
        super(reason);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
