package com.salas.musicwave.application.execution;

import com.salas.musicwave.application.artifact.ArtifactStorage;
import com.salas.musicwave.application.artifact.ArtifactStorageUnavailableException;
import com.salas.musicwave.application.artifact.StoredArtifact;
import com.salas.musicwave.application.download.DownloadOutputEvent;
import com.salas.musicwave.application.process.ProcessExecutionException;
import com.salas.musicwave.application.process.StreamingProcessRequest;
import com.salas.musicwave.application.process.StreamingProcessResult;
import com.salas.musicwave.application.process.StreamingProcessRunner;
import com.salas.musicwave.application.realtime.DownloadEventPublisher;
import com.salas.musicwave.application.realtime.DownloadLifecycleEvent;
import com.salas.musicwave.application.security.SourceUrlGuard;
import com.salas.musicwave.application.security.UnsafeSourceUrlException;
import com.salas.musicwave.domain.download.DownloadStatus;
import com.salas.musicwave.domain.download.SourceUrl;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class ExecuteDownloadService {

    private static final Duration MIN_CHECKPOINT_INTERVAL = Duration.ofSeconds(1);
    private static final BigDecimal MIN_PROGRESS_DELTA = BigDecimal.ONE;

    private final DownloadExecutionRepository repository;
    private final StreamingProcessRunner processRunner;
    private final DownloadOutputParser outputParser;
    private final DownloadWorkspace workspace;
    private final ArtifactStorage artifactStorage;
    private final YtDlpCommandFactory commandFactory;
    private final Clock clock;
    private final Duration jobTimeout;
    private final Duration artifactTtl;
    private final DownloadEventPublisher eventPublisher;
    private final SourceUrlGuard sourceUrlGuard;

    public ExecuteDownloadService(DownloadExecutionRepository repository,
                                  StreamingProcessRunner processRunner,
                                  DownloadOutputParser outputParser,
                                  DownloadWorkspace workspace,
                                  ArtifactStorage artifactStorage,
                                  YtDlpCommandFactory commandFactory,
                                  Clock clock,
                                  Duration jobTimeout) {
        this(repository, processRunner, outputParser, workspace, artifactStorage, commandFactory, clock,
                jobTimeout, DownloadEventPublisher.noop(), SourceUrlGuard.permissive(), Duration.ofHours(1));
    }

    public ExecuteDownloadService(DownloadExecutionRepository repository,
                                  StreamingProcessRunner processRunner,
                                  DownloadOutputParser outputParser,
                                  DownloadWorkspace workspace,
                                  ArtifactStorage artifactStorage,
                                  YtDlpCommandFactory commandFactory,
                                  Clock clock,
                                  Duration jobTimeout,
                                  DownloadEventPublisher eventPublisher) {
        this(repository, processRunner, outputParser, workspace, artifactStorage, commandFactory, clock,
                jobTimeout, eventPublisher, SourceUrlGuard.permissive(), Duration.ofHours(1));
    }

    public ExecuteDownloadService(DownloadExecutionRepository repository,
                                  StreamingProcessRunner processRunner,
                                  DownloadOutputParser outputParser,
                                  DownloadWorkspace workspace,
                                  ArtifactStorage artifactStorage,
                                  YtDlpCommandFactory commandFactory,
                                  Clock clock,
                                  Duration jobTimeout,
                                  DownloadEventPublisher eventPublisher,
                                  SourceUrlGuard sourceUrlGuard) {
        this(repository, processRunner, outputParser, workspace, artifactStorage, commandFactory, clock,
                jobTimeout, eventPublisher, sourceUrlGuard, Duration.ofHours(1));
    }

    public ExecuteDownloadService(DownloadExecutionRepository repository,
                                  StreamingProcessRunner processRunner,
                                  DownloadOutputParser outputParser,
                                  DownloadWorkspace workspace,
                                  ArtifactStorage artifactStorage,
                                  YtDlpCommandFactory commandFactory,
                                  Clock clock,
                                  Duration jobTimeout,
                                  DownloadEventPublisher eventPublisher,
                                  SourceUrlGuard sourceUrlGuard,
                                  Duration artifactTtl) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
        this.outputParser = Objects.requireNonNull(outputParser, "outputParser");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.artifactStorage = Objects.requireNonNull(artifactStorage, "artifactStorage");
        this.commandFactory = Objects.requireNonNull(commandFactory, "commandFactory");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (jobTimeout == null || jobTimeout.isNegative() || jobTimeout.isZero()) {
            throw new IllegalArgumentException("jobTimeout must be positive");
        }
        this.jobTimeout = jobTimeout;
        if (artifactTtl == null || artifactTtl.isNegative() || artifactTtl.isZero()) {
            throw new IllegalArgumentException("artifactTtl must be positive");
        }
        this.artifactTtl = artifactTtl;
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.sourceUrlGuard = Objects.requireNonNull(sourceUrlGuard, "sourceUrlGuard");
    }

    public void execute(QueuedDownloadWork work, String workerId) {
        Objects.requireNonNull(work, "work");
        if (!repository.markDownloading(work.jobId(), workerId, clock.instant())) {
            return;
        }

        OutputState state = new OutputState(work.title());
        publishSafely(work, DownloadStatus.DOWNLOADING, state, null, clock.instant());
        try {
            sourceUrlGuard.assertAllowed(SourceUrl.parse(work.sourceUrl()));
            Path jobWorkspace = workspace.prepare(work.jobId());
            StreamingProcessRequest request = new StreamingProcessRequest(
                    work.jobId().toString(),
                    commandFactory.create(work, jobWorkspace),
                    jobTimeout);
            StreamingProcessResult result = processRunner.run(
                    request,
                    line -> handleOutput(work, line, state),
                    () -> repository.cancellationRequested(work.jobId()));

            if (result.cancelled() || repository.cancellationRequested(work.jobId())) {
                workspace.cleanupPartial(work.jobId());
                Instant now = clock.instant();
                if (repository.markCancelled(work.jobId(), "Cancelled by the user", now)) {
                    publishSafely(work, DownloadStatus.CANCELLED, state, null, now);
                }
                return;
            }
            if (result.timedOut()) {
                workspace.cleanupPartial(work.jobId());
                Instant now = clock.instant();
                DownloadStatus status = repository.markFailed(
                        work.jobId(), "DOWNLOAD_TIMEOUT", "Download exceeded its time limit", now);
                publishSafely(work, status, state,
                        status == DownloadStatus.FAILED ? "DOWNLOAD_TIMEOUT" : null, now);
                return;
            }
            if (!result.succeeded()) {
                workspace.cleanupPartial(work.jobId());
                Instant now = clock.instant();
                String errorCode = ytDlpErrorCode(state.failureMessage(), result);
                DownloadStatus status = repository.markFailed(
                        work.jobId(),
                        errorCode,
                        failureDetail(state.failureMessage(), result),
                        now);
                publishSafely(work, status, state,
                        status == DownloadStatus.FAILED ? errorCode : null, now);
                return;
            }

            StagedArtifact stagedArtifact = workspace.locateCompleted(work.jobId(), state.artifactPath());
            StoredArtifact artifact = artifactStorage.store(work.ownerId(), work.jobId(), stagedArtifact);
            Instant now = clock.instant();
            if (markCompletedOrCompensate(work.jobId(), artifact, now)) {
                cleanupSafely(work.jobId());
                state.completed();
                publishSafely(work, DownloadStatus.COMPLETED, state, null, now);
            } else {
                workspace.cleanupPartial(work.jobId());
                publishSafely(work, DownloadStatus.CANCELLED, state, null, now);
            }
        } catch (UnsafeSourceUrlException exception) {
            workspace.cleanupPartial(work.jobId());
            Instant now = clock.instant();
            DownloadStatus status = repository.markFailed(
                    work.jobId(), "URL_DESTINATION_NOT_ALLOWED", "URL destination is not publicly routable", now);
            publishSafely(work, status, state,
                    status == DownloadStatus.FAILED ? "URL_DESTINATION_NOT_ALLOWED" : null, now);
        } catch (ArtifactStorageUnavailableException exception) {
            workspace.cleanupPartial(work.jobId());
            Instant now = clock.instant();
            DownloadStatus status = repository.markFailed(
                    work.jobId(), "ARTIFACT_STORAGE_FAILED", safe(exception.getMessage()), now);
            publishSafely(work, status, state,
                    status == DownloadStatus.FAILED ? "ARTIFACT_STORAGE_FAILED" : null, now);
        } catch (CompletionPersistenceException exception) {
            cleanupSafely(work.jobId());
            throw exception;
        } catch (ProcessExecutionException exception) {
            if (Thread.currentThread().isInterrupted()) {
                throw exception;
            }
            workspace.cleanupPartial(work.jobId());
            Instant now = clock.instant();
            DownloadStatus status = repository.markFailed(
                    work.jobId(), "PROCESS_EXECUTION_FAILED", safe(exception.getMessage()), now);
            publishSafely(work, status, state,
                    status == DownloadStatus.FAILED ? "PROCESS_EXECUTION_FAILED" : null, now);
        } catch (RuntimeException exception) {
            if (Thread.currentThread().isInterrupted()) {
                throw exception;
            }
            workspace.cleanupPartial(work.jobId());
            Instant now = clock.instant();
            DownloadStatus status = repository.markFailed(
                    work.jobId(), "DOWNLOAD_EXECUTION_FAILED", safe(exception.getMessage()), now);
            publishSafely(work, status, state,
                    status == DownloadStatus.FAILED ? "DOWNLOAD_EXECUTION_FAILED" : null, now);
        }
    }

    private boolean markCompletedOrCompensate(java.util.UUID jobId,
                                              StoredArtifact artifact,
                                              Instant now) {
        try {
            boolean completed = repository.markCompleted(jobId, artifact, now.plus(artifactTtl), now);
            if (!completed) {
                deleteSafely(artifact.storageKey());
            }
            return completed;
        } catch (RuntimeException exception) {
            throw new CompletionPersistenceException(exception);
        }
    }

    private void cleanupSafely(java.util.UUID jobId) {
        try {
            workspace.cleanupPartial(jobId);
        } catch (RuntimeException ignored) {
            // The durable artifact and completed database state remain authoritative.
        }
    }

    private void deleteSafely(String storageKey) {
        try {
            artifactStorage.delete(storageKey);
        } catch (RuntimeException ignored) {
            // A deterministic key makes a later retry or cleanup safe.
        }
    }

    private void handleOutput(QueuedDownloadWork work, String line, OutputState state) {
        List<DownloadOutputEvent> events = outputParser.parse(line);
        for (DownloadOutputEvent event : events) {
            if (event instanceof DownloadOutputEvent.Progress progress) {
                Instant now = clock.instant();
                if (state.shouldCheckpoint(
                        progress.percent(), progress.speed(), progress.eta(), now)
                        && repository.checkpointProgress(
                            work.jobId(),
                            progress.percent(),
                            emptyToNull(progress.speed()),
                            emptyToNull(progress.eta()),
                            now)) {
                    publishSafely(work, DownloadStatus.DOWNLOADING, state, null, now);
                }
            } else if (event instanceof DownloadOutputEvent.ConversionStarted) {
                Instant now = clock.instant();
                if (repository.markConverting(work.jobId(), now)) {
                    publishSafely(work, DownloadStatus.CONVERTING, state, null, now);
                }
            } else if (event instanceof DownloadOutputEvent.DestinationDetected destination) {
                state.artifactPath(destination.path());
                if (!destination.title().isBlank()) {
                    state.title(destination.title());
                    repository.updateTitle(work.jobId(), destination.title(), clock.instant());
                }
            } else if (event instanceof DownloadOutputEvent.FailureDetected failure) {
                state.failureMessage(failure.message());
            }
        }
    }

    private void publishSafely(QueuedDownloadWork work,
                               DownloadStatus status,
                               OutputState state,
                               String errorCode,
                               Instant now) {
        try {
            eventPublisher.publish(new DownloadLifecycleEvent(
                    work.ownerId(),
                    work.jobId(),
                    status,
                    state.progress(),
                    state.speed(),
                    state.eta(),
                    state.title(),
                    errorCode,
                    now));
        } catch (RuntimeException ignored) {
            // Realtime delivery is ephemeral and must never invalidate persisted execution state.
        }
    }

    private String failureDetail(String detected, StreamingProcessResult result) {
        if (detected != null && !detected.isBlank()) {
            return safe(detected);
        }
        String output = result.capturedOutput().strip();
        if (!output.isBlank()) {
            return safe(output);
        }
        return "yt-dlp exited with code " + result.exitCode();
    }

    private String ytDlpErrorCode(String detected, StreamingProcessResult result) {
        String evidence = ((detected == null ? "" : detected) + "\n" + result.capturedOutput())
                .toLowerCase(java.util.Locale.ROOT);
        if (evidence.contains("http error 429") || evidence.contains("too many requests")) {
            return "YOUTUBE_RATE_LIMITED";
        }
        if ((evidence.contains("sign in to confirm you") && evidence.contains("not a bot"))
                || evidence.contains("login_required")) {
            return "YOUTUBE_AUTH_REQUIRED";
        }
        return "YT_DLP_EXIT_CODE";
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String safe(String value) {
        String detail = value == null || value.isBlank() ? "Download execution failed" : value.strip();
        return detail.length() > 2_000 ? detail.substring(detail.length() - 2_000) : detail;
    }

    private static final class OutputState {

        private BigDecimal lastProgress = BigDecimal.ZERO;
        private Instant lastCheckpoint;
        private String speed;
        private String eta;
        private String title;
        private String artifactPath;
        private String failureMessage;

        private OutputState(String title) {
            this.title = title;
        }

        synchronized boolean shouldCheckpoint(BigDecimal progress,
                                              String speed,
                                              String eta,
                                              Instant now) {
            boolean first = lastCheckpoint == null;
            boolean advanced = !first
                    && progress.subtract(lastProgress).abs().compareTo(MIN_PROGRESS_DELTA) >= 0;
            boolean elapsed = !first
                    && Duration.between(lastCheckpoint, now).compareTo(MIN_CHECKPOINT_INTERVAL) >= 0;
            boolean terminal = progress.compareTo(BigDecimal.valueOf(100)) == 0;
            if (first || advanced || elapsed || terminal) {
                lastProgress = progress;
                lastCheckpoint = now;
                this.speed = speed;
                this.eta = eta;
                return true;
            }
            return false;
        }

        synchronized void completed() {
            lastProgress = BigDecimal.valueOf(100);
            speed = null;
            eta = null;
        }

        synchronized BigDecimal progress() {
            return lastProgress;
        }

        synchronized String speed() {
            return speed;
        }

        synchronized String eta() {
            return eta;
        }

        synchronized String title() {
            return title;
        }

        synchronized void title(String value) {
            title = value;
        }

        synchronized String artifactPath() {
            return artifactPath;
        }

        synchronized void artifactPath(String value) {
            artifactPath = value;
        }

        synchronized String failureMessage() {
            return failureMessage;
        }

        synchronized void failureMessage(String value) {
            failureMessage = value;
        }
    }

    private static final class CompletionPersistenceException extends RuntimeException {

        private CompletionPersistenceException(RuntimeException cause) {
            super("Could not persist artifact completion; the execution lease will permit recovery", cause);
        }
    }
}
