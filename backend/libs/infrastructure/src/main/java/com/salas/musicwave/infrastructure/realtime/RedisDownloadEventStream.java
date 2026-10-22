package com.salas.musicwave.infrastructure.realtime;

import com.salas.musicwave.application.realtime.DownloadEventStream;
import com.salas.musicwave.application.realtime.DownloadLifecycleEvent;
import com.salas.musicwave.application.realtime.StreamCursor;
import com.salas.musicwave.application.realtime.StreamReadBatch;
import com.salas.musicwave.application.realtime.StreamedDownloadEvent;
import com.salas.musicwave.domain.download.DownloadStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

public final class RedisDownloadEventStream implements DownloadEventStream {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisDownloadEventStream.class);

    private final StringRedisTemplate redisTemplate;
    private final String streamKey;

    public RedisDownloadEventStream(StringRedisTemplate redisTemplate, String streamKey) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        if (streamKey == null || streamKey.isBlank()) {
            throw new IllegalArgumentException("streamKey must not be blank");
        }
        this.streamKey = streamKey;
    }

    @Override
    public Optional<StreamCursor> oldestEventId() {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                .range(streamKey, Range.unbounded(), Limit.limit().count(1));
        return firstId(records);
    }

    @Override
    public Optional<StreamCursor> latestEventId() {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                .reverseRange(streamKey, Range.unbounded(), Limit.limit().count(1));
        return firstId(records);
    }

    @Override
    @SuppressWarnings("unchecked")
    public StreamReadBatch readAfter(StreamCursor cursor,
                                     Duration blockTimeout,
                                     int limit) {
        Objects.requireNonNull(cursor, "cursor");
        if (blockTimeout == null || blockTimeout.isNegative() || blockTimeout.isZero()) {
            throw new IllegalArgumentException("blockTimeout must be positive");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                StreamReadOptions.empty().count(limit).block(blockTimeout),
                StreamOffset.create(streamKey, ReadOffset.from(cursor.toString())));
        if (records == null || records.isEmpty()) {
            return new StreamReadBatch(cursor, List.of());
        }

        StreamCursor lastSeen = cursor;
        List<StreamedDownloadEvent> events = new ArrayList<>(records.size());
        for (MapRecord<String, Object, Object> record : records) {
            StreamCursor id = StreamCursor.parse(record.getId().getValue());
            lastSeen = id;
            try {
                events.add(new StreamedDownloadEvent(id, map(record.getValue())));
            } catch (RuntimeException exception) {
                LOGGER.warn("Skipping malformed realtime stream entry eventId={}", id, exception);
            }
        }
        return new StreamReadBatch(lastSeen, events);
    }

    private Optional<StreamCursor> firstId(List<MapRecord<String, Object, Object>> records) {
        if (records == null || records.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(StreamCursor.parse(records.get(0).getId().getValue()));
    }

    private DownloadLifecycleEvent map(Map<Object, Object> fields) {
        return new DownloadLifecycleEvent(
                UUID.fromString(required(fields, "ownerId")),
                UUID.fromString(required(fields, "jobId")),
                DownloadStatus.valueOf(required(fields, "status")),
                new BigDecimal(required(fields, "progress")),
                optional(fields, "speed"),
                optional(fields, "eta"),
                optional(fields, "title"),
                optional(fields, "errorCode"),
                Instant.parse(required(fields, "occurredAt")));
    }

    private String required(Map<Object, Object> fields, String key) {
        Object value = fields.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Missing realtime field " + key);
        }
        return value.toString();
    }

    private String optional(Map<Object, Object> fields, String key) {
        Object value = fields.get(key);
        return value == null || value.toString().isBlank() ? null : value.toString();
    }
}
