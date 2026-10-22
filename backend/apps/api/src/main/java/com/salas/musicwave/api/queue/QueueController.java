package com.salas.musicwave.api.queue;

import com.salas.musicwave.api.security.CurrentOwnerProvider;
import com.salas.musicwave.application.queue.QueueStartResult;
import com.salas.musicwave.application.queue.StartQueueService;
import com.salas.musicwave.application.execution.DownloadControlService;
import com.salas.musicwave.application.execution.QueueCancellationResult;
import com.salas.musicwave.application.execution.QueueClearResult;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/queue")
public class QueueController {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueueController.class);

    private final StartQueueService service;
    private final CurrentOwnerProvider ownerProvider;
    private final DownloadControlService controlService;

    public QueueController(StartQueueService service,
                           CurrentOwnerProvider ownerProvider,
                           DownloadControlService controlService) {
        this.service = service;
        this.ownerProvider = ownerProvider;
        this.controlService = controlService;
    }

    @PostMapping("/start")
    public ResponseEntity<QueueStartResponse> start() {
        QueueStartResult result = service.start(ownerProvider.currentOwner().id());
        result.jobIds().forEach(jobId -> logQueuedJob(jobId));
        HttpStatus status = result.queuedCount() == 0 ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status)
                .body(new QueueStartResponse(result.queuedCount(), result.jobIds()));
    }

    @PostMapping("/cancel")
    public QueueCancelResponse cancel() {
        QueueCancellationResult result = controlService.cancelActive(
                ownerProvider.currentOwner().id());
        LOGGER.info("Active queue cancellation processed cancelledCount={} requestedCount={}",
                result.cancelledCount(), result.cancellationRequestedCount());
        return new QueueCancelResponse(
                result.cancelledCount(), result.cancellationRequestedCount());
    }

    @DeleteMapping("/completed")
    public QueueClearResponse clearCompleted() {
        QueueClearResult result = controlService.clearCompleted(ownerProvider.currentOwner().id());
        return response(result);
    }

    @DeleteMapping
    public QueueClearResponse clearAll() {
        QueueClearResult result = controlService.clearAll(ownerProvider.currentOwner().id());
        return response(result);
    }

    public record QueueStartResponse(int queuedCount, List<UUID> jobIds) {
    }

    public record QueueCancelResponse(int cancelledCount, int cancellationRequestedCount) {
    }

    public record QueueClearResponse(int clearedCount,
                                     int cancelledCount,
                                     int cancellationRequestedCount) {
    }

    private QueueClearResponse response(QueueClearResult result) {
        return new QueueClearResponse(
                result.clearedCount(),
                result.cancelledCount(),
                result.cancellationRequestedCount());
    }

    private void logQueuedJob(UUID jobId) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("jobId", jobId.toString())) {
            LOGGER.info("Download queued for dispatch");
        }
    }
}
