package com.salas.musicwave.application.security;

public final class RateLimitUnavailableException extends RuntimeException {

    public RateLimitUnavailableException(Throwable cause) {
        super("Distributed rate limiting is unavailable", cause);
    }
}
