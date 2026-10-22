package com.salas.musicwave.infrastructure.messaging;

import com.salas.musicwave.application.outbox.OutboxEvent;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitOutboxPublisherTest {

    @Test
    void publishesSuccessfullyWhenOptionalTelemetryIsAbsent() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(), any(CorrelationData.class));
        RabbitOutboxPublisher publisher = new RabbitOutboxPublisher(
                rabbitTemplate, Duration.ofSeconds(1));
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "download.queued",
                DownloadMessagingTopology.EXECUTE_ROUTING_KEY,
                "{}",
                0);

        publisher.publish(event);

        verify(rabbitTemplate).send(
                anyString(), anyString(), any(), any(CorrelationData.class));
    }
}
