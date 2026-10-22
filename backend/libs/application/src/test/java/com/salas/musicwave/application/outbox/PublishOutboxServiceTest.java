package com.salas.musicwave.application.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PublishOutboxServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

    @Test
    void marksConfirmedEventsPublished() {
        RecordingRepository repository = new RecordingRepository(event(0));
        PublishOutboxService service = service(repository, ignored -> { });

        OutboxPublishResult result = service.publishAvailable("api-1", 20);

        assertEquals(new OutboxPublishResult(1, 1, 0), result);
        assertEquals(List.of(repository.events.get(0).id()), repository.published);
        assertEquals(Duration.ofSeconds(30), repository.lease);
    }

    @Test
    void releasesFailedEventsWithCappedExponentialDelay() {
        RecordingRepository repository = new RecordingRepository(event(5));
        PublishOutboxService service = service(repository, ignored -> {
            throw new IllegalStateException("broker unavailable");
        });

        OutboxPublishResult result = service.publishAvailable("api-1", 20);

        assertEquals(new OutboxPublishResult(1, 0, 1), result);
        assertEquals(NOW.plusSeconds(20), repository.nextAttemptAt);
        assertEquals("broker unavailable", repository.failureDetail);
    }

    private PublishOutboxService service(RecordingRepository repository, OutboxPublisher publisher) {
        return new PublishOutboxService(
                repository,
                publisher,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(30),
                Duration.ofSeconds(1),
                Duration.ofSeconds(20));
    }

    private OutboxEvent event(int attempts) {
        return new OutboxEvent(
                UUID.fromString("00000000-0000-0000-0000-000000000010"),
                UUID.fromString("00000000-0000-0000-0000-000000000011"),
                "DOWNLOAD_DISPATCH_REQUESTED",
                "download.execute",
                "{}",
                attempts);
    }

    private static final class RecordingRepository implements OutboxRepository {

        private final List<OutboxEvent> events;
        private final List<UUID> published = new ArrayList<>();
        private Duration lease;
        private Instant nextAttemptAt;
        private String failureDetail;

        private RecordingRepository(OutboxEvent event) {
            this.events = List.of(event);
        }

        @Override
        public List<OutboxEvent> claimAvailable(
                String publisherId, Instant now, Duration leaseDuration, int limit) {
            this.lease = leaseDuration;
            return events;
        }

        @Override
        public void markPublished(UUID eventId, String publisherId, Instant publishedAt) {
            published.add(eventId);
        }

        @Override
        public void recordFailure(
                UUID eventId, String publisherId, Instant nextAttemptAt, String detail) {
            this.nextAttemptAt = nextAttemptAt;
            this.failureDetail = detail;
        }
    }
}
