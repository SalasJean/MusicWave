package com.salas.musicwave.api.realtime;

import com.salas.musicwave.application.realtime.DownloadEventStream;
import com.salas.musicwave.application.realtime.DownloadLifecycleEvent;
import com.salas.musicwave.application.realtime.StreamCursor;
import com.salas.musicwave.application.realtime.StreamReadBatch;
import com.salas.musicwave.application.realtime.StreamedDownloadEvent;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public final class DownloadEventSseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadEventSseService.class);

    private final DownloadEventStream eventStream;
    private final Duration readBlock;
    private final Duration heartbeatInterval;
    private final int readBatchSize;
    private final long emitterTimeoutMillis;
    private final Semaphore connectionSlots;
    private final ThreadPoolExecutor executor;

    public DownloadEventSseService(DownloadEventStream eventStream,
                                   Duration readBlock,
                                   Duration heartbeatInterval,
                                   Duration emitterTimeout,
                                   int readBatchSize,
                                   int maxConnections) {
        this.eventStream = Objects.requireNonNull(eventStream, "eventStream");
        this.readBlock = positive(readBlock, "readBlock");
        this.heartbeatInterval = positive(heartbeatInterval, "heartbeatInterval");
        this.emitterTimeoutMillis = positive(emitterTimeout, "emitterTimeout").toMillis();
        if (readBatchSize < 1 || maxConnections < 1) {
            throw new IllegalArgumentException("Realtime limits must be positive");
        }
        this.readBatchSize = readBatchSize;
        this.connectionSlots = new Semaphore(maxConnections);
        this.executor = new ThreadPoolExecutor(
                maxConnections,
                maxConnections,
                30,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                new SseThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
        this.executor.allowCoreThreadTimeOut(true);
    }

    public SseEmitter subscribe(UUID ownerId, Optional<StreamCursor> requestedCursor) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(requestedCursor, "requestedCursor");
        if (!connectionSlots.tryAcquire()) {
            throw new RealtimeCapacityException();
        }

        SseEmitter emitter = new SseEmitter(emitterTimeoutMillis);
        AtomicBoolean closed = new AtomicBoolean();
        Runnable release = () -> {
            if (closed.compareAndSet(false, true)) {
                connectionSlots.release();
            }
        };
        emitter.onCompletion(release);
        emitter.onError(error -> release.run());
        emitter.onTimeout(release);
        try {
            executor.execute(() -> stream(ownerId, requestedCursor, emitter, closed, release));
        } catch (RuntimeException exception) {
            release.run();
            throw new RealtimeCapacityException();
        }
        return emitter;
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                LOGGER.warn("Realtime SSE executor did not terminate within the shutdown window");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    int availableConnectionSlots() {
        return connectionSlots.availablePermits();
    }

    private void stream(UUID ownerId,
                        Optional<StreamCursor> requestedCursor,
                        SseEmitter emitter,
                        AtomicBoolean closed,
                        Runnable release) {
        try {
            StreamCursor cursor = initialCursor(requestedCursor, emitter);
            long lastHeartbeat = System.nanoTime();
            while (!closed.get() && !Thread.currentThread().isInterrupted()) {
                StreamReadBatch batch = eventStream.readAfter(cursor, readBlock, readBatchSize);
                cursor = batch.lastSeen();
                for (StreamedDownloadEvent streamed : batch.events()) {
                    if (streamed.event().ownerId().equals(ownerId)) {
                        sendDownload(emitter, streamed);
                    }
                }
                long now = System.nanoTime();
                if (Duration.ofNanos(now - lastHeartbeat).compareTo(heartbeatInterval) >= 0) {
                    emitter.send(SseEmitter.event().comment("keepalive"));
                    lastHeartbeat = now;
                }
            }
        } catch (IOException exception) {
            LOGGER.debug("SSE client disconnected ownerId={}", ownerId);
            release.run();
        } catch (RuntimeException exception) {
            if (!closed.get()) {
                LOGGER.warn("Realtime stream ended unexpectedly ownerId={}", ownerId, exception);
                try {
                    emitter.completeWithError(exception);
                } catch (IllegalStateException completionRace) {
                    LOGGER.debug("SSE error completion raced with container cleanup ownerId={}", ownerId);
                }
            }
            release.run();
        } finally {
            if (!closed.get()) {
                try {
                    emitter.complete();
                } catch (IllegalStateException completionRace) {
                    LOGGER.debug("SSE completion raced with container cleanup ownerId={}", ownerId);
                }
            }
            release.run();
        }
    }

    private StreamCursor initialCursor(Optional<StreamCursor> requestedCursor,
                                       SseEmitter emitter) throws IOException {
        Optional<StreamCursor> latest = eventStream.latestEventId();
        if (requestedCursor.isEmpty()) {
            return latest.orElse(StreamCursor.ZERO);
        }

        StreamCursor requested = requestedCursor.get();
        Optional<StreamCursor> oldest = eventStream.oldestEventId();
        boolean historyExpired = oldest.isPresent() && requested.compareTo(oldest.get()) < 0;
        boolean cursorAhead = latest.isPresent() && requested.compareTo(latest.get()) > 0;
        if (historyExpired || cursorAhead) {
            StreamCursor resumeAt = latest.orElse(StreamCursor.ZERO);
            String reason = historyExpired ? "EVENT_HISTORY_EXPIRED" : "EVENT_CURSOR_AHEAD";
            emitter.send(SseEmitter.event()
                    .id(resumeAt.toString())
                    .name("stream-reset")
                    .data(new StreamResetResponse(
                            reason,
                            "Refresh GET /api/v1/downloads before applying new events",
                            resumeAt.toString())));
            return resumeAt;
        }
        return requested;
    }

    private void sendDownload(SseEmitter emitter,
                              StreamedDownloadEvent streamed) throws IOException {
        DownloadLifecycleEvent event = streamed.event();
        emitter.send(SseEmitter.event()
                .id(streamed.id().toString())
                .name("download")
                .data(new DownloadEventResponse(
                        streamed.id().toString(),
                        event.jobId(),
                        event.status().name(),
                        event.progress(),
                        event.speed(),
                        event.eta(),
                        event.title(),
                        event.errorCode(),
                        event.occurredAt())));
    }

    private Duration positive(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    public record DownloadEventResponse(String eventId,
                                        UUID jobId,
                                        String status,
                                        BigDecimal progress,
                                        String speed,
                                        String eta,
                                        String title,
                                        String errorCode,
                                        Instant timestamp) {
    }

    public record StreamResetResponse(String code, String detail, String resumeAt) {
    }

    private static final class SseThreadFactory implements ThreadFactory {

        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "musicwave-sse-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
