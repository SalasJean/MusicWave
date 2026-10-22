package com.salas.musicwave.application.download;

import java.util.UUID;

public record OwnerIdentity(UUID id, String externalSubject) {

    public OwnerIdentity {
        if (id == null) {
            throw new IllegalArgumentException("owner id is required");
        }
        if (externalSubject == null || externalSubject.isBlank()) {
            throw new IllegalArgumentException("externalSubject is required");
        }
    }
}
