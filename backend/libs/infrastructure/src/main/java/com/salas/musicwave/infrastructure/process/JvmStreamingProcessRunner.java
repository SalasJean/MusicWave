package com.salas.musicwave.infrastructure.process;

import com.salas.musicwave.application.process.ProcessExecutionException;
import com.salas.musicwave.application.process.StreamingProcessRequest;
import com.salas.musicwave.application.process.StreamingProcessResult;
import com.salas.musicwave.application.process.StreamingProcessRunner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class JvmStreamingProcessRunner implements StreamingProcessRunner {

    private static final Duration TERMINATION_GRACE = Duration.ofMillis(500);
    private static final Duration READER_DRAIN_TIMEOUT = Duration.ofSeconds(2);

    private final int maxCapturedCharacters;
    private final Duration cancellationPollInterval;

    public JvmStreamingProcessRunner(int maxCapturedCharacters,
                                     Duration cancellationPollInterval) {
        if (maxCapturedCharacters < 1) {
            throw new IllegalArgumentException("maxCapturedCharacters must be positive");
        }
        if (cancellationPollInterval == null
                || cancellationPollInterval.isNegative()
                || cancellationPollInterval.isZero()) {
            throw new IllegalArgumentException("cancellationPollInterval must be positive");
        }
        this.maxCapturedCharacters = maxCapturedCharacters;
        this.cancellationPollInterval = cancellationPollInterval;
    }

    @Override
    public StreamingProcessResult run(StreamingProcessRequest request,
                                      Consumer<String> outputLineConsumer,
                                      BooleanSupplier cancellationRequested) {
        long startedNanos = System.nanoTime();
        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder(request.command());
            builder.redirectErrorStream(true);
            process = builder.start();
            process.getOutputStream().close();
        } catch (IOException exception) {
            throw new ProcessExecutionException(
                    "Could not start external process: " + request.command().get(0), exception);
        }

        StringBuilder captured = new StringBuilder(Math.min(maxCapturedCharacters, 8_192));
        ExecutorService readerExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "musicwave-stream-" + request.executionId());
            thread.setDaemon(true);
            return thread;
        });
        Future<?> reader = readerExecutor.submit(() -> readLines(
                process, outputLineConsumer, captured));
        boolean timedOut = false;
        boolean cancelled = false;
        try {
            long timeoutNanos = request.timeout().toNanos();
            while (process.isAlive()) {
                if (reader.isDone()) {
                    awaitReader(reader, process);
                }
                if (Thread.currentThread().isInterrupted()) {
                    terminateProcessTree(process);
                    throw interrupted();
                }
                if (cancellationRequested.getAsBoolean()) {
                    cancelled = true;
                    terminateProcessTree(process);
                    break;
                }
                if (System.nanoTime() - startedNanos >= timeoutNanos) {
                    timedOut = true;
                    terminateProcessTree(process);
                    break;
                }
                TimeUnit.MILLISECONDS.sleep(cancellationPollInterval.toMillis());
            }
            awaitReader(reader, process);
            int exitCode = process.isAlive() ? -1 : process.exitValue();
            return new StreamingProcessResult(
                    timedOut || cancelled ? -1 : exitCode,
                    captured.toString(),
                    timedOut,
                    cancelled,
                    Duration.ofNanos(System.nanoTime() - startedNanos));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            terminateProcessTree(process);
            throw new ProcessExecutionException("Interrupted while waiting for external process", exception);
        } catch (RuntimeException exception) {
            terminateProcessTree(process);
            throw exception;
        } finally {
            reader.cancel(true);
            readerExecutor.shutdownNow();
        }
    }

    private void readLines(Process process,
                           Consumer<String> outputLineConsumer,
                           StringBuilder captured) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                appendBounded(captured, line);
                outputLineConsumer.accept(line);
            }
        } catch (IOException exception) {
            if (process.isAlive()) {
                throw new ProcessExecutionException("Could not read external process output", exception);
            }
        }
    }

    private void appendBounded(StringBuilder captured, String line) {
        synchronized (captured) {
            if (!captured.isEmpty()) {
                captured.append('\n');
            }
            if (line.length() > maxCapturedCharacters) {
                captured.append(line, line.length() - maxCapturedCharacters, line.length());
            } else {
                captured.append(line);
            }
            int excess = captured.length() - maxCapturedCharacters;
            if (excess > 0) {
                captured.delete(0, excess);
            }
        }
    }

    private void awaitReader(Future<?> reader, Process process) {
        try {
            reader.get(READER_DRAIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw interrupted();
        } catch (ExecutionException exception) {
            throw new ProcessExecutionException(
                    "External process output handler failed", exception.getCause());
        } catch (TimeoutException exception) {
            terminateProcessTree(process);
            throw new ProcessExecutionException("Timed out while draining external process output", exception);
        }
    }

    private ProcessExecutionException interrupted() {
        return new ProcessExecutionException(
                "Interrupted while waiting for external process", new InterruptedException());
    }

    private void terminateProcessTree(Process process) {
        List<ProcessHandle> descendants = process.descendants().toList();
        descendants.forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            if (!process.waitFor(TERMINATION_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
                descendants.stream().filter(ProcessHandle::isAlive)
                        .forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(TERMINATION_GRACE.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            descendants.stream().filter(ProcessHandle::isAlive)
                    .forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }
}
