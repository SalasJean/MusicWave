package com.salas.musicwave.api.download;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salas.musicwave.application.download.CreateDownloadsService;
import com.salas.musicwave.application.download.DownloadJobRepository;
import com.salas.musicwave.application.download.DownloadQueryService;
import com.salas.musicwave.application.download.IdentifierGenerator;
import com.salas.musicwave.application.download.MediaTitleResolver;
import com.salas.musicwave.application.download.ResolveDownloadTitlesService;
import com.salas.musicwave.application.artifact.ArtifactAccessRepository;
import com.salas.musicwave.application.artifact.ArtifactStorage;
import com.salas.musicwave.application.artifact.GetArtifactService;
import com.salas.musicwave.application.execution.DownloadControlRepository;
import com.salas.musicwave.application.execution.DownloadControlService;
import com.salas.musicwave.application.realtime.DownloadEventPublisher;
import com.salas.musicwave.application.security.SourceUrlGuard;
import com.salas.musicwave.infrastructure.download.YouTubeOEmbedTitleResolver;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DownloadConfiguration {

    @Bean
    IdentifierGenerator identifierGenerator() {
        return UUID::randomUUID;
    }

    @Bean
    CreateDownloadsService createDownloadsService(
            DownloadJobRepository repository,
            IdentifierGenerator identifierGenerator,
            Clock clock,
            SourceUrlGuard sourceUrlGuard) {
        return new CreateDownloadsService(repository, identifierGenerator, clock, sourceUrlGuard);
    }

    @Bean
    DownloadQueryService downloadQueryService(DownloadJobRepository repository) {
        return new DownloadQueryService(repository);
    }

    @Bean
    DownloadControlService downloadControlService(
            DownloadControlRepository repository,
            Clock clock,
            DownloadEventPublisher eventPublisher) {
        return new DownloadControlService(repository, clock, eventPublisher);
    }

    @Bean
    GetArtifactService getArtifactService(
            ArtifactAccessRepository repository,
            ArtifactStorage storage,
            IdentifierGenerator identifierGenerator,
            Clock clock,
            @Value("${musicwave.storage.delivery-lease:PT30M}") Duration deliveryLease,
            @Value("${musicwave.storage.deletion-lease:PT1M}") Duration deletionLease,
            @Value("${musicwave.storage.deletion-retry-delay:PT30S}") Duration deletionRetryDelay,
            @Value("${musicwave.storage.cleanup-batch-size:50}") int cleanupBatchSize) {
        return new GetArtifactService(
                repository,
                storage,
                identifierGenerator,
                clock,
                deliveryLease,
                deletionLease,
                deletionRetryDelay,
                cleanupBatchSize);
    }

    @Bean
    MediaTitleResolver mediaTitleResolver(
            ObjectMapper objectMapper,
            @Value("${musicwave.title.oembed-endpoint:https://www.youtube.com/oembed}") URI endpoint,
            @Value("${musicwave.title.timeout:PT3S}") Duration timeout) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        return new YouTubeOEmbedTitleResolver(client, objectMapper, endpoint, timeout);
    }

    @Bean
    ResolveDownloadTitlesService resolveDownloadTitlesService(
            DownloadJobRepository repository,
            MediaTitleResolver titleResolver,
            Clock clock,
            @Value("${musicwave.title.max-attempts:3}") int maxAttempts,
            @Value("${musicwave.title.retry-base-delay:PT10S}") Duration retryBaseDelay) {
        return new ResolveDownloadTitlesService(
                repository, titleResolver, clock, maxAttempts, retryBaseDelay);
    }
}
