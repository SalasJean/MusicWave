package com.salas.musicwave.application.download;

import com.salas.musicwave.domain.download.DownloadJob;

import java.util.List;
import java.util.UUID;

public record PersistedDownloadBatch(UUID requestId,
                                     boolean replayed,
                                     List<DownloadJob> jobs) {

    public PersistedDownloadBatch {
        jobs = List.copyOf(jobs);
    }
}
