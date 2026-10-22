package com.salas.musicwave.application.download;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class ResolveDownloadTitlesService {

    private final DownloadJobRepository repository;
    private final MediaTitleResolver titleResolver;
    private final Clock clock;
    private final int maxAttempts;
    private final Duration retryBaseDelay;

    public ResolveDownloadTitlesService(DownloadJobRepository repository,
                                        MediaTitleResolver titleResolver,
                                        Clock clock,
                                        int maxAttempts,
                                        Duration retryBaseDelay) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.titleResolver = Objects.requireNonNull(titleResolver, "titleResolver");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (retryBaseDelay.isNegative() || retryBaseDelay.isZero()) {
            throw new IllegalArgumentException("retryBaseDelay must be positive");
        }
        this.maxAttempts = maxAttempts;
        this.retryBaseDelay = retryBaseDelay;
    }

    public ResolutionBatchReport resolveNext(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        Instant now = clock.instant();
        List<PendingTitleResolution> pending = repository.findPendingTitleResolutions(now, limit);
        int resolved = 0;
        int unavailable = 0;
        int retrying = 0;

        for (PendingTitleResolution candidate : pending) {
            TitleResolution result = resolveSafely(candidate.sourceUrl());
            if (result.outcome() == TitleResolution.Outcome.RESOLVED) {
                repository.markTitleResolved(candidate.jobId(), result.title(), now);
                resolved++;
                continue;
            }

            boolean terminal = result.outcome() == TitleResolution.Outcome.UNAVAILABLE
                    || candidate.attempts() + 1 >= maxAttempts;
            Duration retryDelay = retryBaseDelay.multipliedBy(
                    1L << Math.min(candidate.attempts(), 10));
            repository.recordTitleFailure(
                    candidate.jobId(), result.detail(), terminal, now, retryDelay);
            if (terminal) {
                unavailable++;
            } else {
                retrying++;
            }
        }
        return new ResolutionBatchReport(pending.size(), resolved, unavailable, retrying);
    }

    private TitleResolution resolveSafely(String sourceUrl) {
        try {
            return titleResolver.resolve(sourceUrl);
        } catch (RuntimeException exception) {
            String detail = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            return TitleResolution.retryableFailure(detail);
        }
    }

    public record ResolutionBatchReport(int processed,
                                        int resolved,
                                        int unavailable,
                                        int retrying) {
    }
}
