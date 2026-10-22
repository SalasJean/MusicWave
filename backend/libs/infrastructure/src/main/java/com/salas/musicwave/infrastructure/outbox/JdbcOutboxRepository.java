package com.salas.musicwave.infrastructure.outbox;

import com.salas.musicwave.application.outbox.OutboxEvent;
import com.salas.musicwave.application.outbox.OutboxRepository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcOutboxRepository implements OutboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public List<OutboxEvent> claimAvailable(String publisherId,
                                            Instant now,
                                            Duration leaseDuration,
                                            int limit) {
        return jdbcTemplate.query(
                """
                WITH candidates AS (
                    SELECT id
                    FROM outbox_event
                    WHERE published_at IS NULL
                      AND available_at <= ?
                      AND (locked_until IS NULL OR locked_until <= ?)
                    ORDER BY available_at, created_at, id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE outbox_event event
                SET locked_by = ?, locked_until = ?
                FROM candidates
                WHERE event.id = candidates.id
                RETURNING event.id, event.aggregate_id, event.event_type,
                          event.routing_key, event.payload::text, event.publish_attempts,
                          event.trace_id, event.span_id
                """,
                (resultSet, rowNumber) -> new OutboxEvent(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("aggregate_id", UUID.class),
                        resultSet.getString("event_type"),
                        resultSet.getString("routing_key"),
                        resultSet.getString("payload"),
                        resultSet.getInt("publish_attempts"),
                        resultSet.getString("trace_id"),
                        resultSet.getString("span_id")),
                Timestamp.from(now),
                Timestamp.from(now),
                limit,
                publisherId,
                Timestamp.from(now.plus(leaseDuration)));
    }

    @Override
    public void markPublished(UUID eventId, String publisherId, Instant publishedAt) {
        int updated = jdbcTemplate.update(
                """
                UPDATE outbox_event
                SET published_at = ?, locked_by = NULL, locked_until = NULL, last_error = NULL
                WHERE id = ? AND published_at IS NULL AND locked_by = ?
                """,
                Timestamp.from(publishedAt), eventId, publisherId);
        if (updated != 1) {
            throw new IllegalStateException("Outbox lease was lost before publication was recorded");
        }
    }

    @Override
    public void recordFailure(UUID eventId,
                              String publisherId,
                              Instant nextAttemptAt,
                              String detail) {
        jdbcTemplate.update(
                """
                UPDATE outbox_event
                SET publish_attempts = publish_attempts + 1, last_error = ?,
                    available_at = ?, locked_by = NULL, locked_until = NULL
                WHERE id = ? AND published_at IS NULL AND locked_by = ?
                """,
                detail,
                Timestamp.from(nextAttemptAt),
                eventId,
                publisherId);
    }
}
