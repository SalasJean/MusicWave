package com.salas.musicwave.application.download;

import com.salas.musicwave.domain.download.DownloadJob;
import com.salas.musicwave.domain.download.InvalidSourceUrlException;
import com.salas.musicwave.domain.download.SourceUrl;
import com.salas.musicwave.application.security.SourceUrlGuard;
import com.salas.musicwave.application.security.UnsafeSourceUrlException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public final class CreateDownloadsService {

    public static final int MAX_URLS_PER_REQUEST = 100;
    public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 255;

    private final DownloadJobRepository repository;
    private final IdentifierGenerator identifierGenerator;
    private final Clock clock;
    private final SourceUrlGuard sourceUrlGuard;

    public CreateDownloadsService(DownloadJobRepository repository,
                                  IdentifierGenerator identifierGenerator,
                                  Clock clock) {
        this(repository, identifierGenerator, clock, SourceUrlGuard.permissive());
    }

    public CreateDownloadsService(DownloadJobRepository repository,
                                  IdentifierGenerator identifierGenerator,
                                  Clock clock,
                                  SourceUrlGuard sourceUrlGuard) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.identifierGenerator = Objects.requireNonNull(identifierGenerator, "identifierGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sourceUrlGuard = Objects.requireNonNull(sourceUrlGuard, "sourceUrlGuard");
    }

    public CreateDownloadsResult create(CreateDownloadsCommand command) {
        validateCommand(command);
        String idempotencyKey = normalizeIdempotencyKey(command.idempotencyKey());
        List<AcceptedInput> accepted = new ArrayList<>();
        List<RejectedUrl> rejected = new ArrayList<>();

        for (RawUrlInput input : command.inputs()) {
            String raw = input.value();
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                SourceUrl sourceUrl = SourceUrl.parse(raw);
                sourceUrlGuard.assertAllowed(sourceUrl);
                accepted.add(new AcceptedInput(input.position(), sourceUrl));
            } catch (InvalidSourceUrlException | UnsafeSourceUrlException exception) {
                String reason = exception instanceof InvalidSourceUrlException invalid
                        ? invalid.reason()
                        : ((UnsafeSourceUrlException) exception).reason();
                rejected.add(new RejectedUrl(
                        input.position(),
                        raw.trim(),
                        reason,
                        messageFor(reason)));
            }
        }

        if (command.requestType() == DownloadRequestType.SINGLE && !rejected.isEmpty()) {
            RejectedUrl invalid = rejected.get(0);
            throw new InvalidDownloadRequestException(invalid.code(), invalid.message());
        }
        if (accepted.isEmpty()) {
            throw new InvalidDownloadRequestException(
                    "NO_VALID_URLS", "The request does not contain a valid HTTP(S) URL");
        }

        Instant now = clock.instant();
        var requestId = identifierGenerator.next();
        List<DownloadJob> jobs = new ArrayList<>(accepted.size());
        for (int index = 0; index < accepted.size(); index++) {
            jobs.add(DownloadJob.pending(
                    identifierGenerator.next(),
                    command.owner().id(),
                    requestId,
                    index,
                    accepted.get(index).sourceUrl(),
                    now));
        }

        String fingerprint = fingerprint(command.requestType(), accepted, rejected);
        PersistedDownloadBatch persisted = repository.create(new NewDownloadBatch(
                requestId,
                command.owner(),
                command.requestType(),
                idempotencyKey,
                fingerprint,
                now,
                jobs));
        return new CreateDownloadsResult(
                persisted.requestId(), persisted.replayed(), persisted.jobs(), rejected);
    }

    private void validateCommand(CreateDownloadsCommand command) {
        if (command.inputs().isEmpty()) {
            throw new InvalidDownloadRequestException("URLS_REQUIRED", "At least one URL is required");
        }
        if (command.inputs().size() > MAX_URLS_PER_REQUEST) {
            throw new InvalidDownloadRequestException(
                    "TOO_MANY_URLS",
                    "A request may contain at most " + MAX_URLS_PER_REQUEST + " URL entries");
        }
        if (command.requestType() == DownloadRequestType.SINGLE && command.inputs().size() != 1) {
            throw new InvalidDownloadRequestException(
                    "SINGLE_URL_REQUIRED", "Single download creation requires exactly one URL");
        }
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new InvalidDownloadRequestException(
                    "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must not be blank");
        }
        if (normalized.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new InvalidDownloadRequestException(
                    "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must not exceed " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
        return normalized;
    }

    private String fingerprint(DownloadRequestType requestType,
                               List<AcceptedInput> accepted,
                               List<RejectedUrl> rejected) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(requestType.name().getBytes(StandardCharsets.UTF_8));
            Stream.concat(
                            accepted.stream().map(input -> new FingerprintInput(
                                    input.position(), "A", input.sourceUrl().value())),
                            rejected.stream().map(input -> new FingerprintInput(
                                    input.position(), "R", input.value())))
                    .sorted(java.util.Comparator.comparingInt(FingerprintInput::position))
                    .forEach(input -> updateDigest(
                            digest, input.type(), input.position() + ":" + input.value()));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private void updateDigest(MessageDigest digest, String type, String value) {
        digest.update((byte) 0);
        digest.update(type.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(value.getBytes(StandardCharsets.UTF_8));
    }

    private String messageFor(String reason) {
        return switch (reason) {
            case "URL_TOO_LONG" -> "URL exceeds " + SourceUrl.MAX_LENGTH + " characters";
            case "URL_CONTAINS_CONTROL_CHARACTERS" -> "URL contains control characters";
            case "URL_SCHEME_NOT_ALLOWED" -> "Only HTTP and HTTPS URLs are accepted";
            case "URL_HOST_REQUIRED" -> "URL must contain a host";
            case "URL_USERINFO_NOT_ALLOWED" -> "URL credentials are not accepted";
            case "URL_DESTINATION_NOT_ALLOWED" -> "URL destination is not publicly routable";
            case "URL_HOST_UNRESOLVED" -> "URL host could not be resolved safely";
            default -> "URL is malformed";
        };
    }

    private record AcceptedInput(int position, SourceUrl sourceUrl) {
    }

    private record FingerprintInput(int position, String type, String value) {
    }
}
