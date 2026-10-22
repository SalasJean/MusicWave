package com.salas.musicwave.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salas.musicwave.application.security.OwnerRateLimiter;
import com.salas.musicwave.application.security.RateLimitDecision;
import com.salas.musicwave.application.security.RateLimitUnavailableException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class MutationRateLimitInterceptor implements HandlerInterceptor {

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");

    private final CurrentOwnerProvider ownerProvider;
    private final OwnerRateLimiter rateLimiter;
    private final SecurityProblemWriter problems;
    private final Counter allowed;
    private final Counter rejected;
    private final Counter unavailable;

    public MutationRateLimitInterceptor(CurrentOwnerProvider ownerProvider,
                                        OwnerRateLimiter rateLimiter,
                                        ObjectMapper objectMapper,
                                        Clock clock,
                                        MeterRegistry meterRegistry) {
        this.ownerProvider = ownerProvider;
        this.rateLimiter = rateLimiter;
        this.problems = new SecurityProblemWriter(objectMapper, clock);
        this.allowed = counter(meterRegistry, "allowed");
        this.rejected = counter(meterRegistry, "rejected");
        this.unavailable = counter(meterRegistry, "unavailable");
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        if (!MUTATING_METHODS.contains(request.getMethod())) {
            return true;
        }
        try {
            RateLimitDecision decision = rateLimiter.consume(ownerProvider.currentOwner().id());
            response.setHeader("X-RateLimit-Limit", Integer.toString(decision.limit()));
            response.setHeader("X-RateLimit-Remaining", Integer.toString(decision.remaining()));
            if (decision.allowed()) {
                allowed.increment();
                return true;
            }
            rejected.increment();
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
            problems.write(request, response, 429, "Too Many Requests", "RATE_LIMIT_EXCEEDED",
                    "The mutation rate limit has been exceeded");
            return false;
        } catch (RateLimitUnavailableException exception) {
            unavailable.increment();
            problems.write(request, response, 503, "Service Unavailable", "RATE_LIMIT_UNAVAILABLE",
                    "Request admission is temporarily unavailable");
            return false;
        }
    }

    private Counter counter(MeterRegistry registry, String outcome) {
        return Counter.builder("musicwave.rate.limit.requests")
                .description("Mutation admission decisions without owner cardinality")
                .tag("outcome", outcome)
                .register(registry);
    }
}
