package com.salas.musicwave.api.download;

import com.salas.musicwave.application.download.ResolveDownloadTitlesService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DownloadTitleScheduler {

    private final ResolveDownloadTitlesService service;
    private final int batchSize;

    public DownloadTitleScheduler(
            ResolveDownloadTitlesService service,
            @Value("${musicwave.title.batch-size:10}") int batchSize) {
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("musicwave.title.batch-size must be between 1 and 100");
        }
        this.service = service;
        this.batchSize = batchSize;
    }

    @Scheduled(
            initialDelayString = "${musicwave.title.initial-delay:PT2S}",
            fixedDelayString = "${musicwave.title.scan-interval:PT5S}")
    public void resolvePendingTitles() {
        service.resolveNext(batchSize);
    }
}
