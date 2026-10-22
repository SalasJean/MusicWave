package com.salas.musicwave.domain.download;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

public record SourceUrl(String value, boolean youtube) {

    public static final int MAX_LENGTH = 2_048;

    private static final Set<String> YOUTUBE_HOSTS = Set.of(
            "youtube.com",
            "www.youtube.com",
            "m.youtube.com",
            "music.youtube.com",
            "youtu.be",
            "www.youtube-nocookie.com",
            "youtube-nocookie.com");

    public static SourceUrl parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidSourceUrlException("URL_EMPTY");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new InvalidSourceUrlException("URL_TOO_LONG");
        }
        if (trimmed.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidSourceUrlException("URL_CONTAINS_CONTROL_CHARACTERS");
        }

        URI uri;
        try {
            uri = new URI(trimmed).normalize();
        } catch (URISyntaxException exception) {
            throw new InvalidSourceUrlException("URL_MALFORMED");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new InvalidSourceUrlException("URL_SCHEME_NOT_ALLOWED");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidSourceUrlException("URL_HOST_REQUIRED");
        }
        if (uri.getUserInfo() != null) {
            throw new InvalidSourceUrlException("URL_USERINFO_NOT_ALLOWED");
        }

        String normalized = uri.toASCIIString();
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return new SourceUrl(normalized, YOUTUBE_HOSTS.contains(host));
    }

    public String fallbackTitle() {
        return value.length() > 60 ? value.substring(0, 57) + "..." : value;
    }
}
