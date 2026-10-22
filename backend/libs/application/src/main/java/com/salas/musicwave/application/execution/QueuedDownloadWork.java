package com.salas.musicwave.application.execution;

import java.util.Objects;
import java.util.UUID;

public record QueuedDownloadWork(UUID messageId,
                                 UUID jobId,
                                 UUID ownerId,
                                 String sourceUrl,
                                 String title,
                                 int executionAttempt,
                                 String audioQuality,
                                 String traceId,
                                 String spanId) {

    public QueuedDownloadWork(UUID messageId,
                              UUID jobId,
                              UUID ownerId,
                              String sourceUrl,
                              String title,
                              int executionAttempt) {
        this(messageId, jobId, ownerId, sourceUrl, title, executionAttempt, "192K", null, null);
    }

    public QueuedDownloadWork(UUID messageId,
                              UUID jobId,
                              UUID ownerId,
                              String sourceUrl,
                              String title,
                              int executionAttempt,
                              String traceId,
                              String spanId) {
        this(messageId, jobId, ownerId, sourceUrl, title, executionAttempt, "192K", traceId, spanId);
    }

    public QueuedDownloadWork {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(ownerId, "ownerId");
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("sourceUrl must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (executionAttempt < 1) {
            throw new IllegalArgumentException("executionAttempt must be positive");
        }
        if (audioQuality == null || !audioQuality.matches("(?:64|96|128|160|192|256|320)K")) {
            throw new IllegalArgumentException("audioQuality must be a supported MP3 quality");
        }
        if ((traceId == null) != (spanId == null)) {
            throw new IllegalArgumentException("traceId and spanId must be supplied together");
        }
        if (traceId != null && (!traceId.matches("[0-9a-fA-F]{32}")
                || !spanId.matches("[0-9a-fA-F]{16}"))) {
            throw new IllegalArgumentException("traceId or spanId has an invalid format");
        }
    }
}
