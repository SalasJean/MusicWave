package com.salas.musicwave.application.tools;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolStatusQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");

    @Test
    void reportsReadyAndQueriesOnlyFreshWorkers() {
        RecordingInventory inventory = new RecordingInventory(List.of(snapshot(true, true)));
        ToolStatusQueryService service = new ToolStatusQueryService(
                inventory,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(30));

        ToolStatusReport report = service.query();

        assertEquals(ToolFleetStatus.READY, report.status());
        assertEquals(1, report.activeWorkers());
        assertEquals(1, report.readyWorkers());
        assertEquals(NOW.minusSeconds(30), inventory.cutoff);
    }

    @Test
    void reportsDegradedWhenOnlySomeActiveWorkersAreReady() {
        RecordingInventory inventory = new RecordingInventory(List.of(
                snapshot(true, true),
                snapshot(true, false)));
        ToolStatusQueryService service = new ToolStatusQueryService(
                inventory,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(30));

        assertEquals(ToolFleetStatus.DEGRADED, service.query().status());
    }

    @Test
    void reportsUnavailableWithoutAReadyWorker() {
        RecordingInventory inventory = new RecordingInventory(List.of(snapshot(false, false)));
        ToolStatusQueryService service = new ToolStatusQueryService(
                inventory,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(30));

        assertEquals(ToolFleetStatus.UNAVAILABLE, service.query().status());
    }

    private WorkerToolSnapshot snapshot(boolean ytDlp, boolean ffmpeg) {
        return new WorkerToolSnapshot(
                "worker-" + ytDlp + "-" + ffmpeg,
                NOW.minusSeconds(60),
                NOW,
                List.of(
                        inspection(MediaTool.YT_DLP, ytDlp),
                        inspection(MediaTool.FFMPEG, ffmpeg)));
    }

    private ToolInspection inspection(MediaTool tool, boolean available) {
        return available
                ? ToolInspection.available(tool, tool.apiName(), "1.0")
                : ToolInspection.unavailable(tool, tool.apiName(), "missing");
    }

    private static final class RecordingInventory implements WorkerToolInventory {

        private final List<WorkerToolSnapshot> snapshots;
        private Instant cutoff;

        private RecordingInventory(List<WorkerToolSnapshot> snapshots) {
            this.snapshots = snapshots;
        }

        @Override
        public void save(WorkerToolSnapshot snapshot) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<WorkerToolSnapshot> findSeenSince(Instant cutoff) {
            this.cutoff = cutoff;
            return snapshots;
        }
    }
}
