package com.salas.musicwave.application.download;

@FunctionalInterface
public interface MediaTitleResolver {

    TitleResolution resolve(String sourceUrl);
}
