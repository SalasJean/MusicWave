package com.salas.musicwave.application.queue;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DownloadDispatchCommand(UUID messageId,
                                      UUID jobId,
                                      int attempt,
                                      Instant createdAt,
                                      String traceId,
                                      String spanId) {

    public DownloadDispatchCommand(UUID messageId,
                                   UUID jobId,
                                   int attempt,
                                   Instant createdAt) {
        this(messageId, jobId, attempt, createdAt, null, null);
    }

    public DownloadDispatchCommand {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(createdAt, "createdAt");
        if (attempt < 0) {
            throw new IllegalArgumentException("attempt must not be negative");
        }
        if ((traceId == null) != (spanId == null)) {
            throw new IllegalArgumentException("traceId and spanId must be supplied together");
        }
        if (traceId != null && (!traceId.matches("[0-9a-fA-F]{32}")
                || !spanId.matches("[0-9a-fA-F]{16}"))) {
            throw new IllegalArgumentException("traceId or spanId has an invalid format");
        }
    }

    public DownloadDispatchCommand nextAttempt() {
        return new DownloadDispatchCommand(messageId, jobId, attempt + 1, createdAt, traceId, spanId);
    }

    public DownloadDispatchCommand withTraceContext(String newTraceId, String newSpanId) {
        return new DownloadDispatchCommand(
                messageId, jobId, attempt, createdAt, newTraceId, newSpanId);
    }
}
