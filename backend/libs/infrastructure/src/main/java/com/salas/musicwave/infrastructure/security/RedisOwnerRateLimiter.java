package com.salas.musicwave.infrastructure.security;

import com.salas.musicwave.application.security.OwnerRateLimiter;
import com.salas.musicwave.application.security.RateLimitDecision;
import com.salas.musicwave.application.security.RateLimitUnavailableException;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public final class RedisOwnerRateLimiter implements OwnerRateLimiter {

    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>(
            """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            local ttl = redis.call('PTTL', KEYS[1])
            return {current, ttl}
            """,
            List.class);

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final int limit;
    private final Duration window;

    public RedisOwnerRateLimiter(StringRedisTemplate redisTemplate,
                                 String keyPrefix,
                                 int limit,
                                 Duration window) {
        this.redisTemplate = java.util.Objects.requireNonNull(redisTemplate, "redisTemplate");
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("keyPrefix must not be blank");
        }
        if (limit < 1 || window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Rate limit and window must be positive");
        }
        this.keyPrefix = keyPrefix;
        this.limit = limit;
        this.window = window;
    }

    @Override
    public RateLimitDecision consume(UUID ownerId) {
        try {
            List<?> result = redisTemplate.execute(
                    SCRIPT,
                    List.of(keyPrefix + ":" + ownerId),
                    Integer.toString(limit),
                    Long.toString(window.toMillis()));
            if (result == null || result.size() != 2) {
                throw new IllegalStateException("Redis returned an invalid rate-limit result");
            }
            long count = ((Number) result.get(0)).longValue();
            long ttlMillis = Math.max(0, ((Number) result.get(1)).longValue());
            boolean allowed = count <= limit;
            int remaining = (int) Math.max(0, limit - count);
            long retryAfter = allowed ? 0 : Math.max(1, (ttlMillis + 999) / 1_000);
            return new RateLimitDecision(allowed, limit, remaining, retryAfter);
        } catch (RuntimeException exception) {
            throw new RateLimitUnavailableException(exception);
        }
    }
}
