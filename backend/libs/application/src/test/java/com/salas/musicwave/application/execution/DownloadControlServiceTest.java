package com.salas.musicwave.application.execution;

import com.salas.musicwave.application.realtime.DownloadLifecycleEvent;
import com.salas.musicwave.domain.download.DownloadStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DownloadControlServiceTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID JOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");

    @Test
    void publishesImmediateCancellationAndRetryButWaitsForWorkerOnActiveCancellation() {
        RecordingRepository repository = new RecordingRepository();
        List<DownloadLifecycleEvent> events = new ArrayList<>();
        DownloadControlService service = new DownloadControlService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC), events::add);

        repository.cancelResult = new DownloadControlResult(
                JOB_ID, DownloadStatus.CANCELLED, true);
        service.cancel(OWNER_ID, JOB_ID);

        repository.cancelResult = new DownloadControlResult(
                JOB_ID, DownloadStatus.DOWNLOADING, true);
        service.cancel(OWNER_ID, JOB_ID);

        service.retry(OWNER_ID, JOB_ID);

        assertEquals(
                List.of(DownloadStatus.CANCELLED, DownloadStatus.PENDING),
                events.stream().map(DownloadLifecycleEvent::status).toList());
    }

    @Test
    void publishesEveryImmediateQueueCancellation() {
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000012");
        RecordingRepository repository = new RecordingRepository();
        repository.queueResult = new QueueCancellationResult(
                2,
                1,
                List.of(
                        new DownloadControlResult(JOB_ID, DownloadStatus.CANCELLED, true),
                        new DownloadControlResult(second, DownloadStatus.CANCELLED, true),
                        new DownloadControlResult(
                                UUID.randomUUID(), DownloadStatus.CONVERTING, true)));
        List<DownloadLifecycleEvent> events = new ArrayList<>();
        DownloadControlService service = new DownloadControlService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC), events::add);

        QueueCancellationResult result = service.cancelActive(OWNER_ID);

        assertEquals(2, result.cancelledCount());
        assertEquals(List.of(JOB_ID, second),
                events.stream().map(DownloadLifecycleEvent::jobId).toList());
    }

    private static final class RecordingRepository implements DownloadControlRepository {

        private DownloadControlResult cancelResult = new DownloadControlResult(
                JOB_ID, DownloadStatus.CANCELLED, true);
        private QueueCancellationResult queueResult = new QueueCancellationResult(0, 0);
        private QueueClearResult clearResult = new QueueClearResult(0, 0, 0);

        @Override
        public Optional<DownloadControlResult> cancel(UUID ownerId, UUID jobId, Instant now) {
            return Optional.of(cancelResult);
        }

        @Override
        public Optional<DownloadControlResult> retryFailed(
                UUID ownerId, UUID jobId, Instant now) {
            return Optional.of(new DownloadControlResult(jobId, DownloadStatus.PENDING, false));
        }

        @Override
        public QueueCancellationResult cancelActive(UUID ownerId, Instant now) {
            return queueResult;
        }

        @Override
        public QueueClearResult clearCompleted(UUID ownerId, Instant now) {
            return clearResult;
        }

        @Override
        public QueueClearResult clearAll(UUID ownerId, Instant now) {
            return clearResult;
        }
    }
}
