package com.salas.musicwave.infrastructure.tools;

import com.salas.musicwave.application.process.ExternalProcessRunner;
import com.salas.musicwave.application.process.ProcessRequest;
import com.salas.musicwave.application.process.ProcessResult;
import com.salas.musicwave.application.tools.MediaTool;
import com.salas.musicwave.application.tools.ToolInspection;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandToolInspectorTest {

    @Test
    void discoversBothVersionsThroughTheProcessPort() {
        List<ProcessRequest> requests = new ArrayList<>();
        ExternalProcessRunner fakeRunner = request -> {
            requests.add(request);
            if (request.command().get(0).equals("custom-yt-dlp")) {
                return result(0, "2026.08.19\n", "", false);
            }
            return result(0, "ffmpeg version 7.1.2 Copyright\nmore", "", false);
        };
        CommandToolInspector inspector = new CommandToolInspector(
                fakeRunner, "custom-yt-dlp", "custom-ffmpeg", Duration.ofSeconds(3));

        List<ToolInspection> reports = inspector.inspect();

        assertEquals(2, reports.size());
        assertEquals("2026.08.19", report(reports, MediaTool.YT_DLP).version());
        assertEquals("ffmpeg version 7.1.2 Copyright", report(reports, MediaTool.FFMPEG).version());
        assertEquals(List.of("custom-yt-dlp", "--version"), requests.get(0).command());
        assertEquals(List.of("custom-ffmpeg", "-version"), requests.get(1).command());
        assertEquals(Duration.ofSeconds(3), requests.get(0).timeout());
    }

    @Test
    void reportsTimeoutAndNonZeroExitWithoutThrowing() {
        ExternalProcessRunner fakeRunner = request -> request.command().get(0).contains("yt")
                ? result(-1, "", "", true)
                : result(127, "", "not found", false);
        CommandToolInspector inspector = new CommandToolInspector(
                fakeRunner, "yt-dlp", "ffmpeg", Duration.ofSeconds(1));

        List<ToolInspection> reports = inspector.inspect();

        assertFalse(report(reports, MediaTool.YT_DLP).available());
        assertTrue(report(reports, MediaTool.YT_DLP).detail().contains("timed out"));
        assertFalse(report(reports, MediaTool.FFMPEG).available());
        assertTrue(report(reports, MediaTool.FFMPEG).detail().contains("127"));
    }

    private ToolInspection report(List<ToolInspection> reports, MediaTool tool) {
        return reports.stream().filter(report -> report.tool() == tool).findFirst().orElseThrow();
    }

    private ProcessResult result(int exitCode, String stdout, String stderr, boolean timedOut) {
        return new ProcessResult(exitCode, stdout, stderr, timedOut, Duration.ofMillis(10));
    }
}
