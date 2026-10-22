package com.salas.musicwave.api.tools;

import com.salas.musicwave.application.tools.MediaTool;
import com.salas.musicwave.application.tools.ToolInspection;
import com.salas.musicwave.application.tools.ToolStatusQueryService;
import com.salas.musicwave.application.tools.WorkerToolInventory;
import com.salas.musicwave.application.tools.WorkerToolSnapshot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ToolStatusControllerTest {

    @Test
    void returnsTheReadyWorkerToolInventory() throws Exception {
        Instant now = Instant.parse("2026-10-09T12:00:00Z");
        WorkerToolSnapshot snapshot = new WorkerToolSnapshot(
                "worker-1",
                now.minusSeconds(60),
                now,
                List.of(
                        ToolInspection.available(MediaTool.YT_DLP, "yt-dlp", "2026.08.19"),
                        ToolInspection.available(MediaTool.FFMPEG, "ffmpeg", "ffmpeg version 7.1.2")));
        WorkerToolInventory inventory = new WorkerToolInventory() {
            @Override
            public void save(WorkerToolSnapshot ignored) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<WorkerToolSnapshot> findSeenSince(Instant cutoff) {
                return List.of(snapshot);
            }
        };
        ToolStatusQueryService service = new ToolStatusQueryService(
                inventory, Clock.fixed(now, ZoneOffset.UTC), Duration.ofSeconds(30));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ToolStatusController(service)).build();

        mvc.perform(get("/api/v1/tools/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.activeWorkers").value(1))
                .andExpect(jsonPath("$.readyWorkers").value(1))
                .andExpect(jsonPath("$.workers[0].workerId").value("worker-1"))
                .andExpect(jsonPath("$.workers[0].tools[0].name").value("ytDlp"))
                .andExpect(jsonPath("$.workers[0].tools[0].version").value("2026.08.19"));
    }
}
