package com.salas.musicwave.application.download;

import java.util.Objects;

public record TitleResolution(Outcome outcome, String title, String detail) {

    public TitleResolution {
        Objects.requireNonNull(outcome, "outcome");
        title = Objects.requireNonNullElse(title, "");
        detail = Objects.requireNonNullElse(detail, "");
    }

    public static TitleResolution resolved(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("resolved title must not be blank");
        }
        return new TitleResolution(Outcome.RESOLVED, title.trim(), "");
    }

    public static TitleResolution unavailable(String detail) {
        return new TitleResolution(Outcome.UNAVAILABLE, "", detail);
    }

    public static TitleResolution retryableFailure(String detail) {
        return new TitleResolution(Outcome.RETRYABLE_FAILURE, "", detail);
    }

    public enum Outcome {
        RESOLVED,
        UNAVAILABLE,
        RETRYABLE_FAILURE
    }
}
