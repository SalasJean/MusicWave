package com.salas.musicwave.application.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class PublishOutboxService {

    private final OutboxRepository repository;
    private final OutboxPublisher publisher;
    private final Clock clock;
    private final Duration leaseDuration;
    private final Duration retryBaseDelay;
    private final Duration retryMaxDelay;

    public PublishOutboxService(OutboxRepository repository,
                                OutboxPublisher publisher,
                                Clock clock,
                                Duration leaseDuration,
                                Duration retryBaseDelay,
                                Duration retryMaxDelay) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.leaseDuration = requirePositive(leaseDuration, "leaseDuration");
        this.retryBaseDelay = requirePositive(retryBaseDelay, "retryBaseDelay");
        this.retryMaxDelay = requirePositive(retryMaxDelay, "retryMaxDelay");
    }

    public OutboxPublishResult publishAvailable(String publisherId, int limit) {
        if (publisherId == null || publisherId.isBlank()) {
            throw new IllegalArgumentException("publisherId must not be blank");
        }
        if (limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        Instant claimedAt = clock.instant();
        List<OutboxEvent> events = repository.claimAvailable(
                publisherId, claimedAt, leaseDuration, limit);
        int published = 0;
        int failed = 0;
        for (OutboxEvent event : events) {
            try {
                publisher.publish(event);
                repository.markPublished(event.id(), publisherId, clock.instant());
                published++;
            } catch (RuntimeException exception) {
                repository.recordFailure(
                        event.id(),
                        publisherId,
                        clock.instant().plus(retryDelay(event.publishAttempts())),
                        safeDetail(exception));
                failed++;
            }
        }
        return new OutboxPublishResult(events.size(), published, failed);
    }

    private Duration retryDelay(int previousAttempts) {
        int exponent = Math.min(previousAttempts, 20);
        Duration candidate;
        try {
            candidate = retryBaseDelay.multipliedBy(1L << exponent);
        } catch (ArithmeticException exception) {
            candidate = retryMaxDelay;
        }
        return candidate.compareTo(retryMaxDelay) > 0 ? retryMaxDelay : candidate;
    }

    private String safeDetail(RuntimeException exception) {
        String detail = exception.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = exception.getClass().getSimpleName();
        }
        return detail.length() > 1_000 ? detail.substring(0, 1_000) : detail;
    }

    private Duration requirePositive(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
