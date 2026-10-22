package com.salas.musicwave.application.download;

public record RawUrlInput(int position, String value) {

    public RawUrlInput {
        if (position < 1) {
            throw new IllegalArgumentException("position must be positive");
        }
    }
}
