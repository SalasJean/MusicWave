package com.salas.musicwave.application.execution;

import java.util.List;

public record QueueClearResult(int clearedCount,
                               int cancelledCount,
                               int cancellationRequestedCount,
                               List<DownloadControlResult> transitions) {

    public QueueClearResult(int clearedCount, int cancelledCount, int cancellationRequestedCount) {
        this(clearedCount, cancelledCount, cancellationRequestedCount, List.of());
    }

    public QueueClearResult {
        if (clearedCount < 0 || cancelledCount < 0 || cancellationRequestedCount < 0) {
            throw new IllegalArgumentException("queue clear counts must not be negative");
        }
        transitions = List.copyOf(transitions);
    }
}
