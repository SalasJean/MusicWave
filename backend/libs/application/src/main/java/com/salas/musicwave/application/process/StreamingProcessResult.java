package com.salas.musicwave.application.process;

import java.time.Duration;
import java.util.Objects;

public record StreamingProcessResult(int exitCode,
                                     String capturedOutput,
                                     boolean timedOut,
                                     boolean cancelled,
                                     Duration duration) {

    public StreamingProcessResult {
        capturedOutput = Objects.requireNonNullElse(capturedOutput, "");
        Objects.requireNonNull(duration, "duration");
    }

    public boolean succeeded() {
        return exitCode == 0 && !timedOut && !cancelled;
    }
}
