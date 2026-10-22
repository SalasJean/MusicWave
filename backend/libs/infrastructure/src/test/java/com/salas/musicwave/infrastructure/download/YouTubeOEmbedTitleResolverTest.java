package com.salas.musicwave.infrastructure.download;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salas.musicwave.application.download.TitleResolution;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YouTubeOEmbedTitleResolverTest {

    private HttpServer server;
    private final AtomicInteger responseStatus = new AtomicInteger(200);

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oembed", exchange -> {
            int status = responseStatus.get();
            byte[] body = (status == 200 ? "{\"title\":\"Music & Waves\"}" : "failure")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void decodesTheOEmbedTitle() {
        TitleResolution result = resolver().resolve("https://www.youtube.com/watch?v=a&list=b");

        assertEquals(TitleResolution.Outcome.RESOLVED, result.outcome());
        assertEquals("Music & Waves", result.title());
    }

    @Test
    void classifiesServerErrorsAsRetryableAndNotFoundAsPermanent() {
        responseStatus.set(503);
        TitleResolution retryable = resolver().resolve("https://youtu.be/abc");
        responseStatus.set(404);
        TitleResolution unavailable = resolver().resolve("https://youtu.be/abc");

        assertEquals(TitleResolution.Outcome.RETRYABLE_FAILURE, retryable.outcome());
        assertTrue(retryable.detail().contains("503"));
        assertEquals(TitleResolution.Outcome.UNAVAILABLE, unavailable.outcome());
    }

    private YouTubeOEmbedTitleResolver resolver() {
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/oembed");
        return new YouTubeOEmbedTitleResolver(
                HttpClient.newHttpClient(), new ObjectMapper(), endpoint, Duration.ofSeconds(2));
    }
}
