package com.salas.musicwave.domain.download;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DownloadJob(UUID id,
                          UUID ownerId,
                          UUID requestId,
                          int requestIndex,
                          String sourceUrl,
                          String title,
                          TitleStatus titleStatus,
                          DownloadStatus status,
                          BigDecimal progress,
                          String speed,
                          String eta,
                          String errorCode,
                          String errorMessage,
                          int retryCount,
                          long version,
                          Instant createdAt,
                          Instant updatedAt,
                          Instant startedAt,
                          Instant completedAt) {

    public DownloadJob {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(requestId, "requestId");
        if (requestIndex < 0) {
            throw new IllegalArgumentException("requestIndex must not be negative");
        }
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("sourceUrl must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        Objects.requireNonNull(titleStatus, "titleStatus");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(progress, "progress");
        if (progress.signum() < 0 || progress.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("progress must be between 0 and 100");
        }
        if (retryCount < 0 || version < 0) {
            throw new IllegalArgumentException("retryCount and version must not be negative");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static DownloadJob pending(UUID id,
                                      UUID ownerId,
                                      UUID requestId,
                                      int requestIndex,
                                      SourceUrl sourceUrl,
                                      Instant now) {
        return new DownloadJob(
                id,
                ownerId,
                requestId,
                requestIndex,
                sourceUrl.value(),
                sourceUrl.fallbackTitle(),
                sourceUrl.youtube() ? TitleStatus.PENDING : TitleStatus.NOT_APPLICABLE,
                DownloadStatus.PENDING,
                BigDecimal.ZERO.setScale(2),
                null,
                null,
                null,
                null,
                0,
                0,
                now,
                now,
                null,
                null);
    }
}
