package com.salas.musicwave.infrastructure.process;

import com.salas.musicwave.application.process.ProcessRequest;
import com.salas.musicwave.application.process.ProcessResult;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JvmExternalProcessRunnerTest {

    private final JvmExternalProcessRunner runner = new JvmExternalProcessRunner(1_024);

    @Test
    void executesAnArgumentVectorAndCapturesOutput() {
        ProcessResult result = runner.run(ProcessRequest.of(
                Duration.ofSeconds(2), "/usr/bin/printf", "%s", "hello world"));

        assertTrue(result.succeeded());
        assertEquals("hello world", result.standardOutput());
        assertEquals("", result.standardError());
        assertFalse(result.timedOut());
    }

    @Test
    void terminatesAProcessAtItsDeadline() {
        ProcessResult result = runner.run(ProcessRequest.of(
                Duration.ofMillis(100), "/usr/bin/sleep", "2"));

        assertTrue(result.timedOut());
        assertEquals(-1, result.exitCode());
        assertTrue(result.duration().compareTo(Duration.ofSeconds(2)) < 0);
    }
}
