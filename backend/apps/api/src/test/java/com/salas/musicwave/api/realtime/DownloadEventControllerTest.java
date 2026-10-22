package com.salas.musicwave.api.realtime;

import com.salas.musicwave.api.download.LocalOwnerProvider;
import com.salas.musicwave.api.error.ApiExceptionHandler;
import com.salas.musicwave.application.realtime.DownloadEventStream;
import com.salas.musicwave.application.realtime.DownloadLifecycleEvent;
import com.salas.musicwave.application.realtime.StreamCursor;
import com.salas.musicwave.application.realtime.StreamReadBatch;
import com.salas.musicwave.application.realtime.StreamedDownloadEvent;
import com.salas.musicwave.domain.download.DownloadStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DownloadEventControllerTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_OWNER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OWNED_JOB = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID OTHER_JOB = UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");

    private DownloadEventSseService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.close();
        }
    }

    @Test
    void replaysAfterLastEventIdInOrderAndFiltersOtherOwners() throws Exception {
        FakeStream stream = new FakeStream(
                StreamCursor.parse("100-0"),
                StreamCursor.parse("101-0"),
                new StreamReadBatch(
                        StreamCursor.parse("101-0"),
                        List.of(
                                streamed("100-1", OWNER_ID, OWNED_JOB, DownloadStatus.DOWNLOADING),
                                streamed("101-0", OTHER_OWNER, OTHER_JOB, DownloadStatus.FAILED))));
        MockMvc mvc = mvc(stream);

        MvcResult pending = mvc.perform(get("/api/v1/events")
                        .header("Last-Event-ID", "100-0"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id:100-1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(OWNED_JOB.toString())))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(OTHER_JOB.toString()))));
    }

    @Test
    void emitsStreamResetWhenRequestedHistoryWasTrimmed() throws Exception {
        FakeStream stream = new FakeStream(
                StreamCursor.parse("100-0"),
                StreamCursor.parse("200-0"),
                new StreamReadBatch(StreamCursor.parse("200-0"), List.of()));
        MockMvc mvc = mvc(stream);

        MvcResult pending = mvc.perform(get("/api/v1/events")
                        .header("Last-Event-ID", "1-0"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:stream-reset")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("EVENT_HISTORY_EXPIRED")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("200-0")));
    }

    @Test
    void rejectsMalformedLastEventIdAsRfc9457() throws Exception {
        MockMvc mvc = mvc(new FakeStream(
                StreamCursor.ZERO,
                StreamCursor.ZERO,
                new StreamReadBatch(StreamCursor.ZERO, List.of())));

        mvc.perform(get("/api/v1/events").header("Last-Event-ID", "latest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EVENT_CURSOR"));
    }

    @Test
    void boundsConnectionsAndReleasesTheSlotDuringShutdown() {
        DownloadEventStream blocking = new DownloadEventStream() {
            @Override
            public Optional<StreamCursor> oldestEventId() {
                return Optional.empty();
            }

            @Override
            public Optional<StreamCursor> latestEventId() {
                return Optional.empty();
            }

            @Override
            public StreamReadBatch readAfter(
                    StreamCursor cursor, Duration blockTimeout, int limit) {
                try {
                    Thread.sleep(30_000);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                return new StreamReadBatch(cursor, List.of());
            }
        };
        service = new DownloadEventSseService(
                blocking,
                Duration.ofSeconds(1),
                Duration.ofHours(1),
                Duration.ofMinutes(1),
                10,
                1);

        service.subscribe(OWNER_ID, Optional.empty());

        assertEquals(0, service.availableConnectionSlots());
        assertThrows(RealtimeCapacityException.class,
                () -> service.subscribe(OWNER_ID, Optional.empty()));
        service.close();
        assertEquals(1, service.availableConnectionSlots());
        service = null;
    }

    private MockMvc mvc(DownloadEventStream stream) {
        service = new DownloadEventSseService(
                stream,
                Duration.ofMillis(10),
                Duration.ofHours(1),
                Duration.ofSeconds(5),
                10,
                2);
        DownloadEventController controller = new DownloadEventController(
                service, new LocalOwnerProvider(OWNER_ID, "local-user"));
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler(
                        Clock.fixed(NOW, ZoneOffset.UTC)))
                .build();
    }

    private StreamedDownloadEvent streamed(String id,
                                            UUID ownerId,
                                            UUID jobId,
                                            DownloadStatus status) {
        return new StreamedDownloadEvent(
                StreamCursor.parse(id),
                new DownloadLifecycleEvent(
                        ownerId,
                        jobId,
                        status,
                        new BigDecimal("25"),
                        "1MiB/s",
                        "00:10",
                        "song",
                        null,
                        NOW));
    }

    private static final class FakeStream implements DownloadEventStream {

        private final StreamCursor oldest;
        private final StreamCursor latest;
        private final StreamReadBatch batch;
        private final AtomicInteger reads = new AtomicInteger();

        private FakeStream(StreamCursor oldest,
                           StreamCursor latest,
                           StreamReadBatch batch) {
            this.oldest = oldest;
            this.latest = latest;
            this.batch = batch;
        }

        @Override
        public Optional<StreamCursor> oldestEventId() {
            return oldest.equals(StreamCursor.ZERO) ? Optional.empty() : Optional.of(oldest);
        }

        @Override
        public Optional<StreamCursor> latestEventId() {
            return latest.equals(StreamCursor.ZERO) ? Optional.empty() : Optional.of(latest);
        }

        @Override
        public StreamReadBatch readAfter(StreamCursor cursor,
                                         Duration blockTimeout,
                                         int limit) {
            if (reads.incrementAndGet() == 1) {
                return batch;
            }
            Thread.currentThread().interrupt();
            return new StreamReadBatch(cursor, List.of());
        }
    }
}
