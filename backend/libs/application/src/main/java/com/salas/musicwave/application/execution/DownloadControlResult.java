package com.salas.musicwave.application.execution;

import com.salas.musicwave.domain.download.DownloadStatus;

import java.util.Objects;
import java.util.UUID;

public record DownloadControlResult(UUID jobId,
                                    DownloadStatus status,
                                    boolean cancellationRequested) {

    public DownloadControlResult {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(status, "status");
    }
}
