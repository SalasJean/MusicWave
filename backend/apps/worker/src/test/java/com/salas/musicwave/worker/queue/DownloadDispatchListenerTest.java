package com.salas.musicwave.worker.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.salas.musicwave.application.queue.AcceptDispatchService;
import com.salas.musicwave.application.queue.DeliveryAcceptance;
import com.salas.musicwave.application.queue.DispatchFailureOutcome;
import com.salas.musicwave.application.queue.DownloadDispatchCommand;
import com.salas.musicwave.application.queue.HandleDispatchFailureService;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DownloadDispatchListenerTest {

    private AcceptDispatchService acceptService;
    private HandleDispatchFailureService failureService;
    private WorkerReadiness readiness;
    private Channel channel;
    private DownloadDispatchListener listener;

    @BeforeEach
    void setUp() {
        acceptService = mock(AcceptDispatchService.class);
        failureService = mock(HandleDispatchFailureService.class);
        readiness = new WorkerReadiness();
        channel = mock(Channel.class);
        listener = new DownloadDispatchListener(
                new ObjectMapper(), acceptService, failureService, readiness, "worker-test");
    }

    @Test
    void acknowledgesOnlyAfterTheDurableAcceptanceReturns() throws Exception {
        readiness.update(true);
        when(acceptService.accept(any(DownloadDispatchCommand.class), eq("worker-test")))
                .thenReturn(DeliveryAcceptance.ACCEPTED);

        listener.receive(message(validJson()), channel);

        ArgumentCaptor<DownloadDispatchCommand> command =
                ArgumentCaptor.forClass(DownloadDispatchCommand.class);
        verify(acceptService).accept(command.capture(), eq("worker-test"));
        assertEquals(0, command.getValue().attempt());
        verify(channel).basicAck(42L, false);
    }

    @Test
    void schedulesABoundedRetryWhenToolsAreUnavailable() throws Exception {
        when(failureService.handle(any(), anyString(), anyString(), eq(true)))
                .thenReturn(DispatchFailureOutcome.RETRY_SCHEDULED);

        listener.receive(message(validJson()), channel);

        verify(failureService).handle(
                any(), eq("WORKER_TOOLS_UNAVAILABLE"), anyString(), eq(true));
        verify(acceptService, never()).accept(any(), anyString());
        verify(channel).basicAck(42L, false);
    }

    @Test
    void rejectsMalformedMessagesWithoutRequeueSoRabbitRoutesThemToDlq() throws Exception {
        readiness.update(true);

        listener.receive(message("{\"messageId\":\"not-a-uuid\"}"), channel);

        verify(channel).basicReject(42L, false);
        verify(acceptService, never()).accept(any(), anyString());
    }

    @Test
    void retainsPersistedTraceContextWhenNoConsumerTracerIsAvailable() throws Exception {
        readiness.update(true);
        when(acceptService.accept(any(DownloadDispatchCommand.class), eq("worker-test")))
                .thenReturn(DeliveryAcceptance.ACCEPTED);
        String json = validJson().replace(
                "\"createdAt\":\"2026-10-10T12:00:00Z\"",
                "\"createdAt\":\"2026-10-10T12:00:00Z\",\n"
                        + "  \"traceId\":\"0123456789abcdef0123456789abcdef\",\n"
                        + "  \"spanId\":\"0123456789abcdef\"");

        listener.receive(message(json), channel);

        ArgumentCaptor<DownloadDispatchCommand> command =
                ArgumentCaptor.forClass(DownloadDispatchCommand.class);
        verify(acceptService).accept(command.capture(), eq("worker-test"));
        assertEquals("0123456789abcdef0123456789abcdef", command.getValue().traceId());
        assertEquals("0123456789abcdef", command.getValue().spanId());
    }

    private Message message(String json) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(42L);
        return new Message(json.getBytes(StandardCharsets.UTF_8), properties);
    }

    private String validJson() {
        return """
                {
                  "messageId":"00000000-0000-0000-0000-000000000010",
                  "jobId":"00000000-0000-0000-0000-000000000011",
                  "attempt":0,
                  "createdAt":"2026-10-10T12:00:00Z"
                }
                """;
    }
}
