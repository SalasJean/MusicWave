package com.salas.musicwave.application.process;

import java.time.Duration;
import java.util.Objects;

public record ProcessResult(int exitCode,
                            String standardOutput,
                            String standardError,
                            boolean timedOut,
                            Duration duration) {

    public ProcessResult {
        standardOutput = Objects.requireNonNullElse(standardOutput, "");
        standardError = Objects.requireNonNullElse(standardError, "");
        Objects.requireNonNull(duration, "duration");
    }

    public boolean succeeded() {
        return !timedOut && exitCode == 0;
    }
}
