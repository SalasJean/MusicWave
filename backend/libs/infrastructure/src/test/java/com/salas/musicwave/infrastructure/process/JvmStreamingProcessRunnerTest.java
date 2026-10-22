package com.salas.musicwave.infrastructure.process;

import com.salas.musicwave.application.process.StreamingProcessRequest;
import com.salas.musicwave.application.process.StreamingProcessResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JvmStreamingProcessRunnerTest {

    private final JvmStreamingProcessRunner runner = new JvmStreamingProcessRunner(
            12, Duration.ofMillis(20));

    @Test
    void streamsLinesAndBoundsCapturedDiagnostics() {
        List<String> lines = new ArrayList<>();

        StreamingProcessResult result = runner.run(
                new StreamingProcessRequest(
                        "stream-test",
                        List.of("/usr/bin/printf", "first line\nsecond line\n"),
                        Duration.ofSeconds(2)),
                lines::add,
                () -> false);

        assertTrue(result.succeeded());
        assertEquals(List.of("first line", "second line"), lines);
        assertTrue(result.capturedOutput().length() <= 12);
        assertTrue(result.capturedOutput().endsWith("second line"));
    }

    @Test
    void terminatesWhenCancellationIsRequested() {
        StreamingProcessResult result = runner.run(
                new StreamingProcessRequest(
                        "cancel-test",
                        List.of("/usr/bin/sleep", "5"),
                        Duration.ofSeconds(10)),
                ignored -> { },
                () -> true);

        assertTrue(result.cancelled());
        assertFalse(result.timedOut());
        assertTrue(result.duration().compareTo(Duration.ofSeconds(2)) < 0);
    }

    @Test
    void terminatesAtTheHardDeadline() {
        StreamingProcessResult result = runner.run(
                new StreamingProcessRequest(
                        "timeout-test",
                        List.of("/usr/bin/sleep", "5"),
                        Duration.ofMillis(80)),
                ignored -> { },
                () -> false);

        assertTrue(result.timedOut());
        assertFalse(result.cancelled());
        assertTrue(result.duration().compareTo(Duration.ofSeconds(2)) < 0);
    }

    @Test
    void cancellationTerminatesTheCompleteProcessTree() throws Exception {
        AtomicLong childPid = new AtomicLong();

        StreamingProcessResult result = new JvmStreamingProcessRunner(
                1_024, Duration.ofMillis(20)).run(
                new StreamingProcessRequest(
                        "tree-cancel-test",
                        List.of("/bin/sh", "-c", "sleep 30 & echo $!; wait"),
                        Duration.ofSeconds(10)),
                line -> childPid.compareAndSet(0, Long.parseLong(line.trim())),
                () -> childPid.get() > 0);

        assertTrue(result.cancelled());
        assertTrue(childPid.get() > 0);
        Thread.sleep(50);
        assertTrue(ProcessHandle.of(childPid.get()).map(handle -> !handle.isAlive()).orElse(true));
    }
}
