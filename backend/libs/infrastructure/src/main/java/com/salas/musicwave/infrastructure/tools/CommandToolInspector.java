package com.salas.musicwave.infrastructure.tools;

import com.salas.musicwave.application.process.ExternalProcessRunner;
import com.salas.musicwave.application.process.ProcessExecutionException;
import com.salas.musicwave.application.process.ProcessRequest;
import com.salas.musicwave.application.process.ProcessResult;
import com.salas.musicwave.application.tools.MediaTool;
import com.salas.musicwave.application.tools.ToolInspection;
import com.salas.musicwave.application.tools.ToolInspector;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public final class CommandToolInspector implements ToolInspector {

    private static final int MAX_DETAIL_LENGTH = 500;

    private final ExternalProcessRunner processRunner;
    private final String ytDlpExecutable;
    private final String ffmpegExecutable;
    private final Duration timeout;

    public CommandToolInspector(ExternalProcessRunner processRunner,
                                String ytDlpExecutable,
                                String ffmpegExecutable,
                                Duration timeout) {
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
        this.ytDlpExecutable = requireExecutable(ytDlpExecutable, "ytDlpExecutable");
        this.ffmpegExecutable = requireExecutable(ffmpegExecutable, "ffmpegExecutable");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    @Override
    public List<ToolInspection> inspect() {
        return List.of(
                inspect(MediaTool.YT_DLP, ytDlpExecutable, "--version"),
                inspect(MediaTool.FFMPEG, ffmpegExecutable, "-version"));
    }

    private ToolInspection inspect(MediaTool tool, String executable, String versionArgument) {
        try {
            ProcessResult result = processRunner.run(ProcessRequest.of(timeout, executable, versionArgument));
            if (result.timedOut()) {
                return ToolInspection.unavailable(tool, executable, "Version command timed out");
            }
            if (!result.succeeded()) {
                String output = firstNonBlank(result.standardError(), result.standardOutput());
                return ToolInspection.unavailable(
                        tool,
                        executable,
                        limited("Version command exited with code " + result.exitCode() + appendOutput(output)));
            }
            String version = firstNonBlank(result.standardOutput(), result.standardError());
            if (version.isBlank()) {
                return ToolInspection.unavailable(tool, executable, "Version command returned no version");
            }
            return ToolInspection.available(tool, executable, limited(version));
        } catch (ProcessExecutionException exception) {
            return ToolInspection.unavailable(tool, executable, limited(exception.getMessage()));
        }
    }

    private String firstNonBlank(String preferred, String fallback) {
        String value = preferred == null || preferred.isBlank() ? fallback : preferred;
        if (value == null) {
            return "";
        }
        return value.lines().filter(line -> !line.isBlank()).findFirst().orElse("").trim();
    }

    private String appendOutput(String output) {
        return output.isBlank() ? "" : ": " + output;
    }

    private String limited(String value) {
        String safe = Objects.requireNonNullElse(value, "Unknown process error");
        return safe.length() <= MAX_DETAIL_LENGTH ? safe : safe.substring(0, MAX_DETAIL_LENGTH);
    }

    private String requireExecutable(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
