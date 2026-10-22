package com.salas.musicwave.application.tools;

import java.time.Instant;
import java.util.List;

public record ToolStatusReport(ToolFleetStatus status,
                               Instant generatedAt,
                               List<WorkerToolSnapshot> workers) {

    public ToolStatusReport {
        workers = List.copyOf(workers);
    }

    public int activeWorkers() {
        return workers.size();
    }

    public int readyWorkers() {
        return (int) workers.stream().filter(WorkerToolSnapshot::ready).count();
    }
}
