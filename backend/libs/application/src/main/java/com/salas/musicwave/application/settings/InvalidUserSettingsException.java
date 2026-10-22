package com.salas.musicwave.application.settings;

public final class InvalidUserSettingsException extends RuntimeException {

    private final String code;

    public InvalidUserSettingsException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
