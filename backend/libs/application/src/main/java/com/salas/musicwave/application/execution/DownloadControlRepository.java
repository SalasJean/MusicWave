package com.salas.musicwave.application.execution;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface DownloadControlRepository {

    Optional<DownloadControlResult> cancel(UUID ownerId, UUID jobId, Instant now);

    Optional<DownloadControlResult> retryFailed(UUID ownerId, UUID jobId, Instant now);

    QueueCancellationResult cancelActive(UUID ownerId, Instant now);

    QueueClearResult clearCompleted(UUID ownerId, Instant now);

    QueueClearResult clearAll(UUID ownerId, Instant now);
}
