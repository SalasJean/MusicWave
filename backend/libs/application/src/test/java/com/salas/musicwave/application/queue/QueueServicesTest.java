package com.salas.musicwave.application.queue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.ArrayList;

import com.salas.musicwave.application.realtime.DownloadLifecycleEvent;
import com.salas.musicwave.domain.download.DownloadStatus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueueServicesTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID JOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");

    @Test
    void startsOnlyThroughTheOwnerScopedRepositoryOperation() {
        RecordingRepository repository = new RecordingRepository();
        repository.startResult = new QueueStartResult(1, List.of(JOB_ID));
        List<DownloadLifecycleEvent> events = new ArrayList<>();
        StartQueueService service = new StartQueueService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC), events::add);

        QueueStartResult result = service.start(OWNER_ID);

        assertEquals(1, result.queuedCount());
        assertEquals(OWNER_ID, repository.ownerId);
        assertEquals(NOW, repository.now);
        assertEquals(1, events.size());
        assertEquals(DownloadStatus.QUEUED, events.get(0).status());
        assertEquals(JOB_ID, events.get(0).jobId());
    }

    @Test
    void realtimeFailureDoesNotRollbackQueueing() {
        RecordingRepository repository = new RecordingRepository();
        repository.startResult = new QueueStartResult(1, List.of(JOB_ID));
        StartQueueService service = new StartQueueService(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                event -> {
                    throw new IllegalStateException("Redis unavailable");
                });

        QueueStartResult result = service.start(OWNER_ID);

        assertEquals(1, result.queuedCount());
        assertEquals(NOW, repository.now);
    }

    @Test
    void acceptsDeliveryWithTheWorkerIdentityAndCurrentTime() {
        RecordingRepository repository = new RecordingRepository();
        repository.acceptance = DeliveryAcceptance.ACCEPTED;
        AcceptDispatchService service = new AcceptDispatchService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));
        DownloadDispatchCommand command = command(0);

        DeliveryAcceptance result = service.accept(command, "worker-1");

        assertEquals(DeliveryAcceptance.ACCEPTED, result);
        assertEquals(command, repository.command);
        assertEquals("worker-1", repository.workerId);
        assertEquals(NOW, repository.now);
    }

    @Test
    void schedulesExponentialRetriesAndDeadLettersTheLastDelivery() {
        RecordingRepository repository = new RecordingRepository();
        AtomicLong ids = new AtomicLong(100);
        HandleDispatchFailureService service = new HandleDispatchFailureService(
                repository,
                () -> new UUID(0, ids.getAndIncrement()),
                Clock.fixed(NOW, ZoneOffset.UTC),
                3,
                Duration.ofSeconds(5));

        assertEquals(
                DispatchFailureOutcome.RETRY_SCHEDULED,
                service.handle(command(1), "TOOLS_UNAVAILABLE", "not ready", true));
        assertEquals(2, repository.command.attempt());
        assertEquals(NOW.plusSeconds(10), repository.availableAt);

        assertEquals(
                DispatchFailureOutcome.DEAD_LETTERED,
                service.handle(command(2), "TOOLS_UNAVAILABLE", "not ready", true));
        assertEquals("dead", repository.operation);
        assertEquals("TOOLS_UNAVAILABLE", repository.errorCode);
    }

    private DownloadDispatchCommand command(int attempt) {
        return new DownloadDispatchCommand(MESSAGE_ID, JOB_ID, attempt, NOW.minusSeconds(30));
    }

    private static final class RecordingRepository implements QueueRepository {

        private QueueStartResult startResult = new QueueStartResult(0, List.of());
        private DeliveryAcceptance acceptance = DeliveryAcceptance.IGNORED;
        private UUID ownerId;
        private Instant now;
        private DownloadDispatchCommand command;
        private String workerId;
        private Instant availableAt;
        private String operation;
        private String errorCode;

        @Override
        public QueueStartResult queuePending(UUID ownerId, Instant now) {
            this.ownerId = ownerId;
            this.now = now;
            return startResult;
        }

        @Override
        public DeliveryAcceptance acceptDelivery(
                DownloadDispatchCommand command, String workerId, Instant now) {
            this.command = command;
            this.workerId = workerId;
            this.now = now;
            return acceptance;
        }

        @Override
        public void scheduleRetry(UUID outboxId,
                                  DownloadDispatchCommand command,
                                  Instant availableAt,
                                  Instant now,
                                  String errorCode,
                                  String errorDetail) {
            this.operation = "retry";
            this.command = command;
            this.availableAt = availableAt;
            this.now = now;
            this.errorCode = errorCode;
        }

        @Override
        public void failAndDeadLetter(UUID outboxId,
                                      DownloadDispatchCommand command,
                                      Instant now,
                                      String errorCode,
                                      String errorDetail) {
            this.operation = "dead";
            this.command = command;
            this.now = now;
            this.errorCode = errorCode;
        }
    }
}
