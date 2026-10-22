package com.salas.musicwave.application.queue;

import com.salas.musicwave.application.download.IdentifierGenerator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class HandleDispatchFailureService {

    private final QueueRepository repository;
    private final IdentifierGenerator identifiers;
    private final Clock clock;
    private final int maxDeliveries;
    private final Duration retryBaseDelay;

    public HandleDispatchFailureService(QueueRepository repository,
                                        IdentifierGenerator identifiers,
                                        Clock clock,
                                        int maxDeliveries,
                                        Duration retryBaseDelay) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.identifiers = Objects.requireNonNull(identifiers, "identifiers");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (maxDeliveries < 1) {
            throw new IllegalArgumentException("maxDeliveries must be positive");
        }
        if (retryBaseDelay == null || retryBaseDelay.isNegative() || retryBaseDelay.isZero()) {
            throw new IllegalArgumentException("retryBaseDelay must be positive");
        }
        this.maxDeliveries = maxDeliveries;
        this.retryBaseDelay = retryBaseDelay;
    }

    public DispatchFailureOutcome handle(DownloadDispatchCommand command,
                                         String errorCode,
                                         String errorDetail,
                                         boolean transientFailure) {
        Objects.requireNonNull(command, "command");
        Instant now = clock.instant();
        DownloadDispatchCommand retry = command.nextAttempt();
        if (transientFailure && retry.attempt() < maxDeliveries) {
            Duration delay = exponentialDelay(command.attempt());
            repository.scheduleRetry(
                    identifiers.next(), retry, now.plus(delay), now, errorCode, errorDetail);
            return DispatchFailureOutcome.RETRY_SCHEDULED;
        }
        repository.failAndDeadLetter(
                identifiers.next(), command, now, errorCode, errorDetail);
        return DispatchFailureOutcome.DEAD_LETTERED;
    }

    private Duration exponentialDelay(int attempt) {
        int exponent = Math.min(attempt, 20);
        try {
            return retryBaseDelay.multipliedBy(1L << exponent);
        } catch (ArithmeticException exception) {
            return Duration.ofDays(1);
        }
    }
}
