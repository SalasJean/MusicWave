package com.salas.musicwave.application.process;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record ProcessRequest(List<String> command, Duration timeout) {

    public ProcessRequest {
        command = List.copyOf(Objects.requireNonNull(command, "command"));
        if (command.isEmpty() || command.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("command must contain only non-blank values");
        }
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    public static ProcessRequest of(Duration timeout, String... command) {
        return new ProcessRequest(List.of(command), timeout);
    }
}
