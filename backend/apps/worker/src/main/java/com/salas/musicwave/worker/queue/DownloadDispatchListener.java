package com.salas.musicwave.worker.queue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.salas.musicwave.application.queue.AcceptDispatchService;
import com.salas.musicwave.application.queue.DeliveryAcceptance;
import com.salas.musicwave.application.queue.DownloadDispatchCommand;
import com.salas.musicwave.application.queue.HandleDispatchFailureService;
import com.salas.musicwave.infrastructure.messaging.DownloadMessagingTopology;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
public class DownloadDispatchListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadDispatchListener.class);

    private final ObjectMapper objectMapper;
    private final AcceptDispatchService acceptService;
    private final HandleDispatchFailureService failureService;
    private final WorkerReadiness readiness;
    private final String workerId;
    private final Tracer tracer;

    public DownloadDispatchListener(
            ObjectMapper objectMapper,
            AcceptDispatchService acceptService,
            HandleDispatchFailureService failureService,
            WorkerReadiness readiness,
            @Value("${musicwave.worker.id:${HOSTNAME:local-worker}}") String workerId) {
        this(objectMapper, acceptService, failureService, readiness, workerId, (Tracer) null);
    }

    @Autowired
    public DownloadDispatchListener(
            ObjectMapper objectMapper,
            AcceptDispatchService acceptService,
            HandleDispatchFailureService failureService,
            WorkerReadiness readiness,
            @Value("${musicwave.worker.id:${HOSTNAME:local-worker}}") String workerId,
            ObjectProvider<Tracer> tracerProvider) {
        this(objectMapper, acceptService, failureService, readiness, workerId,
                tracerProvider.getIfAvailable());
    }

    private DownloadDispatchListener(
            ObjectMapper objectMapper,
            AcceptDispatchService acceptService,
            HandleDispatchFailureService failureService,
            WorkerReadiness readiness,
            String workerId,
            Tracer tracer) {
        this.objectMapper = objectMapper;
        this.acceptService = acceptService;
        this.failureService = failureService;
        this.readiness = readiness;
        this.workerId = workerId;
        this.tracer = tracer;
    }

    @RabbitListener(queues = DownloadMessagingTopology.EXECUTION_QUEUE, ackMode = "MANUAL")
    public void receive(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        final DownloadDispatchCommand command;
        try {
            command = withConsumerTrace(parse(message));
        } catch (RuntimeException exception) {
            LOGGER.warn("Rejecting malformed download dispatch message messageId={}",
                    message.getMessageProperties().getMessageId());
            channel.basicReject(deliveryTag, false);
            return;
        }

        try (MDC.MDCCloseable ignoredJob = MDC.putCloseable("jobId", command.jobId().toString());
             MDC.MDCCloseable ignoredMessage = MDC.putCloseable(
                     "messageId", command.messageId().toString())) {
            if (!readiness.isReady()) {
                failureService.handle(
                        command,
                        "WORKER_TOOLS_UNAVAILABLE",
                        "The worker has not reported both yt-dlp and FFmpeg as available",
                        true);
                channel.basicAck(deliveryTag, false);
                return;
            }

            DeliveryAcceptance acceptance = acceptService.accept(command, workerId);
            if (acceptance == DeliveryAcceptance.NOT_FOUND) {
                failureService.handle(
                        command,
                        "DOWNLOAD_NOT_FOUND",
                        "The queued download no longer exists",
                        false);
            }
            channel.basicAck(deliveryTag, false);
            LOGGER.info("Download dispatch accepted outcome={}", acceptance);
        } catch (DataAccessException exception) {
            LOGGER.warn("Database unavailable while accepting download dispatch");
            channel.basicNack(deliveryTag, false, true);
        } catch (RuntimeException exception) {
            LOGGER.error("Unexpected failure while accepting download dispatch", exception);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private DownloadDispatchCommand parse(Message message) {
        try {
            JsonNode root = objectMapper.readTree(message.getBody());
            UUID messageId = UUID.fromString(requiredText(root, "messageId"));
            UUID jobId = UUID.fromString(requiredText(root, "jobId"));
            JsonNode attemptNode = root.get("attempt");
            if (attemptNode == null || !attemptNode.canConvertToInt()) {
                throw new IllegalArgumentException("attempt is required");
            }
            Instant createdAt = Instant.parse(requiredText(root, "createdAt"));
            String traceId = optionalText(root, "traceId");
            String spanId = optionalText(root, "spanId");
            return new DownloadDispatchCommand(
                    messageId, jobId, attemptNode.intValue(), createdAt, traceId, spanId);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Message JSON is malformed", exception);
        }
    }

    private String requiredText(JsonNode root, String name) {
        JsonNode value = root.get(name);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.textValue();
    }

    private String optionalText(JsonNode root, String name) {
        JsonNode value = root.get(name);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException(name + " must be a non-blank string");
        }
        return value.textValue();
    }

    private DownloadDispatchCommand withConsumerTrace(DownloadDispatchCommand command) {
        if (tracer == null) {
            return command;
        }
        Span current = tracer.currentSpan();
        if (current == null) {
            return command;
        }
        return command.withTraceContext(
                current.context().traceId(), current.context().spanId());
    }
}
