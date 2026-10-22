package com.salas.musicwave.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salas.musicwave.application.download.OwnerIdentity;
import com.salas.musicwave.application.security.OwnerRateLimiter;
import com.salas.musicwave.application.security.RateLimitDecision;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutationRateLimitInterceptorTest {

    private static final OwnerIdentity OWNER = new OwnerIdentity(UUID.randomUUID(), "test-user");

    @Test
    void permitsReadsWithoutConsumingCapacity() throws Exception {
        OwnerRateLimiter limiter = ownerId -> { throw new AssertionError("must not consume"); };
        MutationRateLimitInterceptor interceptor = interceptor(limiter);

        assertTrue(interceptor.preHandle(
                request("GET"), new MockHttpServletResponse(), new Object()));
    }

    @Test
    void returnsStableProblemAndHeadersWhenMutationLimitIsExceeded() throws Exception {
        MutationRateLimitInterceptor interceptor = interceptor(
                ownerId -> new RateLimitDecision(false, 10, 0, 23));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request("POST"), response, new Object()));

        assertEquals(429, response.getStatus());
        assertEquals("10", response.getHeader("X-RateLimit-Limit"));
        assertEquals("0", response.getHeader("X-RateLimit-Remaining"));
        assertEquals("23", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("RATE_LIMIT_EXCEEDED"));
    }

    private MutationRateLimitInterceptor interceptor(OwnerRateLimiter limiter) {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        return new MutationRateLimitInterceptor(
                () -> OWNER,
                limiter,
                objectMapper,
                Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneOffset.UTC),
                new SimpleMeterRegistry());
    }

    private MockHttpServletRequest request(String method) {
        return new MockHttpServletRequest(method, "/api/v1/downloads");
    }
}
