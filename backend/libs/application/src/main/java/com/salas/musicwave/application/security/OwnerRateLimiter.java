package com.salas.musicwave.application.security;

import java.util.UUID;

public interface OwnerRateLimiter {

    RateLimitDecision consume(UUID ownerId);
}
