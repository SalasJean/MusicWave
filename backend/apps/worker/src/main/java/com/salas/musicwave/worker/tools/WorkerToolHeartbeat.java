package com.salas.musicwave.worker.tools;

import com.salas.musicwave.application.tools.ToolInspection;
import com.salas.musicwave.application.tools.ToolInspector;
import com.salas.musicwave.application.tools.WorkerToolInventory;
import com.salas.musicwave.application.tools.WorkerToolSnapshot;
import com.salas.musicwave.worker.queue.WorkerReadiness;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WorkerToolHeartbeat {

    private final ToolInspector toolInspector;
    private final WorkerToolInventory inventory;
    private final Clock clock;
    private final String workerId;
    private final WorkerReadiness readiness;
    private final Instant startedAt;

    public WorkerToolHeartbeat(ToolInspector toolInspector,
                               WorkerToolInventory inventory,
                               Clock clock,
                               @Value("${musicwave.worker.id:${HOSTNAME:local-worker}}") String workerId,
                               WorkerReadiness readiness) {
        this.toolInspector = toolInspector;
        this.inventory = inventory;
        this.clock = clock;
        this.workerId = requireWorkerId(workerId);
        this.readiness = readiness;
        this.startedAt = clock.instant();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        publish();
    }

    @Scheduled(
            initialDelayString = "${musicwave.tools.heartbeat-interval:PT10S}",
            fixedDelayString = "${musicwave.tools.heartbeat-interval:PT10S}")
    public void publish() {
        List<ToolInspection> tools = toolInspector.inspect();
        WorkerToolSnapshot snapshot = new WorkerToolSnapshot(
                workerId, startedAt, clock.instant(), tools);
        inventory.save(snapshot);
        readiness.update(snapshot.ready());
    }

    private String requireWorkerId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("musicwave.worker.id must not be blank");
        }
        if (value.length() > 255) {
            throw new IllegalArgumentException("musicwave.worker.id must not exceed 255 characters");
        }
        return value;
    }
}
