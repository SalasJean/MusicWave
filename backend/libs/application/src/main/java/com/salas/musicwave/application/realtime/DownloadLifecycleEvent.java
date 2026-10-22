package com.salas.musicwave.application.realtime;

import com.salas.musicwave.domain.download.DownloadStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DownloadLifecycleEvent(UUID ownerId,
                                     UUID jobId,
                                     DownloadStatus status,
                                     BigDecimal progress,
                                     String speed,
                                     String eta,
                                     String title,
                                     String errorCode,
                                     Instant occurredAt) {

    public DownloadLifecycleEvent {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (progress.compareTo(BigDecimal.ZERO) < 0
                || progress.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("progress must be between 0 and 100");
        }
        speed = normalize(speed);
        eta = normalize(eta);
        title = normalize(title);
        errorCode = normalize(errorCode);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
