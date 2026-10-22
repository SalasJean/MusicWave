package com.salas.musicwave.api.download;

import com.salas.musicwave.api.security.CurrentOwnerProvider;
import com.salas.musicwave.application.execution.DownloadControlResult;
import com.salas.musicwave.application.execution.DownloadControlService;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/downloads")
public class DownloadControlController {

    private final DownloadControlService service;
    private final CurrentOwnerProvider ownerProvider;

    public DownloadControlController(DownloadControlService service,
                                     CurrentOwnerProvider ownerProvider) {
        this.service = service;
        this.ownerProvider = ownerProvider;
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<DownloadControlResponse> cancel(@PathVariable UUID id) {
        DownloadControlResult result = service.cancel(ownerProvider.currentOwner().id(), id);
        HttpStatus responseStatus = result.status().isTerminal()
                ? HttpStatus.OK
                : HttpStatus.ACCEPTED;
        return ResponseEntity.status(responseStatus).body(response(result));
    }

    @PostMapping("/{id}/retry")
    public DownloadControlResponse retry(@PathVariable UUID id) {
        return response(service.retry(ownerProvider.currentOwner().id(), id));
    }

    private DownloadControlResponse response(DownloadControlResult result) {
        return new DownloadControlResponse(
                result.jobId(), result.status().name(), result.cancellationRequested());
    }

    public record DownloadControlResponse(UUID id,
                                          String status,
                                          boolean cancellationRequested) {
    }
}
