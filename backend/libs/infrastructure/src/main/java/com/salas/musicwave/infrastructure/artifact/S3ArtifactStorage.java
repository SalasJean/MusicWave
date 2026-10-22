package com.salas.musicwave.infrastructure.artifact;

import com.salas.musicwave.application.artifact.ArtifactMissingException;
import com.salas.musicwave.application.artifact.ArtifactStorage;
import com.salas.musicwave.application.artifact.ArtifactStorageUnavailableException;
import com.salas.musicwave.application.artifact.ArtifactStream;
import com.salas.musicwave.application.artifact.StoredArtifact;
import com.salas.musicwave.application.execution.StagedArtifact;

import java.util.Map;
import java.util.UUID;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

public final class S3ArtifactStorage implements ArtifactStorage {

    private static final String CONTENT_TYPE = "audio/mpeg";
    private final S3Client client;
    private final String bucket;

    public S3ArtifactStorage(S3Client client, String bucket) {
        this.client = java.util.Objects.requireNonNull(client, "client");
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("bucket must not be blank");
        }
        this.bucket = bucket;
        ensureBucket();
    }

    @Override
    public StoredArtifact store(UUID ownerId, UUID jobId, StagedArtifact stagedArtifact) {
        String key = ArtifactKeys.forJob(ownerId, jobId);
        String filename = ArtifactKeys.safeFilename(stagedArtifact.filename());
        String sha256 = ArtifactDigests.sha256(stagedArtifact.path());
        try {
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(CONTENT_TYPE)
                            .contentLength(stagedArtifact.sizeBytes())
                            .metadata(Map.of("sha256", sha256))
                            .build(),
                    RequestBody.fromFile(stagedArtifact.path()));
            return new StoredArtifact(
                    key, filename, CONTENT_TYPE, stagedArtifact.sizeBytes(), sha256);
        } catch (RuntimeException exception) {
            throw unavailable("Could not store the MP3 artifact", exception);
        }
    }

    @Override
    public ArtifactStream open(StoredArtifact artifact) {
        try {
            ResponseInputStream<GetObjectResponse> response = client.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(artifact.storageKey()).build());
            return new ArtifactStream(response, response.response().contentLength());
        } catch (NoSuchKeyException exception) {
            throw new ArtifactMissingException("The stored artifact is missing", exception);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new ArtifactMissingException("The stored artifact is missing", exception);
            }
            throw unavailable("Could not open the MP3 artifact", exception);
        } catch (RuntimeException exception) {
            throw unavailable("Could not open the MP3 artifact", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            client.deleteObject(builder -> builder.bucket(bucket).key(storageKey));
        } catch (RuntimeException exception) {
            throw unavailable("Could not delete the MP3 artifact", exception);
        }
    }

    private void ensureBucket() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) {
                throw unavailable("Could not inspect the artifact bucket", exception);
            }
            try {
                client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            } catch (S3Exception createException) {
                if (createException.statusCode() != 409) {
                    throw unavailable("Could not create the artifact bucket", createException);
                }
            }
        } catch (RuntimeException exception) {
            throw unavailable("Could not inspect the artifact bucket", exception);
        }
    }

    private ArtifactStorageUnavailableException unavailable(String message, RuntimeException cause) {
        if (cause instanceof ArtifactStorageUnavailableException unavailable) {
            return unavailable;
        }
        return new ArtifactStorageUnavailableException(message, cause);
    }
}
