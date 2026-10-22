package com.salas.musicwave.application.download;

import com.salas.musicwave.domain.download.DownloadJob;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DownloadJobRepository {

    PersistedDownloadBatch create(NewDownloadBatch batch);

    DownloadJobPage findByOwner(UUID ownerId, int page, int size);

    Optional<DownloadJob> findByIdAndOwner(UUID jobId, UUID ownerId);

    List<PendingTitleResolution> findPendingTitleResolutions(Instant now, int limit);

    void markTitleResolved(UUID jobId, String title, Instant now);

    void recordTitleFailure(UUID jobId,
                            String detail,
                            boolean terminal,
                            Instant now,
                            Duration retryDelay);
}
