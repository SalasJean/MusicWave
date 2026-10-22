package com.salas.musicwave.api.download;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.salas.musicwave.api.security.CurrentOwnerProvider;
import com.salas.musicwave.application.download.CreateDownloadsCommand;
import com.salas.musicwave.application.download.CreateDownloadsResult;
import com.salas.musicwave.application.download.CreateDownloadsService;
import com.salas.musicwave.application.download.DownloadJobPage;
import com.salas.musicwave.application.download.DownloadQueryService;
import com.salas.musicwave.application.download.DownloadRequestType;
import com.salas.musicwave.application.download.InvalidDownloadRequestException;
import com.salas.musicwave.application.download.RawUrlInput;
import com.salas.musicwave.application.download.RejectedUrl;
import com.salas.musicwave.domain.download.DownloadJob;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/downloads")
public class DownloadController {

    static final int MAX_IMPORT_BYTES = 262_144;

    private final CreateDownloadsService createService;
    private final DownloadQueryService queryService;
    private final CurrentOwnerProvider ownerProvider;

    public DownloadController(CreateDownloadsService createService,
                              DownloadQueryService queryService,
                              CurrentOwnerProvider ownerProvider) {
        this.createService = createService;
        this.queryService = queryService;
        this.ownerProvider = ownerProvider;
    }

    @PostMapping
    public ResponseEntity<CreateDownloadsResponse> createOne(
            @Valid @RequestBody SingleDownloadRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        CreateDownloadsResult result = createService.create(new CreateDownloadsCommand(
                ownerProvider.currentOwner(),
                DownloadRequestType.SINGLE,
                idempotencyKey,
                List.of(new RawUrlInput(1, request.url()))));
        return createdResponse(result, true);
    }

    @PostMapping("/bulk")
    public ResponseEntity<CreateDownloadsResponse> createBulk(
            @Valid @RequestBody BulkDownloadRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        List<RawUrlInput> inputs = new ArrayList<>(request.urls().size());
        for (int index = 0; index < request.urls().size(); index++) {
            inputs.add(new RawUrlInput(index + 1, request.urls().get(index)));
        }
        CreateDownloadsResult result = createService.create(new CreateDownloadsCommand(
                ownerProvider.currentOwner(), DownloadRequestType.BULK, idempotencyKey, inputs));
        return createdResponse(result, false);
    }

    @PostMapping(path = "/import", consumes = "multipart/form-data")
    public ResponseEntity<CreateDownloadsResponse> importText(
            @RequestPart("file") MultipartFile file,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        List<RawUrlInput> inputs = decodeImport(file);
        CreateDownloadsResult result = createService.create(new CreateDownloadsCommand(
                ownerProvider.currentOwner(), DownloadRequestType.IMPORT, idempotencyKey, inputs));
        return createdResponse(result, false);
    }

    @GetMapping
    public DownloadPageResponse list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        DownloadJobPage result = queryService.list(ownerProvider.currentOwner().id(), page, size);
        return new DownloadPageResponse(
                result.items().stream().map(this::jobResponse).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    @GetMapping("/{id}")
    public DownloadJobResponse get(@PathVariable UUID id) {
        return jobResponse(queryService.get(ownerProvider.currentOwner().id(), id));
    }

    private ResponseEntity<CreateDownloadsResponse> createdResponse(
            CreateDownloadsResult result, boolean includeLocation) {
        CreateDownloadsResponse body = new CreateDownloadsResponse(
                result.requestId(),
                result.replayed(),
                result.jobs().size(),
                result.rejected().size(),
                result.jobs().stream().map(this::jobResponse).toList(),
                result.rejected().stream().map(this::rejectedResponse).toList());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(
                result.replayed() ? HttpStatus.OK : HttpStatus.CREATED);
        if (includeLocation && !result.jobs().isEmpty()) {
            response.location(URI.create("/api/v1/downloads/" + result.jobs().get(0).id()));
        }
        return response.body(body);
    }

    private List<RawUrlInput> decodeImport(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidDownloadRequestException("IMPORT_EMPTY", "TXT import must not be empty");
        }
        if (file.getSize() > MAX_IMPORT_BYTES) {
            throw new InvalidDownloadRequestException(
                    "IMPORT_TOO_LARGE", "TXT import must not exceed " + MAX_IMPORT_BYTES + " bytes");
        }
        try {
            byte[] bytes = file.getBytes();
            CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            String text = decoded.toString();
            if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
                text = text.substring(1);
            }
            String[] lines = text.split("\\R", -1);
            List<RawUrlInput> inputs = new ArrayList<>();
            for (int index = 0; index < lines.length; index++) {
                if (!lines[index].isBlank()) {
                    inputs.add(new RawUrlInput(index + 1, lines[index]));
                }
            }
            return inputs;
        } catch (CharacterCodingException exception) {
            throw new InvalidDownloadRequestException(
                    "IMPORT_NOT_UTF8", "TXT import must contain valid UTF-8 text");
        } catch (IOException exception) {
            throw new InvalidDownloadRequestException(
                    "IMPORT_READ_FAILED", "TXT import could not be read");
        }
    }

    private DownloadJobResponse jobResponse(DownloadJob job) {
        JobErrorResponse error = job.errorCode() == null && job.errorMessage() == null
                ? null
                : new JobErrorResponse(job.errorCode(), job.errorMessage());
        return new DownloadJobResponse(
                job.id(),
                job.sourceUrl(),
                job.title(),
                job.titleStatus().name(),
                job.status().name(),
                job.progress(),
                job.speed(),
                job.eta(),
                error,
                job.retryCount(),
                job.createdAt(),
                job.updatedAt(),
                job.startedAt(),
                job.completedAt());
    }

    private RejectedUrlResponse rejectedResponse(RejectedUrl rejected) {
        return new RejectedUrlResponse(
                rejected.position(), rejected.value(), rejected.code(), rejected.message());
    }

    public record SingleDownloadRequest(@NotBlank String url) {
    }

    public record BulkDownloadRequest(@NotNull List<String> urls) {
    }

    public record CreateDownloadsResponse(UUID requestId,
                                          boolean replayed,
                                          int createdCount,
                                          int rejectedCount,
                                          List<DownloadJobResponse> jobs,
                                          List<RejectedUrlResponse> rejected) {
    }

    public record RejectedUrlResponse(int position, String value, String code, String message) {
    }

    public record DownloadPageResponse(List<DownloadJobResponse> items,
                                       int page,
                                       int size,
                                       long totalElements,
                                       int totalPages) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DownloadJobResponse(UUID id,
                                      String sourceUrl,
                                      String title,
                                      String titleStatus,
                                      String status,
                                      java.math.BigDecimal progress,
                                      String speed,
                                      String eta,
                                      JobErrorResponse error,
                                      int retryCount,
                                      @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
                                      @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt,
                                      @JsonFormat(shape = JsonFormat.Shape.STRING) Instant startedAt,
                                      @JsonFormat(shape = JsonFormat.Shape.STRING) Instant completedAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JobErrorResponse(String code, String message) {
    }
}
