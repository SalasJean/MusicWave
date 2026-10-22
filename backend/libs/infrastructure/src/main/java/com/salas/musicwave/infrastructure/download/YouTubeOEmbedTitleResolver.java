package com.salas.musicwave.infrastructure.download;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salas.musicwave.application.download.MediaTitleResolver;
import com.salas.musicwave.application.download.TitleResolution;
import com.salas.musicwave.domain.download.InvalidSourceUrlException;
import com.salas.musicwave.domain.download.SourceUrl;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

public final class YouTubeOEmbedTitleResolver implements MediaTitleResolver {

    private static final int MAX_RESPONSE_CHARACTERS = 32_768;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final Duration requestTimeout;

    public YouTubeOEmbedTitleResolver(HttpClient httpClient,
                                     ObjectMapper objectMapper,
                                     URI endpoint,
                                     Duration requestTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    @Override
    public TitleResolution resolve(String sourceUrl) {
        SourceUrl parsed;
        try {
            parsed = SourceUrl.parse(sourceUrl);
        } catch (InvalidSourceUrlException exception) {
            return TitleResolution.unavailable("Source URL is no longer valid");
        }
        if (!parsed.youtube()) {
            return TitleResolution.unavailable("Title lookup is only available for YouTube URLs");
        }

        URI requestUri = URI.create(endpoint + "?url="
                + URLEncoder.encode(parsed.value(), StandardCharsets.UTF_8)
                + "&format=json");
        HttpRequest request = HttpRequest.newBuilder(requestUri)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("User-Agent", "MusicWave/0.1")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status == 200) {
                if (response.body().length() > MAX_RESPONSE_CHARACTERS) {
                    return TitleResolution.unavailable("oEmbed response exceeded the size limit");
                }
                JsonNode body = objectMapper.readTree(response.body());
                String title = body.path("title").asText("").trim();
                return title.isBlank()
                        ? TitleResolution.unavailable("oEmbed response did not contain a title")
                        : TitleResolution.resolved(title);
            }
            if (status == 404 || (status >= 400 && status < 500 && status != 408 && status != 429)) {
                return TitleResolution.unavailable("YouTube oEmbed rejected the URL with HTTP " + status);
            }
            return TitleResolution.retryableFailure("YouTube oEmbed returned HTTP " + status);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return TitleResolution.retryableFailure("Title lookup was interrupted");
        } catch (IOException | IllegalArgumentException exception) {
            return TitleResolution.retryableFailure("Title lookup failed: " + exception.getMessage());
        }
    }
}
