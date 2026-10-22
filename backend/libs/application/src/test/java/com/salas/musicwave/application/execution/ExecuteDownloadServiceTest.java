package com.salas.musicwave.application.execution;

import com.salas.musicwave.application.artifact.ArtifactStorage;
import com.salas.musicwave.application.artifact.ArtifactStorageUnavailableException;
import com.salas.musicwave.application.artifact.ArtifactStream;
import com.salas.musicwave.application.artifact.StoredArtifact;
import com.salas.musicwave.application.process.StreamingProcessRequest;
import com.salas.musicwave.application.process.StreamingProcessResult;
import com.salas.musicwave.application.process.StreamingProcessRunner;
import com.salas.musicwave.application.download.DownloadOutputEvent;
import com.salas.musicwave.application.realtime.DownloadEventPublisher;
import com.salas.musicwave.application.realtime.DownloadLifecycleEvent;
import com.salas.musicwave.application.security.UnsafeSourceUrlException;
import com.salas.musicwave.domain.download.DownloadStatus;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecuteDownloadServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final UUID JOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final QueuedDownloadWork WORK = new QueuedDownloadWork(
            UUID.fromString("00000000-0000-0000-0000-000000000010"),
            JOB_ID,
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "https://example.com/audio",
            "audio",
            1);

    @Test
    void persistsProgressConversionAndOneCompletedArtifact() {
        RecordingRepository repository = new RecordingRepository();
        RecordingWorkspace workspace = new RecordingWorkspace();
        StreamingProcessRunner runner = new EmittingRunner(
                List.of(
                        "musicwave_progress:45.3%|1.20MiB/s|00:03",
                        "[ExtractAudio] Destination: /media/job/song.mp3",
                        "musicwave_artifact:/media/job/song.mp3"),
                new StreamingProcessResult(0, "", false, false, Duration.ofSeconds(1)));

        service(repository, workspace, runner).execute(WORK, "worker-1");

        assertTrue(repository.downloading);
        assertEquals(new BigDecimal("45.3"), repository.progress);
        assertEquals("1.20MiB/s", repository.speed);
        assertEquals("00:03", repository.eta);
        assertTrue(repository.converting);
        assertEquals("/media/job/song.mp3", workspace.reportedPath);
        assertEquals("completed", repository.terminal);
        assertEquals("owners/owner/jobs/job/artifact.mp3", repository.artifact.storageKey());
        assertEquals(NOW.plus(Duration.ofHours(1)), repository.artifactExpiresAt);
        assertEquals(
                List.of(
                        DownloadStatus.DOWNLOADING,
                        DownloadStatus.DOWNLOADING,
                        DownloadStatus.CONVERTING,
                        DownloadStatus.COMPLETED),
                repository.events.stream().map(DownloadLifecycleEvent::status).toList());
        assertEquals(new BigDecimal("100"), repository.events.get(3).progress());
    }

    @Test
    void timeoutFailsAndCleansPartialFiles() {
        RecordingRepository repository = new RecordingRepository();
        RecordingWorkspace workspace = new RecordingWorkspace();
        StreamingProcessRunner runner = new EmittingRunner(
                List.of(), new StreamingProcessResult(-1, "", true, false, Duration.ofSeconds(2)));

        service(repository, workspace, runner).execute(WORK, "worker-1");

        assertEquals("failed", repository.terminal);
        assertEquals("DOWNLOAD_TIMEOUT", repository.errorCode);
        assertTrue(workspace.cleaned);
        assertEquals(DownloadStatus.FAILED, repository.events.get(1).status());
    }

    @Test
    void classifiesYoutubeRateLimitingWithAStableErrorCode() {
        RecordingRepository repository = new RecordingRepository();
        RecordingWorkspace workspace = new RecordingWorkspace();
        StreamingProcessRunner runner = new EmittingRunner(
                List.of(),
                new StreamingProcessResult(
                        1,
                        "Unable to download webpage: HTTP Error 429: Too Many Requests\n"
                                + "playability status: LOGIN_REQUIRED",
                        false,
                        false,
                        Duration.ofSeconds(1)));

        service(repository, workspace, runner).execute(WORK, "worker-1");

        assertEquals("failed", repository.terminal);
        assertEquals("YOUTUBE_RATE_LIMITED", repository.errorCode);
        assertEquals("YOUTUBE_RATE_LIMITED", repository.events.get(1).errorCode());
        assertTrue(workspace.cleaned);
    }

    @Test
    void classifiesYoutubeBotAuthenticationChallengeWithAStableErrorCode() {
        RecordingRepository repository = new RecordingRepository();
        RecordingWorkspace workspace = new RecordingWorkspace();
        StreamingProcessRunner runner = new EmittingRunner(
                List.of(),
                new StreamingProcessResult(
                        1,
                        "Sign in to confirm you’re not a bot (LOGIN_REQUIRED)",
                        false,
                        false,
                        Duration.ofSeconds(1)));

        service(repository, workspace, runner).execute(WORK, "worker-1");

        assertEquals("YOUTUBE_AUTH_REQUIRED", repository.errorCode);
        assertEquals("YOUTUBE_AUTH_REQUIRED", repository.events.get(1).errorCode());
    }

    @Test
    void cancellationWinsAndCleansPartialFiles() {
        RecordingRepository repository = new RecordingRepository();
        RecordingWorkspace workspace = new RecordingWorkspace();
        StreamingProcessRunner runner = new EmittingRunner(
                List.of(), new StreamingProcessResult(-1, "", false, true, Duration.ofMillis(50)));

        service(repository, workspace, runner).execute(WORK, "worker-1");

        assertEquals("cancelled", repository.terminal);
        assertTrue(workspace.cleaned);
        assertEquals(DownloadStatus.CANCELLED, repository.events.get(1).status());
    }

    @Test
    void redisPublicationFailureCannotFailACompletedDownload() {
        RecordingRepository repository = new RecordingRepository();
        RecordingWorkspace workspace = new RecordingWorkspace();
        StreamingProcessRunner runner = new EmittingRunner(
                List.of("musicwave_artifact:/media/job/song.mp3"),
                new StreamingProcessResult(0, "", false, false, Duration.ofSeconds(1)));
        DownloadEventPublisher unavailableRedis = event -> {
            throw new IllegalStateException("Redis unavailable");
        };

        service(repository, workspace, runner, unavailableRedis).execute(WORK, "worker-1");

        assertEquals("completed", repository.terminal);
        assertEquals("owners/owner/jobs/job/artifact.mp3", repository.artifact.storageKey());
    }

    @Test
    void lostCancellationRaceDeletesUploadedArtifactAndStaging() {
        RecordingRepository repository = new RecordingRepository();
        repository.completionAccepted = false;
        RecordingWorkspace workspace = new RecordingWorkspace();
        RecordingStorage storage = new RecordingStorage();
        StreamingProcessRunner runner = new EmittingRunner(
                List.of("musicwave_artifact:/media/job/song.mp3"),
                new StreamingProcessResult(0, "", false, false, Duration.ofSeconds(1)));

        service(repository, workspace, runner, repository.events::add, storage)
                .execute(WORK, "worker-1");

        assertEquals("cancelled", repository.terminal);
        assertTrue(workspace.cleaned);
        assertEquals("owners/owner/jobs/job/artifact.mp3", storage.deletedKey);
    }

    @Test
    void storageFailurePersistsAStableFailureCode() {
        RecordingRepository repository = new RecordingRepository();
        RecordingWorkspace workspace = new RecordingWorkspace();
        StreamingProcessRunner runner = new EmittingRunner(
                List.of("musicwave_artifact:/media/job/song.mp3"),
                new StreamingProcessResult(0, "", false, false, Duration.ofSeconds(1)));
        RecordingStorage storage = new RecordingStorage();
        storage.failStore = true;

        service(repository, workspace, runner, repository.events::add, storage)
                .execute(WORK, "worker-1");

        assertEquals("failed", repository.terminal);
        assertEquals("ARTIFACT_STORAGE_FAILED", repository.errorCode);
        assertTrue(workspace.cleaned);
    }

    @Test
    void ambiguousCompletionCommitKeepsDeterministicObjectForLeaseRecovery() {
        RecordingRepository repository = new RecordingRepository();
        repository.failCompletionPersistence = true;
        RecordingWorkspace workspace = new RecordingWorkspace();
        RecordingStorage storage = new RecordingStorage();
        StreamingProcessRunner runner = new EmittingRunner(
                List.of("musicwave_artifact:/media/job/song.mp3"),
                new StreamingProcessResult(0, "", false, false, Duration.ofSeconds(1)));

        assertThrows(RuntimeException.class, () ->
                service(repository, workspace, runner, repository.events::add, storage)
                        .execute(WORK, "worker-1"));

        assertNull(storage.deletedKey);
        assertNull(repository.terminal);
        assertTrue(workspace.cleaned);
    }

    @Test
    void rechecksTheDestinationBeforeStartingTheExternalProcess() {
        RecordingRepository repository = new RecordingRepository();
        RecordingWorkspace workspace = new RecordingWorkspace();
        StreamingProcessRunner runner = (request, output, cancelled) -> {
            throw new AssertionError("external process must not start");
        };
        ExecuteDownloadService guarded = new ExecuteDownloadService(
                repository,
                runner,
                this::parse,
                workspace,
                new RecordingStorage(),
                new YtDlpCommandFactory("yt-dlp", "ffmpeg", "192K"),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(10),
                repository.events::add,
                sourceUrl -> { throw new UnsafeSourceUrlException("URL_DESTINATION_NOT_ALLOWED"); });

        guarded.execute(WORK, "worker-1");

        assertEquals("failed", repository.terminal);
        assertEquals("URL_DESTINATION_NOT_ALLOWED", repository.errorCode);
        assertTrue(workspace.cleaned);
    }

    private ExecuteDownloadService service(RecordingRepository repository,
                                           RecordingWorkspace workspace,
                                           StreamingProcessRunner runner) {
        return service(repository, workspace, runner, repository.events::add);
    }

    private ExecuteDownloadService service(RecordingRepository repository,
                                           RecordingWorkspace workspace,
                                           StreamingProcessRunner runner,
                                           DownloadEventPublisher publisher) {
        return service(repository, workspace, runner, publisher, new RecordingStorage());
    }

    private ExecuteDownloadService service(RecordingRepository repository,
                                           RecordingWorkspace workspace,
                                           StreamingProcessRunner runner,
                                           DownloadEventPublisher publisher,
                                           ArtifactStorage storage) {
        return new ExecuteDownloadService(
                repository,
                runner,
                this::parse,
                workspace,
                storage,
                new YtDlpCommandFactory("yt-dlp", "ffmpeg", "192K"),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(10),
                publisher);
    }

    private List<DownloadOutputEvent> parse(String line) {
        if (line.startsWith("musicwave_progress:")) {
            return List.of(new DownloadOutputEvent.Progress(
                    new BigDecimal("45.3"), "1.20MiB/s", "00:03"));
        }
        if (line.startsWith("[ExtractAudio]")) {
            return List.of(
                    new DownloadOutputEvent.ConversionStarted(),
                    new DownloadOutputEvent.DestinationDetected("/media/job/song.mp3", "song"));
        }
        if (line.startsWith("musicwave_artifact:")) {
            return List.of(new DownloadOutputEvent.DestinationDetected(
                    "/media/job/song.mp3", "song"));
        }
        return List.of();
    }

    private static final class EmittingRunner implements StreamingProcessRunner {

        private final List<String> lines;
        private final StreamingProcessResult result;

        private EmittingRunner(List<String> lines, StreamingProcessResult result) {
            this.lines = lines;
            this.result = result;
        }

        @Override
        public StreamingProcessResult run(StreamingProcessRequest request,
                                          Consumer<String> outputLineConsumer,
                                          BooleanSupplier cancellationRequested) {
            lines.forEach(outputLineConsumer);
            return result;
        }
    }

    private static final class RecordingWorkspace implements DownloadWorkspace {

        private boolean cleaned;
        private String reportedPath;

        @Override
        public Path prepare(UUID jobId) {
            return Path.of("/safe", jobId.toString());
        }

        @Override
        public StagedArtifact locateCompleted(UUID jobId, String reportedPath) {
            this.reportedPath = reportedPath;
            return new StagedArtifact(Path.of("/safe/job/song.mp3"), "song.mp3", 123);
        }

        @Override
        public void cleanupPartial(UUID jobId) {
            cleaned = true;
        }
    }

    private static final class RecordingRepository implements DownloadExecutionRepository {

        private boolean downloading;
        private BigDecimal progress;
        private String speed;
        private String eta;
        private boolean converting;
        private String terminal;
        private String errorCode;
        private StoredArtifact artifact;
        private Instant artifactExpiresAt;
        private boolean completionAccepted = true;
        private boolean failCompletionPersistence;
        private final List<DownloadLifecycleEvent> events = new ArrayList<>();

        @Override
        public List<QueuedDownloadWork> claimReady(
                String workerId, Instant now, Duration leaseDuration, int limit, int maxPerOwner) {
            return List.of();
        }

        @Override
        public void renewLeases(
                String workerId, List<UUID> jobIds, Instant now, Duration leaseDuration) {
        }

        @Override
        public boolean markDownloading(UUID jobId, String workerId, Instant now) {
            downloading = true;
            return true;
        }

        @Override
        public boolean checkpointProgress(
                UUID jobId, BigDecimal progress, String speed, String eta, Instant now) {
            this.progress = progress;
            this.speed = speed;
            this.eta = eta;
            return true;
        }

        @Override
        public boolean markConverting(UUID jobId, Instant now) {
            converting = true;
            return true;
        }

        @Override
        public void updateTitle(UUID jobId, String title, Instant now) {
        }

        @Override
        public boolean cancellationRequested(UUID jobId) {
            return false;
        }

        @Override
        public boolean markCompleted(UUID jobId, StoredArtifact artifact, Instant artifactExpiresAt, Instant now) {
            this.artifact = artifact;
            this.artifactExpiresAt = artifactExpiresAt;
            if (failCompletionPersistence) {
                throw new IllegalStateException("database response was ambiguous");
            }
            terminal = completionAccepted ? "completed" : "cancelled";
            return completionAccepted;
        }

        @Override
        public DownloadStatus markFailed(
                UUID jobId, String errorCode, String errorMessage, Instant now) {
            terminal = "failed";
            this.errorCode = errorCode;
            return DownloadStatus.FAILED;
        }

        @Override
        public boolean markCancelled(UUID jobId, String detail, Instant now) {
            terminal = "cancelled";
            return true;
        }
    }

    private static final class RecordingStorage implements ArtifactStorage {

        private boolean failStore;
        private String deletedKey;

        @Override
        public StoredArtifact store(UUID ownerId, UUID jobId, StagedArtifact stagedArtifact) {
            if (failStore) {
                throw new ArtifactStorageUnavailableException(
                        "storage unavailable", new IllegalStateException("offline"));
            }
            return new StoredArtifact(
                    "owners/owner/jobs/job/artifact.mp3",
                    stagedArtifact.filename(),
                    "audio/mpeg",
                    stagedArtifact.sizeBytes(),
                    "a".repeat(64));
        }

        @Override
        public ArtifactStream open(StoredArtifact artifact) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String storageKey) {
            deletedKey = storageKey;
        }
    }
}
