package com.salas.musicwave.api.error;

import com.salas.musicwave.application.artifact.ArtifactMissingException;
import com.salas.musicwave.application.artifact.ArtifactNotReadyException;
import com.salas.musicwave.application.artifact.ArtifactStorageUnavailableException;
import com.salas.musicwave.application.artifact.ArtifactDeliveryInProgressException;
import com.salas.musicwave.application.artifact.ArtifactUnavailableException;
import com.salas.musicwave.application.download.DownloadNotFoundException;
import com.salas.musicwave.application.download.DownloadQuotaExceededException;
import com.salas.musicwave.application.download.IdempotencyConflictException;
import com.salas.musicwave.application.download.InvalidDownloadRequestException;
import com.salas.musicwave.application.execution.InvalidDownloadStateException;
import com.salas.musicwave.api.realtime.InvalidEventCursorException;
import com.salas.musicwave.api.realtime.RealtimeCapacityException;
import com.salas.musicwave.application.settings.InvalidUserSettingsException;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private final Clock clock;

    public ApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(InvalidDownloadRequestException.class)
    ProblemDetail invalidDownload(InvalidDownloadRequestException exception,
                                  HttpServletRequest request) {
        HttpStatus status = "IMPORT_TOO_LARGE".equals(exception.code())
                ? HttpStatus.PAYLOAD_TOO_LARGE
                : HttpStatus.BAD_REQUEST;
        return problem(status, exception.code(), exception.getMessage(), request);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ProblemDetail idempotencyConflict(IdempotencyConflictException exception,
                                      HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_KEY_REUSED",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(DownloadNotFoundException.class)
    ProblemDetail downloadNotFound(DownloadNotFoundException exception,
                                   HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "DOWNLOAD_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(DownloadQuotaExceededException.class)
    ProblemDetail downloadQuotaExceeded(DownloadQuotaExceededException exception,
                                        HttpServletRequest request) {
        ProblemDetail problem = problem(
                HttpStatus.CONFLICT,
                "DOWNLOAD_QUOTA_EXCEEDED",
                exception.getMessage(),
                request);
        problem.setProperty("limit", exception.limit());
        return problem;
    }

    @ExceptionHandler(InvalidDownloadStateException.class)
    ProblemDetail invalidDownloadState(InvalidDownloadStateException exception,
                                       HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, exception.code(), exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidUserSettingsException.class)
    ProblemDetail invalidSettings(InvalidUserSettingsException exception,
                                  HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, exception.code(), exception.getMessage(), request);
    }

    @ExceptionHandler(ArtifactNotReadyException.class)
    ProblemDetail artifactNotReady(ArtifactNotReadyException exception,
                                   HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "ARTIFACT_NOT_READY", exception.getMessage(), request);
    }

    @ExceptionHandler(ArtifactMissingException.class)
    ProblemDetail artifactMissing(ArtifactMissingException exception,
                                  HttpServletRequest request) {
        return problem(HttpStatus.GONE, "ARTIFACT_MISSING", exception.getMessage(), request);
    }

    @ExceptionHandler(ArtifactStorageUnavailableException.class)
    ProblemDetail artifactStorageUnavailable(ArtifactStorageUnavailableException exception,
                                             HttpServletRequest request) {
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "ARTIFACT_STORAGE_UNAVAILABLE",
                "Artifact storage is temporarily unavailable",
                request);
    }

    @ExceptionHandler(ArtifactDeliveryInProgressException.class)
    ProblemDetail artifactDeliveryInProgress(ArtifactDeliveryInProgressException exception,
                                             HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "ARTIFACT_DELIVERY_IN_PROGRESS",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(ArtifactUnavailableException.class)
    ProblemDetail artifactUnavailable(ArtifactUnavailableException exception,
                                      HttpServletRequest request) {
        return problem(HttpStatus.GONE, exception.code(), exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidEventCursorException.class)
    ProblemDetail invalidEventCursor(InvalidEventCursorException exception,
                                     HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "INVALID_EVENT_CURSOR",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(RealtimeCapacityException.class)
    ProblemDetail realtimeCapacity(RealtimeCapacityException exception,
                                   HttpServletRequest request) {
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "REALTIME_CAPACITY_EXHAUSTED",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception,
                             HttpServletRequest request) {
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Request validation failed",
                request);
        Map<String, String> violations = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                violations.putIfAbsent(error.getField(), error.getDefaultMessage()));
        problem.setProperty("violations", violations);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail malformedJson(HttpMessageNotReadableException exception,
                                HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "MALFORMED_JSON", "Request JSON is malformed", request);
    }

    @ExceptionHandler({MissingServletRequestPartException.class})
    ProblemDetail missingFile(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "IMPORT_FILE_REQUIRED", "TXT file part is required", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail oversizedUpload(MaxUploadSizeExceededException exception,
                                  HttpServletRequest request) {
        return problem(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "IMPORT_TOO_LARGE",
                "TXT import exceeds the configured upload limit",
                request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail typeMismatch(MethodArgumentTypeMismatchException exception,
                               HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER",
                "Request parameter has an invalid value",
                request);
    }

    private ProblemDetail problem(HttpStatus status,
                                  String code,
                                  String detail,
                                  HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", clock.instant());
        return problem;
    }
}
