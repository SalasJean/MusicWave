package com.salas.musicwave.application.process;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record StreamingProcessRequest(String executionId,
                                      List<String> command,
                                      Duration timeout) {

    public StreamingProcessRequest {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("executionId must not be blank");
        }
        command = List.copyOf(Objects.requireNonNull(command, "command"));
        if (command.isEmpty() || command.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("command must contain only non-blank values");
        }
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }
}
