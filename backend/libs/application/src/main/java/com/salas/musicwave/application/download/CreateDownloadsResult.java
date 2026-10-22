package com.salas.musicwave.application.download;

import com.salas.musicwave.domain.download.DownloadJob;

import java.util.List;
import java.util.UUID;

public record CreateDownloadsResult(UUID requestId,
                                    boolean replayed,
                                    List<DownloadJob> jobs,
                                    List<RejectedUrl> rejected) {

    public CreateDownloadsResult {
        jobs = List.copyOf(jobs);
        rejected = List.copyOf(rejected);
    }
}
