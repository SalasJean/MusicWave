package com.salas.musicwave.application.execution;

import com.salas.musicwave.application.download.DownloadNotFoundException;
import com.salas.musicwave.application.realtime.DownloadEventPublisher;
import com.salas.musicwave.application.realtime.DownloadLifecycleEvent;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class DownloadControlService {

    private final DownloadControlRepository repository;
    private final Clock clock;
    private final DownloadEventPublisher eventPublisher;

    public DownloadControlService(DownloadControlRepository repository, Clock clock) {
        this(repository, clock, DownloadEventPublisher.noop());
    }

    public DownloadControlService(DownloadControlRepository repository,
                                  Clock clock,
                                  DownloadEventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    }

    public DownloadControlResult cancel(UUID ownerId, UUID jobId) {
        Instant now = clock.instant();
        DownloadControlResult result = repository.cancel(ownerId, jobId, now)
                .orElseThrow(() -> new DownloadNotFoundException(jobId));
        publishTerminalCancellation(ownerId, result, now);
        return result;
    }

    public DownloadControlResult retry(UUID ownerId, UUID jobId) {
        Instant now = clock.instant();
        DownloadControlResult result = repository.retryFailed(ownerId, jobId, now)
                .orElseThrow(() -> new DownloadNotFoundException(jobId));
        publishSafely(event(ownerId, result, now));
        return result;
    }

    public QueueCancellationResult cancelActive(UUID ownerId) {
        Instant now = clock.instant();
        QueueCancellationResult result = repository.cancelActive(ownerId, now);
        result.transitions().forEach(transition ->
                publishTerminalCancellation(ownerId, transition, now));
        return result;
    }

    public QueueClearResult clearCompleted(UUID ownerId) {
        return repository.clearCompleted(ownerId, clock.instant());
    }

    public QueueClearResult clearAll(UUID ownerId) {
        Instant now = clock.instant();
        QueueClearResult result = repository.clearAll(ownerId, now);
        result.transitions().forEach(transition ->
                publishTerminalCancellation(ownerId, transition, now));
        return result;
    }

    private void publishTerminalCancellation(UUID ownerId,
                                             DownloadControlResult result,
                                             Instant now) {
        if (result.status().isTerminal()) {
            publishSafely(event(ownerId, result, now));
        }
    }

    private DownloadLifecycleEvent event(UUID ownerId,
                                         DownloadControlResult result,
                                         Instant now) {
        return new DownloadLifecycleEvent(
                ownerId,
                result.jobId(),
                result.status(),
                BigDecimal.ZERO,
                null,
                null,
                null,
                null,
                now);
    }

    private void publishSafely(DownloadLifecycleEvent event) {
        try {
            eventPublisher.publish(event);
        } catch (RuntimeException ignored) {
            // PostgreSQL remains authoritative when the ephemeral event transport is unavailable.
        }
    }
}
