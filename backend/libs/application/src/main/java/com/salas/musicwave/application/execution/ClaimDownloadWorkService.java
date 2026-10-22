package com.salas.musicwave.application.execution;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ClaimDownloadWorkService {

    private final DownloadExecutionRepository repository;
    private final Clock clock;
    private final Duration leaseDuration;
    private final int maxPerOwner;

    public ClaimDownloadWorkService(DownloadExecutionRepository repository,
                                    Clock clock,
                                    Duration leaseDuration) {
        this(repository, clock, leaseDuration, Integer.MAX_VALUE);
    }

    public ClaimDownloadWorkService(DownloadExecutionRepository repository,
                                    Clock clock,
                                    Duration leaseDuration,
                                    int maxPerOwner) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
        this.leaseDuration = leaseDuration;
        if (maxPerOwner < 1) {
            throw new IllegalArgumentException("maxPerOwner must be positive");
        }
        this.maxPerOwner = maxPerOwner;
    }

    public List<QueuedDownloadWork> claim(String workerId, int limit) {
        if (limit < 1) {
            return List.of();
        }
        return repository.claimReady(workerId, clock.instant(), leaseDuration, limit, maxPerOwner);
    }

    public void renew(String workerId, List<UUID> jobIds) {
        if (!jobIds.isEmpty()) {
            repository.renewLeases(workerId, jobIds, clock.instant(), leaseDuration);
        }
    }
}
