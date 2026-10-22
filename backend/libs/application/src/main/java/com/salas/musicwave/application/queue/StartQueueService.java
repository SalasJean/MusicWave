package com.salas.musicwave.application.queue;

import com.salas.musicwave.application.realtime.DownloadEventPublisher;
import com.salas.musicwave.application.realtime.DownloadLifecycleEvent;
import com.salas.musicwave.domain.download.DownloadStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class StartQueueService {

    private final QueueRepository repository;
    private final Clock clock;
    private final DownloadEventPublisher eventPublisher;

    public StartQueueService(QueueRepository repository, Clock clock) {
        this(repository, clock, DownloadEventPublisher.noop());
    }

    public StartQueueService(QueueRepository repository,
                             Clock clock,
                             DownloadEventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    }

    public QueueStartResult start(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        Instant now = clock.instant();
        QueueStartResult result = repository.queuePending(ownerId, now);
        result.jobIds().forEach(jobId -> publishSafely(new DownloadLifecycleEvent(
                ownerId,
                jobId,
                DownloadStatus.QUEUED,
                BigDecimal.ZERO,
                null,
                null,
                null,
                null,
                now)));
        return result;
    }

    private void publishSafely(DownloadLifecycleEvent event) {
        try {
            eventPublisher.publish(event);
        } catch (RuntimeException ignored) {
            // Redis carries ephemeral updates; the durable queue transaction already succeeded.
        }
    }
}
