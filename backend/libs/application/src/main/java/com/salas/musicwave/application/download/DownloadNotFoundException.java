package com.salas.musicwave.application.download;

import java.util.UUID;

public class DownloadNotFoundException extends RuntimeException {

    public DownloadNotFoundException(UUID id) {
        super("Download job not found: " + id);
    }
}
