package com.salas.musicwave.application.security;

public record RateLimitDecision(boolean allowed,
                                int limit,
                                int remaining,
                                long retryAfterSeconds) {

    public RateLimitDecision {
        if (limit < 1 || remaining < 0 || retryAfterSeconds < 0) {
            throw new IllegalArgumentException("Invalid rate-limit decision");
        }
    }
}
