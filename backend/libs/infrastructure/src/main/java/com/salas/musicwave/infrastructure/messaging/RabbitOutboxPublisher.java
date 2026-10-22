package com.salas.musicwave.infrastructure.messaging;

import com.salas.musicwave.application.outbox.OutboxEvent;
import com.salas.musicwave.application.outbox.OutboxPublisher;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class RabbitOutboxPublisher implements OutboxPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitOutboxPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmTimeout;
    private final Tracer tracer;
    private final Counter published;
    private final Counter failed;

    public RabbitOutboxPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${musicwave.messaging.confirm-timeout:PT5S}") Duration confirmTimeout) {
        this(rabbitTemplate, confirmTimeout, (Tracer) null, (MeterRegistry) null);
    }

    @Autowired
    public RabbitOutboxPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${musicwave.messaging.confirm-timeout:PT5S}") Duration confirmTimeout,
            ObjectProvider<Tracer> tracerProvider,
            ObjectProvider<MeterRegistry> registryProvider) {
        this(rabbitTemplate, confirmTimeout, tracerProvider.getIfAvailable(), registryProvider.getIfAvailable());
    }

    private RabbitOutboxPublisher(RabbitTemplate rabbitTemplate,
                                  Duration confirmTimeout,
                                  Tracer tracer,
                                  MeterRegistry registry) {
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeout = confirmTimeout;
        this.tracer = tracer;
        this.published = registry == null ? null : counter(registry, "published");
        this.failed = registry == null ? null : counter(registry, "failed");
    }

    @Override
    public void publish(OutboxEvent event) {
        Span span = startSpan(event);
        try (Tracer.SpanInScope ignoredSpan = span == null ? null : tracer.withSpan(span);
             MDC.MDCCloseable ignoredJob = MDC.putCloseable("jobId", event.aggregateId().toString());
             MDC.MDCCloseable ignoredEvent = MDC.putCloseable("outboxEventId", event.id().toString())) {
            publishWithinContext(event);
            increment(published);
            LOGGER.info("Outbox event published eventType={}", event.eventType());
        } catch (RuntimeException exception) {
            increment(failed);
            if (span != null) {
                span.error(exception);
            }
            throw exception;
        } finally {
            if (span != null) {
                span.end();
            }
        }
    }

    private void publishWithinContext(OutboxEvent event) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(event.id().toString());
        properties.setHeader("musicwave-event-type", event.eventType());
        properties.setHeader("musicwave-job-id", event.aggregateId().toString());
        Message message = new Message(event.payload().getBytes(StandardCharsets.UTF_8), properties);
        CorrelationData correlation = new CorrelationData(event.id().toString());
        String exchange = DownloadMessagingTopology.DEAD_ROUTING_KEY.equals(event.routingKey())
                ? DownloadMessagingTopology.DEAD_LETTER_EXCHANGE
                : DownloadMessagingTopology.EXCHANGE;
        try {
            rabbitTemplate.send(exchange, event.routingKey(), message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new IllegalStateException("RabbitMQ rejected publication: " + confirm.getReason());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for RabbitMQ confirm", exception);
        } catch (java.util.concurrent.ExecutionException
                 | java.util.concurrent.TimeoutException exception) {
            throw new IllegalStateException("RabbitMQ publication was not confirmed", exception);
        }
    }

    private Span startSpan(OutboxEvent event) {
        if (tracer == null) {
            return null;
        }
        Span.Builder builder = tracer.spanBuilder()
                .name("musicwave.outbox.publish")
                .tag("messaging.operation", "publish")
                .tag("musicwave.event.type", event.eventType())
                .tag("musicwave.job.id", event.aggregateId().toString());
        if (event.traceId() != null) {
            TraceContext parent = tracer.traceContextBuilder()
                    .traceId(event.traceId())
                    .spanId(event.spanId())
                    .sampled(true)
                    .build();
            builder.setParent(parent);
        } else {
            builder.setNoParent();
        }
        return builder.start();
    }

    private Counter counter(MeterRegistry registry, String outcome) {
        return Counter.builder("musicwave.outbox.publications")
                .description("Outbox broker publication outcomes")
                .tag("outcome", outcome)
                .register(registry);
    }

    private void increment(Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }
}
