package com.salas.musicwave.infrastructure.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salas.musicwave.application.queue.DeliveryAcceptance;
import com.salas.musicwave.application.queue.DownloadDispatchCommand;
import com.salas.musicwave.application.queue.QueueRepository;
import com.salas.musicwave.application.queue.QueueStartResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcQueueRepository implements QueueRepository {

    public static final String EXECUTE_ROUTING_KEY = "download.execute";
    public static final String DEAD_ROUTING_KEY = "download.execute.dead";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;

    public JdbcQueueRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this(jdbcTemplate, objectMapper, (Tracer) null);
    }

    @Autowired
    public JdbcQueueRepository(JdbcTemplate jdbcTemplate,
                               ObjectMapper objectMapper,
                               ObjectProvider<Tracer> tracerProvider) {
        this(jdbcTemplate, objectMapper, tracerProvider.getIfAvailable());
    }

    private JdbcQueueRepository(JdbcTemplate jdbcTemplate,
                                ObjectMapper objectMapper,
                                Tracer tracer) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.tracer = tracer;
    }

    @Override
    @Transactional
    public QueueStartResult queuePending(UUID ownerId, Instant now) {
        List<PendingJob> pendingJobs = jdbcTemplate.query(
                """
                SELECT id, retry_count
                FROM download_job
                WHERE owner_id = ? AND visible AND status = 'PENDING'
                ORDER BY created_at, id
                FOR UPDATE
                """,
                (resultSet, rowNumber) -> new PendingJob(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getInt("retry_count")),
                ownerId);

        for (PendingJob pendingJob : pendingJobs) {
            UUID jobId = pendingJob.id();
            int updated = jdbcTemplate.update(
                    """
                    UPDATE download_job
                    SET status = 'QUEUED', updated_at = ?, version = version + 1
                    WHERE id = ? AND owner_id = ? AND status = 'PENDING'
                    """,
                    Timestamp.from(now), jobId, ownerId);
            if (updated != 1) {
                throw new IllegalStateException("Pending job changed while queue start held its lock");
            }
            UUID messageId = UUID.randomUUID();
            TraceData trace = currentTrace();
            DownloadDispatchCommand command = new DownloadDispatchCommand(
                    messageId, jobId, 0, now, trace.traceId(), trace.spanId());
            insertOutbox(
                    messageId,
                    jobId,
                    "DOWNLOAD_DISPATCH_REQUESTED",
                    EXECUTE_ROUTING_KEY,
                    payload(command),
                    now,
                    now,
                    "dispatch:" + jobId + ":" + pendingJob.retryCount(),
                    trace);
        }
        List<UUID> jobIds = pendingJobs.stream().map(PendingJob::id).toList();
        return new QueueStartResult(jobIds.size(), jobIds);
    }

    @Override
    @Transactional
    public DeliveryAcceptance acceptDelivery(DownloadDispatchCommand command,
                                             String workerId,
                                             Instant now) {
        Integer duplicate = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM worker_inbox WHERE message_id = ? OR job_id = ?",
                Integer.class,
                command.messageId(),
                command.jobId());
        if (duplicate != null && duplicate > 0) {
            return DeliveryAcceptance.DUPLICATE;
        }

        List<String> statuses = jdbcTemplate.query(
                "SELECT status FROM download_job WHERE id = ? FOR UPDATE",
                (resultSet, rowNumber) -> resultSet.getString("status"),
                command.jobId());
        if (statuses.isEmpty()) {
            return DeliveryAcceptance.NOT_FOUND;
        }
        if (!"QUEUED".equals(statuses.get(0))) {
            return DeliveryAcceptance.IGNORED;
        }

        jdbcTemplate.update(
                """
                INSERT INTO worker_inbox (
                    message_id, job_id, delivery_attempt, first_received_by, received_at,
                    trace_id, span_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                command.messageId(),
                command.jobId(),
                command.attempt(),
                workerId,
                Timestamp.from(now),
                command.traceId(),
                command.spanId());
        jdbcTemplate.update(
                """
                UPDATE download_job
                SET dispatched_at = ?, dispatch_attempts = GREATEST(dispatch_attempts, ?),
                    updated_at = ?, version = version + 1
                WHERE id = ?
                """,
                Timestamp.from(now),
                command.attempt() + 1,
                Timestamp.from(now),
                command.jobId());
        return DeliveryAcceptance.ACCEPTED;
    }

    @Override
    @Transactional
    public void scheduleRetry(UUID outboxId,
                              DownloadDispatchCommand command,
                              Instant availableAt,
                              Instant now,
                              String errorCode,
                              String errorDetail) {
        insertOutbox(
                outboxId,
                command.jobId(),
                "DOWNLOAD_DISPATCH_RETRY",
                EXECUTE_ROUTING_KEY,
                payload(command),
                now,
                availableAt,
                "retry:" + command.messageId() + ":" + command.attempt(),
                new TraceData(command.traceId(), command.spanId()));
        jdbcTemplate.update(
                """
                UPDATE download_job
                SET dispatch_attempts = GREATEST(dispatch_attempts, ?), updated_at = ?,
                    error_code = ?, error_message = ?, version = version + 1
                WHERE id = ? AND status = 'QUEUED'
                """,
                command.attempt(),
                Timestamp.from(now),
                safe(errorCode, 64),
                safe(errorDetail, 1_000),
                command.jobId());
    }

    @Override
    @Transactional
    public void failAndDeadLetter(UUID outboxId,
                                  DownloadDispatchCommand command,
                                  Instant now,
                                  String errorCode,
                                  String errorDetail) {
        String safeCode = safe(errorCode, 64);
        String safeDetail = safe(errorDetail, 1_000);
        jdbcTemplate.update(
                """
                UPDATE download_job
                SET status = 'FAILED', dispatch_attempts = GREATEST(dispatch_attempts, ?),
                    error_code = ?, error_message = ?, completed_at = ?, updated_at = ?,
                    version = version + 1
                WHERE id = ? AND status = 'QUEUED'
                """,
                command.attempt() + 1,
                safeCode,
                safeDetail,
                Timestamp.from(now),
                Timestamp.from(now),
                command.jobId());

        Map<String, Object> deadPayload = commandPayload(command);
        deadPayload.put("errorCode", safeCode);
        deadPayload.put("errorDetail", safeDetail);
        deadPayload.put("failedAt", now.toString());
        insertOutbox(
                outboxId,
                command.jobId(),
                "DOWNLOAD_DISPATCH_DEAD_LETTERED",
                DEAD_ROUTING_KEY,
                json(deadPayload),
                now,
                now,
                "dead:" + command.messageId(),
                new TraceData(command.traceId(), command.spanId()));
    }

    private void insertOutbox(UUID id,
                              UUID jobId,
                              String eventType,
                              String routingKey,
                              String payload,
                              Instant createdAt,
                              Instant availableAt,
                              String deduplicationKey,
                              TraceData trace) {
        jdbcTemplate.update(
                """
                INSERT INTO outbox_event (
                    id, aggregate_id, event_type, routing_key, payload, created_at,
                    available_at, deduplication_key, trace_id, span_id
                ) VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?)
                ON CONFLICT (deduplication_key) WHERE deduplication_key IS NOT NULL DO NOTHING
                """,
                id,
                jobId,
                eventType,
                routingKey,
                payload,
                Timestamp.from(createdAt),
                Timestamp.from(availableAt),
                deduplicationKey,
                trace.traceId(),
                trace.spanId());
    }

    private String payload(DownloadDispatchCommand command) {
        return json(commandPayload(command));
    }

    private Map<String, Object> commandPayload(DownloadDispatchCommand command) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("messageId", command.messageId().toString());
        value.put("jobId", command.jobId().toString());
        value.put("attempt", command.attempt());
        value.put("createdAt", command.createdAt().toString());
        if (command.traceId() != null) {
            value.put("traceId", command.traceId());
            value.put("spanId", command.spanId());
        }
        return value;
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize queue event", exception);
        }
    }

    private String safe(String value, int maxLength) {
        String normalized = value == null || value.isBlank() ? "DISPATCH_FAILED" : value.trim();
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private TraceData currentTrace() {
        Span current = tracer == null ? null : tracer.currentSpan();
        return current == null
                ? new TraceData(null, null)
                : new TraceData(current.context().traceId(), current.context().spanId());
    }

    private record PendingJob(UUID id, int retryCount) {
    }

    private record TraceData(String traceId, String spanId) {
    }
}
