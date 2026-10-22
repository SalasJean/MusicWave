package com.salas.musicwave.infrastructure.observability;

import com.salas.musicwave.domain.download.DownloadStatus;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class MusicWavePersistenceMetrics implements MeterBinder {

    private static final List<String> OUTBOX_STATES = List.of("ready", "delayed", "leased", "published");
    private static final List<String> INBOX_STATES = List.of(
            "RECEIVED", "PROCESSING", "COMPLETED", "FAILED", "CANCELLED");

    private final JdbcTemplate jdbcTemplate;
    private final String applicationName;

    public MusicWavePersistenceMetrics(
            JdbcTemplate jdbcTemplate,
            @Value("${spring.application.name:musicwave}") String applicationName) {
        this.jdbcTemplate = jdbcTemplate;
        this.applicationName = applicationName;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        if ("musicwave-api".equals(applicationName)) {
            for (DownloadStatus status : DownloadStatus.values()) {
                Gauge.builder("musicwave.download.jobs", () -> countJobs(status.name()))
                        .description("Authoritative download jobs by lifecycle status")
                        .tag("status", status.name())
                        .register(registry);
            }
            for (String state : OUTBOX_STATES) {
                Gauge.builder("musicwave.outbox.events", () -> countOutbox(state))
                        .description("Transactional outbox events by bounded state")
                        .tag("state", state)
                        .register(registry);
            }
        }
        if ("musicwave-worker".equals(applicationName)) {
            for (String state : INBOX_STATES) {
                Gauge.builder("musicwave.worker.inbox", () -> countInbox(state))
                        .description("Durable worker inbox entries by processing state")
                        .tag("state", state)
                        .register(registry);
            }
        }
    }

    private double countJobs(String status) {
        return safeCount("SELECT count(*) FROM download_job WHERE status = ?", status);
    }

    private double countInbox(String status) {
        return safeCount("SELECT count(*) FROM worker_inbox WHERE processing_status = ?", status);
    }

    private double countOutbox(String state) {
        String sql = switch (state) {
            case "ready" -> """
                    SELECT count(*) FROM outbox_event
                    WHERE published_at IS NULL AND available_at <= CURRENT_TIMESTAMP
                      AND (locked_until IS NULL OR locked_until <= CURRENT_TIMESTAMP)
                    """;
            case "delayed" -> """
                    SELECT count(*) FROM outbox_event
                    WHERE published_at IS NULL AND available_at > CURRENT_TIMESTAMP
                    """;
            case "leased" -> """
                    SELECT count(*) FROM outbox_event
                    WHERE published_at IS NULL AND locked_until > CURRENT_TIMESTAMP
                    """;
            case "published" -> "SELECT count(*) FROM outbox_event WHERE published_at IS NOT NULL";
            default -> throw new IllegalArgumentException("Unknown outbox state");
        };
        return safeCount(sql);
    }

    private double safeCount(String sql, Object... arguments) {
        try {
            Long count = jdbcTemplate.queryForObject(sql, Long.class, arguments);
            return count == null ? Double.NaN : count.doubleValue();
        } catch (RuntimeException exception) {
            return Double.NaN;
        }
    }
}
