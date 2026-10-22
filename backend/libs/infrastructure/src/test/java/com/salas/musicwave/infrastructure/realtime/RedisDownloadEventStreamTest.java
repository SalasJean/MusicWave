package com.salas.musicwave.infrastructure.realtime;

import com.salas.musicwave.application.realtime.StreamCursor;
import com.salas.musicwave.application.realtime.StreamReadBatch;
import com.salas.musicwave.domain.download.DownloadStatus;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisDownloadEventStreamTest {

    @SuppressWarnings("unchecked")
    @Test
    void mapsOrderedRecordsAndAdvancesTheReadCursor() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        StreamOperations<String, Object, Object> operations = mock(StreamOperations.class);
        when(template.<Object, Object>opsForStream()).thenReturn(operations);
        UUID ownerId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        MapRecord<String, Object, Object> first = record(
                "100-1", ownerId, jobId, "DOWNLOADING", "10");
        MapRecord<String, Object, Object> second = record(
                "101-0", ownerId, jobId, "CONVERTING", "100");
        when(operations.read(
                any(StreamReadOptions.class),
                any(StreamOffset[].class))).thenReturn(List.of(first, second));
        RedisDownloadEventStream stream = new RedisDownloadEventStream(template, "events");

        StreamReadBatch batch = stream.readAfter(
                StreamCursor.parse("100-0"), Duration.ofMillis(10), 10);

        assertEquals(StreamCursor.parse("101-0"), batch.lastSeen());
        assertEquals(2, batch.events().size());
        assertEquals(DownloadStatus.DOWNLOADING, batch.events().get(0).event().status());
        assertEquals(DownloadStatus.CONVERTING, batch.events().get(1).event().status());
    }

    private MapRecord<String, Object, Object> record(String id,
                                                     UUID ownerId,
                                                     UUID jobId,
                                                     String status,
                                                     String progress) {
        Map<Object, Object> fields = new LinkedHashMap<>();
        fields.put("ownerId", ownerId.toString());
        fields.put("jobId", jobId.toString());
        fields.put("status", status);
        fields.put("progress", progress);
        fields.put("speed", "");
        fields.put("eta", "");
        fields.put("title", "song");
        fields.put("errorCode", "");
        fields.put("occurredAt", "2026-10-11T12:00:00Z");
        return StreamRecords.newRecord()
                .in("events")
                .withId(id)
                .ofMap(fields);
    }
}
