package com.salas.musicwave.api.download;

import com.salas.musicwave.api.security.CurrentOwnerProvider;
import com.salas.musicwave.application.artifact.ArtifactDownload;
import com.salas.musicwave.application.artifact.GetArtifactService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/downloads")
public class ArtifactController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtifactController.class);

    private final GetArtifactService service;
    private final CurrentOwnerProvider ownerProvider;

    public ArtifactController(GetArtifactService service, CurrentOwnerProvider ownerProvider) {
        this.service = service;
        this.ownerProvider = ownerProvider;
    }

    @GetMapping("/{id}/artifact")
    @Operation(
            summary = "Download and consume a completed MP3 artifact",
            description = "Streams the temporary MP3 once. A completed transfer schedules immediate permanent deletion.")
    @ApiResponse(
            responseCode = "200",
            description = "MP3 stream",
            content = @Content(
                    mediaType = "audio/mpeg",
                    schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<StreamingResponseBody> get(@PathVariable UUID id) {
        ArtifactDownload artifact = service.get(ownerProvider.currentOwner().id(), id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(artifact.filename(), StandardCharsets.UTF_8)
                .build();
        StreamingResponseBody body = output -> {
            boolean transferred = false;
            try (var input = artifact.inputStream()) {
                input.transferTo(output);
                output.flush();
                transferred = true;
            } finally {
                try {
                    if (transferred) {
                        if (!service.completeDelivery(artifact)) {
                            LOGGER.warn("Completed artifact transfer awaits reconciled deletion jobId={}", id);
                        }
                    } else {
                        service.abortDelivery(artifact);
                    }
                } catch (RuntimeException exception) {
                    LOGGER.error("Artifact delivery finalization will rely on lease recovery jobId={}",
                            id, exception);
                }
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(artifact.contentType()))
                .contentLength(artifact.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .header(HttpHeaders.ACCEPT_RANGES, "none")
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Content-SHA256", artifact.sha256())
                .body(body);
    }
}
