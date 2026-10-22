package com.salas.musicwave.application.tools;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record WorkerToolSnapshot(String workerId,
                                 Instant startedAt,
                                 Instant lastSeenAt,
                                 List<ToolInspection> tools) {

    public WorkerToolSnapshot {
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId must not be blank");
        }
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt");
        tools = List.copyOf(Objects.requireNonNull(tools, "tools"));

        Set<MediaTool> reported = tools.stream()
                .map(ToolInspection::tool)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(MediaTool.class)));
        if (!reported.equals(EnumSet.allOf(MediaTool.class)) || reported.size() != tools.size()) {
            throw new IllegalArgumentException("exactly one report per media tool is required");
        }
    }

    public boolean ready() {
        return tools.stream().allMatch(ToolInspection::available);
    }
}
