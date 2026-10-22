package com.salas.musicwave.infrastructure.process;

import com.salas.musicwave.application.process.ExternalProcessRunner;
import com.salas.musicwave.application.process.ProcessExecutionException;
import com.salas.musicwave.application.process.ProcessRequest;
import com.salas.musicwave.application.process.ProcessResult;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public final class JvmExternalProcessRunner implements ExternalProcessRunner {

    private static final Duration STREAM_DRAIN_TIMEOUT = Duration.ofSeconds(2);

    private final int maxCapturedCharacters;

    public JvmExternalProcessRunner(int maxCapturedCharacters) {
        if (maxCapturedCharacters < 1) {
            throw new IllegalArgumentException("maxCapturedCharacters must be positive");
        }
        this.maxCapturedCharacters = maxCapturedCharacters;
    }

    @Override
    public ProcessResult run(ProcessRequest request) {
        long startedNanos = System.nanoTime();
        Process process;
        try {
            process = new ProcessBuilder(request.command()).start();
            process.getOutputStream().close();
        } catch (IOException exception) {
            throw new ProcessExecutionException(
                    "Could not start external process: " + request.command().get(0), exception);
        }

        ExecutorService readers = Executors.newFixedThreadPool(2, daemonThreadFactory());
        Future<String> standardOutput = readers.submit(new BoundedStreamCollector(
                process.getInputStream(), maxCapturedCharacters));
        Future<String> standardError = readers.submit(new BoundedStreamCollector(
                process.getErrorStream(), maxCapturedCharacters));

        boolean timedOut = false;
        int exitCode = -1;
        try {
            if (process.waitFor(request.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
                exitCode = process.exitValue();
            } else {
                timedOut = true;
                terminateProcessTree(process);
            }

            return new ProcessResult(
                    exitCode,
                    awaitOutput(standardOutput),
                    awaitOutput(standardError),
                    timedOut,
                    Duration.ofNanos(System.nanoTime() - startedNanos));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            terminateProcessTree(process);
            throw new ProcessExecutionException("Interrupted while waiting for external process", exception);
        } finally {
            readers.shutdownNow();
        }
    }

    private String awaitOutput(Future<String> output) {
        try {
            return output.get(STREAM_DRAIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProcessExecutionException("Interrupted while collecting external process output", exception);
        } catch (ExecutionException exception) {
            throw new ProcessExecutionException("Could not collect external process output", exception.getCause());
        } catch (TimeoutException exception) {
            output.cancel(true);
            throw new ProcessExecutionException("Timed out while collecting external process output", exception);
        }
    }

    private void terminateProcessTree(Process process) {
        List<ProcessHandle> descendants = process.descendants().toList();
        descendants.forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(500, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    private ThreadFactory daemonThreadFactory() {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "musicwave-process-output-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static final class BoundedStreamCollector implements Callable<String> {

        private final InputStream stream;
        private final int limit;

        private BoundedStreamCollector(InputStream stream, int limit) {
            this.stream = stream;
            this.limit = limit;
        }

        @Override
        public String call() throws IOException {
            StringBuilder captured = new StringBuilder(Math.min(limit, 8_192));
            char[] buffer = new char[4_096];
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    int remaining = limit - captured.length();
                    if (remaining > 0) {
                        captured.append(buffer, 0, Math.min(read, remaining));
                    }
                }
            }
            return captured.toString();
        }
    }
}
