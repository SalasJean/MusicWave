package com.salas.musicwave.worker.download;

import com.salas.musicwave.application.execution.ClaimDownloadWorkService;
import com.salas.musicwave.application.execution.ExecuteDownloadService;
import com.salas.musicwave.application.execution.QueuedDownloadWork;
import com.salas.musicwave.worker.queue.WorkerReadiness;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DownloadExecutionDispatcherTest {

    @Test
    void saturatesButNeverExceedsConfiguredWorkerCapacity() throws Exception {
        ClaimDownloadWorkService claims = mock(ClaimDownloadWorkService.class);
        ExecuteDownloadService execution = mock(ExecuteDownloadService.class);
        WorkerReadiness readiness = mock(WorkerReadiness.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<Tracer> tracers = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<MeterRegistry> registries = mock(ObjectProvider.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CountDownLatch started = new CountDownLatch(10);
        CountDownLatch release = new CountDownLatch(1);
        List<QueuedDownloadWork> work = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> new QueuedDownloadWork(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        "https://example.com/" + index, "job-" + index, 1))
                .toList();
        when(readiness.isReady()).thenReturn(true);
        when(claims.claim("worker-load", 10)).thenReturn(work);
        when(registries.getIfAvailable()).thenReturn(registry);
        doAnswer(invocation -> {
            started.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return null;
        }).when(execution).execute(any(), eq("worker-load"));
        DownloadExecutionDispatcher dispatcher = new DownloadExecutionDispatcher(
                claims, execution, readiness, "worker-load", 10, tracers, registries);
        try {
            dispatcher.dispatch();
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertEquals(10, dispatcher.activeCount());
            assertEquals(10.0, registry.get("musicwave.worker.executions.active").gauge().value());

            dispatcher.dispatch();
            verify(claims).claim("worker-load", 10);
            assertEquals(10, dispatcher.activeCount());
        } finally {
            release.countDown();
            dispatcher.shutdown();
        }
    }
}
