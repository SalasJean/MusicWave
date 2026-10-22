package com.salas.musicwave.domain.download;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceUrlTest {

    @Test
    void normalizesAndClassifiesYouTubeHostsExactly() {
        SourceUrl youtube = SourceUrl.parse("  https://www.youtube.com/watch?v=abc  ");
        SourceUrl lookalike = SourceUrl.parse("https://youtube.com.evil.example/watch?v=abc");

        assertEquals("https://www.youtube.com/watch?v=abc", youtube.value());
        assertTrue(youtube.youtube());
        assertFalse(lookalike.youtube());
    }

    @Test
    void acceptsHttpSourcesButRejectsUnsafeOrMalformedForms() {
        assertEquals("http://example.com/audio", SourceUrl.parse("http://example.com/audio").value());
        assertEquals("URL_SCHEME_NOT_ALLOWED", assertThrows(
                InvalidSourceUrlException.class,
                () -> SourceUrl.parse("file:///tmp/song.mp3")).reason());
        assertEquals("URL_USERINFO_NOT_ALLOWED", assertThrows(
                InvalidSourceUrlException.class,
                () -> SourceUrl.parse("https://user:password@example.com/video")).reason());
        assertEquals("URL_MALFORMED", assertThrows(
                InvalidSourceUrlException.class,
                () -> SourceUrl.parse("https://example.com/a path")).reason());
    }

    @Test
    void createsTheSameSixtyCharacterFallbackAsTheLegacyApplication() {
        SourceUrl url = SourceUrl.parse("https://example.com/this/is/a/very/long/path/that/keeps/going/forever/song");

        assertEquals(60, url.fallbackTitle().length());
        assertTrue(url.fallbackTitle().endsWith("..."));
    }
}
