package com.salas.musicwave.application.download;

import java.math.BigDecimal;
import java.util.Objects;

public sealed interface DownloadOutputEvent permits DownloadOutputEvent.Progress,
        DownloadOutputEvent.ConversionStarted,
        DownloadOutputEvent.DestinationDetected,
        DownloadOutputEvent.FailureDetected {

    record Progress(BigDecimal percent, String speed, String eta) implements DownloadOutputEvent {
        public Progress {
            Objects.requireNonNull(percent, "percent");
            if (percent.signum() < 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("percent must be between 0 and 100");
            }
            speed = Objects.requireNonNullElse(speed, "");
            eta = Objects.requireNonNullElse(eta, "");
        }
    }

    record ConversionStarted() implements DownloadOutputEvent {
    }

    record DestinationDetected(String path, String title) implements DownloadOutputEvent {
        public DestinationDetected {
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("path must not be blank");
            }
            title = Objects.requireNonNullElse(title, "");
        }
    }

    record FailureDetected(String message) implements DownloadOutputEvent {
        public FailureDetected {
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("message must not be blank");
            }
        }
    }
}
