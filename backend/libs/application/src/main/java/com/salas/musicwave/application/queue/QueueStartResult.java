package com.salas.musicwave.application.queue;

import java.util.List;
import java.util.UUID;

public record QueueStartResult(int queuedCount, List<UUID> jobIds) {

    public QueueStartResult {
        jobIds = List.copyOf(jobIds);
        if (queuedCount != jobIds.size()) {
            throw new IllegalArgumentException("queuedCount must match jobIds size");
        }
    }
}
