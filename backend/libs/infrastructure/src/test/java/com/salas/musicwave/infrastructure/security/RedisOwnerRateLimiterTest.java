package com.salas.musicwave.infrastructure.security;

import com.salas.musicwave.application.security.RateLimitDecision;
import com.salas.musicwave.application.security.RateLimitUnavailableException;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class RedisOwnerRateLimiterTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void reportsRemainingCapacityAndRetryDelayFromTheAtomicRedisScript() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        doReturn(List.of(2L, 30_001L)).when(redis)
                .execute(any(RedisScript.class), anyList(), any(), any());
        RedisOwnerRateLimiter limiter = new RedisOwnerRateLimiter(
                redis, "test:rate", 2, Duration.ofMinutes(1));

        RateLimitDecision allowed = limiter.consume(UUID.randomUUID());
        assertTrue(allowed.allowed());
        assertEquals(0, allowed.remaining());

        doReturn(List.of(3L, 30_001L)).when(redis)
                .execute(any(RedisScript.class), anyList(), any(), any());
        RateLimitDecision rejected = limiter.consume(UUID.randomUUID());
        assertFalse(rejected.allowed());
        assertEquals(31, rejected.retryAfterSeconds());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void failsClosedWhenRedisCannotAdmitTheMutation() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        doThrow(new RedisConnectionFailureException("offline")).when(redis)
                .execute(any(RedisScript.class), anyList(), any(), any());
        RedisOwnerRateLimiter limiter = new RedisOwnerRateLimiter(
                redis, "test:rate", 10, Duration.ofMinutes(1));

        assertThrows(RateLimitUnavailableException.class,
                () -> limiter.consume(UUID.randomUUID()));
    }
}
