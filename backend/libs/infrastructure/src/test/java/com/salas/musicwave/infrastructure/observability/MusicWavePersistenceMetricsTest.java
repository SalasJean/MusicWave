package com.salas.musicwave.infrastructure.observability;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MusicWavePersistenceMetricsTest {

    @Test
    void apiPublishesOnlyBoundedJobAndOutboxDimensions() {
        JdbcTemplate jdbc = jdbcReturning(7L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new MusicWavePersistenceMetrics(jdbc, "musicwave-api").bindTo(registry);

        assertNotNull(registry.find("musicwave.download.jobs")
                .tag("status", "PENDING").gauge());
        assertEquals(7.0, registry.find("musicwave.outbox.events")
                .tag("state", "ready").gauge().value());
        assertFalse(registry.getMeters().stream()
                .map(Meter::getId)
                .flatMap(id -> id.getTags().stream())
                .anyMatch(tag -> tag.getKey().equals("jobId")
                        || tag.getKey().equals("ownerId")
                        || tag.getKey().equals("requestId")));
    }

    @Test
    void workerPublishesDurableInboxStatesWithoutBusinessIdentifiers() {
        JdbcTemplate jdbc = jdbcReturning(3L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new MusicWavePersistenceMetrics(jdbc, "musicwave-worker").bindTo(registry);

        assertEquals(3.0, registry.find("musicwave.worker.inbox")
                .tag("state", "PROCESSING").gauge().value());
        assertFalse(registry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .anyMatch(tag -> tag.getKey().endsWith("Id")));
    }

    private JdbcTemplate jdbcReturning(long value) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(value);
        return jdbc;
    }
}
