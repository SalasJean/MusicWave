package com.salas.musicwave.worker.download;

import com.salas.musicwave.application.execution.ClaimDownloadWorkService;
import com.salas.musicwave.application.execution.ExecuteDownloadService;
import com.salas.musicwave.application.execution.QueuedDownloadWork;
import com.salas.musicwave.worker.queue.WorkerReadiness;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DownloadExecutionDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadExecutionDispatcher.class);

    private final ClaimDownloadWorkService claimService;
    private final ExecuteDownloadService executeService;
    private final WorkerReadiness readiness;
    private final String workerId;
    private final int maxConcurrency;
    private final ExecutorService executor;
    private final Map<UUID, QueuedDownloadWork> active = new ConcurrentHashMap<>();
    private final Tracer tracer;
    private final Counter claimed;
    private final Counter finished;
    private final Counter aborted;

    public DownloadExecutionDispatcher(
            ClaimDownloadWorkService claimService,
            ExecuteDownloadService executeService,
            WorkerReadiness readiness,
            @Value("${musicwave.worker.id:${HOSTNAME:local-worker}}") String workerId,
            @Value("${musicwave.download.max-concurrency:10}") int maxConcurrency,
            ObjectProvider<Tracer> tracerProvider,
            ObjectProvider<MeterRegistry> registryProvider) {
        this.claimService = claimService;
        this.executeService = executeService;
        this.readiness = readiness;
        this.workerId = workerId;
        if (maxConcurrency < 1 || maxConcurrency > 20) {
            throw new IllegalArgumentException("max-concurrency must be between 1 and 20");
        }
        this.maxConcurrency = maxConcurrency;
        this.executor = Executors.newFixedThreadPool(maxConcurrency, threadFactory());
        this.tracer = tracerProvider.getIfAvailable();
        MeterRegistry registry = registryProvider.getIfAvailable();
        this.claimed = counter(registry, "claimed");
        this.finished = counter(registry, "finished");
        this.aborted = counter(registry, "aborted");
        if (registry != null) {
            Gauge.builder("musicwave.worker.executions.active", active, Map::size)
                    .description("Downloads currently executing in this worker")
                    .register(registry);
            Gauge.builder("musicwave.worker.executions.capacity", () -> maxConcurrency)
                    .description("Maximum concurrent downloads configured in this worker")
                    .register(registry);
        }
    }

    @Scheduled(
            initialDelayString = "${musicwave.download.dispatch-initial-delay:PT2S}",
            fixedDelayString = "${musicwave.download.dispatch-interval:PT1S}")
    public void dispatch() {
        if (!readiness.isReady()) {
            return;
        }
        int capacity = maxConcurrency - active.size();
        if (capacity < 1) {
            return;
        }
        List<QueuedDownloadWork> claimedWork = claimService.claim(workerId, capacity);
        for (QueuedDownloadWork work : claimedWork) {
            if (active.putIfAbsent(work.jobId(), work) == null) {
                increment(claimed);
                executor.submit(() -> execute(work));
            }
        }
    }

    @Scheduled(
            initialDelayString = "${musicwave.download.lease-renew-interval:PT10S}",
            fixedDelayString = "${musicwave.download.lease-renew-interval:PT10S}")
    public void renewLeases() {
        claimService.renew(workerId, new ArrayList<>(active.keySet()));
    }

    private void execute(QueuedDownloadWork work) {
        Span span = startSpan(work);
        try (Tracer.SpanInScope ignoredSpan = span == null ? null : tracer.withSpan(span);
             MDC.MDCCloseable ignoredJob = MDC.putCloseable("jobId", work.jobId().toString());
             MDC.MDCCloseable ignoredMessage = MDC.putCloseable(
                     "messageId", work.messageId().toString())) {
            LOGGER.info("Download execution started attempt={}", work.executionAttempt());
            executeService.execute(work, workerId);
            increment(finished);
            LOGGER.info("Download execution finished attempt={}", work.executionAttempt());
        } catch (RuntimeException exception) {
            increment(aborted);
            if (span != null) {
                span.error(exception);
            }
            LOGGER.error("Download execution aborted; its lease will permit recovery", exception);
        } finally {
            active.remove(work.jobId());
            if (span != null) {
                span.end();
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    int activeCount() {
        return active.size();
    }

    private Span startSpan(QueuedDownloadWork work) {
        if (tracer == null) {
            return null;
        }
        Span.Builder builder = tracer.spanBuilder()
                .name("musicwave.download.execute")
                .tag("musicwave.job.id", work.jobId().toString())
                .tag("musicwave.execution.attempt", work.executionAttempt());
        if (work.traceId() != null) {
            TraceContext parent = tracer.traceContextBuilder()
                    .traceId(work.traceId())
                    .spanId(work.spanId())
                    .sampled(true)
                    .build();
            builder.setParent(parent);
        } else {
            builder.setNoParent();
        }
        return builder.start();
    }

    private Counter counter(MeterRegistry registry, String outcome) {
        return registry == null ? null : Counter.builder("musicwave.worker.executions")
                .description("Worker download execution outcomes")
                .tag("outcome", outcome)
                .register(registry);
    }

    private void increment(Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }

    private ThreadFactory threadFactory() {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(
                    runnable, "musicwave-download-" + sequence.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
    }
}
