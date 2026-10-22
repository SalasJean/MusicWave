package com.salas.musicwave.worker.tools;

import com.salas.musicwave.application.tools.MediaTool;
import com.salas.musicwave.application.tools.ToolInspection;
import com.salas.musicwave.application.tools.WorkerToolInventory;
import com.salas.musicwave.application.tools.WorkerToolSnapshot;
import com.salas.musicwave.worker.queue.WorkerReadiness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerToolHeartbeatTest {

    @Test
    void savesOneSnapshotWithBothInspectedTools() {
        Instant now = Instant.parse("2026-10-09T12:00:00Z");
        RecordingInventory inventory = new RecordingInventory();
        WorkerReadiness readiness = new WorkerReadiness();
        WorkerToolHeartbeat heartbeat = new WorkerToolHeartbeat(
                () -> List.of(
                        ToolInspection.available(MediaTool.YT_DLP, "yt-dlp", "2026.08.19"),
                        ToolInspection.available(MediaTool.FFMPEG, "ffmpeg", "7.1.2")),
                inventory,
                Clock.fixed(now, ZoneOffset.UTC),
                "worker-test",
                readiness);

        heartbeat.publish();

        assertEquals("worker-test", inventory.saved.workerId());
        assertEquals(now, inventory.saved.startedAt());
        assertEquals(now, inventory.saved.lastSeenAt());
        assertTrue(inventory.saved.ready());
        assertTrue(readiness.isReady());
    }

    private static final class RecordingInventory implements WorkerToolInventory {

        private WorkerToolSnapshot saved;

        @Override
        public void save(WorkerToolSnapshot snapshot) {
            saved = snapshot;
        }

        @Override
        public List<WorkerToolSnapshot> findSeenSince(Instant cutoff) {
            throw new UnsupportedOperationException();
        }
    }
}
