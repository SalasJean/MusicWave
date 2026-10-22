package com.salas.musicwave.application.realtime;

import java.time.Duration;
import java.util.Optional;

public interface DownloadEventStream {

    Optional<StreamCursor> oldestEventId();

    Optional<StreamCursor> latestEventId();

    StreamReadBatch readAfter(StreamCursor cursor,
                              Duration blockTimeout,
                              int limit);
}
