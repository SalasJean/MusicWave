package com.salas.musicwave.application.tools;

import java.util.Objects;

public record ToolInspection(MediaTool tool,
                             boolean available,
                             String executable,
                             String version,
                             String detail) {

    public ToolInspection {
        Objects.requireNonNull(tool, "tool");
        executable = Objects.requireNonNullElse(executable, "");
        version = Objects.requireNonNullElse(version, "");
        detail = Objects.requireNonNullElse(detail, "");
    }

    public static ToolInspection available(MediaTool tool, String executable, String version) {
        return new ToolInspection(tool, true, executable, version, "");
    }

    public static ToolInspection unavailable(MediaTool tool, String executable, String detail) {
        return new ToolInspection(tool, false, executable, "", detail);
    }
}
