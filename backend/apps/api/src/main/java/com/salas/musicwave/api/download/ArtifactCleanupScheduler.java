package com.salas.musicwave.api.download;

import com.salas.musicwave.application.artifact.ArtifactCleanupResult;
import com.salas.musicwave.application.artifact.GetArtifactService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ArtifactCleanupScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtifactCleanupScheduler.class);

    private final GetArtifactService service;

    public ArtifactCleanupScheduler(GetArtifactService service) {
        this.service = service;
    }

    @Scheduled(
            initialDelayString = "${musicwave.storage.cleanup-initial-delay:PT10S}",
            fixedDelayString = "${musicwave.storage.cleanup-interval:PT30S}")
    public void cleanup() {
        try {
            ArtifactCleanupResult result = service.cleanupExpired();
            if (result.claimed() > 0) {
                LOGGER.info(
                        "Temporary artifact cleanup processed claimed={} deleted={} failed={}",
                        result.claimed(), result.deleted(), result.failed());
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Temporary artifact cleanup cycle failed and will be retried", exception);
        }
    }
}
