package com.salas.musicwave.application.download;

import com.salas.musicwave.domain.download.DownloadJob;

import java.util.Objects;
import java.util.UUID;

public final class DownloadQueryService {

    public static final int MAX_PAGE_SIZE = 100;

    private final DownloadJobRepository repository;

    public DownloadQueryService(DownloadJobRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public DownloadJobPage list(UUID ownerId, int page, int size) {
        if (page < 0) {
            throw new InvalidDownloadRequestException("INVALID_PAGE", "page must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidDownloadRequestException(
                    "INVALID_PAGE_SIZE", "size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return repository.findByOwner(ownerId, page, size);
    }

    public DownloadJob get(UUID ownerId, UUID jobId) {
        return repository.findByIdAndOwner(jobId, ownerId)
                .orElseThrow(() -> new DownloadNotFoundException(jobId));
    }
}
