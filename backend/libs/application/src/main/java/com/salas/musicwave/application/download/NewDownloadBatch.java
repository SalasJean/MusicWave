package com.salas.musicwave.application.download;

import com.salas.musicwave.domain.download.DownloadJob;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NewDownloadBatch(UUID requestId,
                               OwnerIdentity owner,
                               DownloadRequestType requestType,
                               String idempotencyKey,
                               String requestFingerprint,
                               Instant createdAt,
                               List<DownloadJob> jobs) {

    public NewDownloadBatch {
        jobs = List.copyOf(jobs);
    }
}
