package com.salas.musicwave.application.security;

import com.salas.musicwave.domain.download.SourceUrl;

@FunctionalInterface
public interface SourceUrlGuard {

    void assertAllowed(SourceUrl sourceUrl);

    static SourceUrlGuard permissive() {
        return ignored -> { };
    }
}
