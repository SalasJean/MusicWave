package com.salas.musicwave.worker.download;

import com.salas.musicwave.application.artifact.ArtifactStorage;
import com.salas.musicwave.application.execution.ClaimDownloadWorkService;
import com.salas.musicwave.application.execution.DownloadExecutionRepository;
import com.salas.musicwave.application.execution.DownloadOutputParser;
import com.salas.musicwave.application.execution.DownloadWorkspace;
import com.salas.musicwave.application.execution.ExecuteDownloadService;
import com.salas.musicwave.application.execution.YtDlpCommandFactory;
import com.salas.musicwave.application.process.StreamingProcessRunner;
import com.salas.musicwave.application.realtime.DownloadEventPublisher;
import com.salas.musicwave.application.security.SourceUrlGuard;
import com.salas.musicwave.infrastructure.download.LocalDownloadWorkspace;
import com.salas.musicwave.infrastructure.process.JvmStreamingProcessRunner;
import com.salas.musicwave.infrastructure.tools.YtDlpOutputParser;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class WorkerDownloadConfiguration {

    @Bean
    StreamingProcessRunner streamingProcessRunner(
            @Value("${musicwave.process.max-captured-characters:262144}") int maxCapturedCharacters,
            @Value("${musicwave.download.cancellation-poll-interval:PT0.5S}") Duration pollInterval) {
        return new JvmStreamingProcessRunner(maxCapturedCharacters, pollInterval);
    }

    @Bean
    DownloadOutputParser downloadOutputParser() {
        return new YtDlpOutputParser();
    }

    @Bean
    DownloadWorkspace downloadWorkspace(
            @Value("${musicwave.download.media-root:/var/lib/musicwave/media}") Path mediaRoot) {
        return new LocalDownloadWorkspace(mediaRoot.resolve("staging"));
    }

    @Bean
    YtDlpCommandFactory ytDlpCommandFactory(
            @Value("${musicwave.tools.yt-dlp-command:yt-dlp}") String ytDlpCommand,
            @Value("${musicwave.tools.ffmpeg-command:ffmpeg}") String ffmpegLocation,
            @Value("${musicwave.download.audio-quality:192K}") String audioQuality) {
        return new YtDlpCommandFactory(ytDlpCommand, ffmpegLocation, audioQuality);
    }

    @Bean
    ClaimDownloadWorkService claimDownloadWorkService(
            DownloadExecutionRepository repository,
            Clock clock,
            @Value("${musicwave.download.execution-lease:PT1M}") Duration leaseDuration,
            @Value("${musicwave.download.max-concurrency-per-owner:10}") int maxPerOwner) {
        return new ClaimDownloadWorkService(repository, clock, leaseDuration, maxPerOwner);
    }

    @Bean
    ExecuteDownloadService executeDownloadService(
            DownloadExecutionRepository repository,
            StreamingProcessRunner processRunner,
            DownloadOutputParser outputParser,
            DownloadWorkspace workspace,
            ArtifactStorage artifactStorage,
            YtDlpCommandFactory commandFactory,
            Clock clock,
            DownloadEventPublisher eventPublisher,
            SourceUrlGuard sourceUrlGuard,
            @Value("${musicwave.download.job-timeout:PT2H}") Duration jobTimeout,
            @Value("${musicwave.storage.artifact-ttl:PT1H}") Duration artifactTtl) {
        return new ExecuteDownloadService(
                repository,
                processRunner,
                outputParser,
                workspace,
                artifactStorage,
                commandFactory,
                clock,
                jobTimeout,
                eventPublisher,
                sourceUrlGuard,
                artifactTtl);
    }
}
