package com.salas.musicwave.infrastructure.artifact;

import com.salas.musicwave.application.artifact.ArtifactStorage;

import java.net.URI;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
public class ArtifactStorageConfiguration {

    @Bean
    @ConditionalOnProperty(name = "musicwave.storage.backend", havingValue = "local")
    ArtifactStorage localArtifactStorage(
            @Value("${musicwave.storage.local-root:/var/lib/musicwave/artifacts}") Path root) {
        return new LocalArtifactStorage(root);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "musicwave.storage.backend", havingValue = "s3", matchIfMissing = true)
    S3Client artifactS3Client(
            @Value("${musicwave.storage.endpoint:http://localhost:9000}") URI endpoint,
            @Value("${musicwave.storage.region:us-east-1}") String region,
            @Value("${musicwave.storage.path-style:true}") boolean pathStyle,
            @Value("${musicwave.storage.access-key:}") String accessKey,
            @Value("${musicwave.storage.secret-key:}") String secretKey) {
        var credentials = accessKey.isBlank() && secretKey.isBlank()
                ? AnonymousCredentialsProvider.create()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        return S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.of(region))
                .forcePathStyle(pathStyle)
                .credentialsProvider(credentials)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "musicwave.storage.backend", havingValue = "s3", matchIfMissing = true)
    ArtifactStorage s3ArtifactStorage(
            S3Client client,
            @Value("${musicwave.storage.bucket:musicwave}") String bucket) {
        return new S3ArtifactStorage(client, bucket);
    }
}
