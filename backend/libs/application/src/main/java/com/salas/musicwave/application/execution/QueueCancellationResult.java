package com.salas.musicwave.application.execution;

import java.util.List;

public record QueueCancellationResult(int cancelledCount,
                                      int cancellationRequestedCount,
                                      List<DownloadControlResult> transitions) {

    public QueueCancellationResult(int cancelledCount, int cancellationRequestedCount) {
        this(cancelledCount, cancellationRequestedCount, List.of());
    }

    public QueueCancellationResult {
        if (cancelledCount < 0 || cancellationRequestedCount < 0) {
            throw new IllegalArgumentException("cancellation counts must not be negative");
        }
        transitions = List.copyOf(transitions);
    }
}
