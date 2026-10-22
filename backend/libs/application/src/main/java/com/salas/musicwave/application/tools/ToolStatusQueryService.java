package com.salas.musicwave.application.tools;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class ToolStatusQueryService {

    private final WorkerToolInventory inventory;
    private final Clock clock;
    private final Duration heartbeatMaxAge;

    public ToolStatusQueryService(WorkerToolInventory inventory, Clock clock, Duration heartbeatMaxAge) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.heartbeatMaxAge = Objects.requireNonNull(heartbeatMaxAge, "heartbeatMaxAge");
        if (heartbeatMaxAge.isZero() || heartbeatMaxAge.isNegative()) {
            throw new IllegalArgumentException("heartbeatMaxAge must be positive");
        }
    }

    public ToolStatusReport query() {
        Instant now = clock.instant();
        List<WorkerToolSnapshot> workers = inventory.findSeenSince(now.minus(heartbeatMaxAge));
        long readyWorkers = workers.stream().filter(WorkerToolSnapshot::ready).count();

        ToolFleetStatus status;
        if (workers.isEmpty() || readyWorkers == 0) {
            status = ToolFleetStatus.UNAVAILABLE;
        } else if (readyWorkers == workers.size()) {
            status = ToolFleetStatus.READY;
        } else {
            status = ToolFleetStatus.DEGRADED;
        }
        return new ToolStatusReport(status, now, workers);
    }
}
