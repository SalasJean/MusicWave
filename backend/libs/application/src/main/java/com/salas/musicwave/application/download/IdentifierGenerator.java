package com.salas.musicwave.application.download;

import java.util.UUID;

@FunctionalInterface
public interface IdentifierGenerator {

    UUID next();
}
