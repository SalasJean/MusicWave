package com.salas.musicwave.infrastructure.tools;

import com.salas.musicwave.application.download.DownloadOutputEvent;
import com.salas.musicwave.application.execution.DownloadOutputParser;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class YtDlpOutputParser implements DownloadOutputParser {

    private static final Pattern PERCENT = Pattern.compile("(?<percent>\\d{1,3}(?:\\.\\d+)?)%");
    private static final Pattern SPEED = Pattern.compile("\\bat\\s+(?<speed>\\S+/s)");
    private static final Pattern ETA = Pattern.compile("\\bETA\\s+(?<eta>\\S+)");
    private static final Pattern DESTINATION = Pattern.compile(
            "^\\[(?:download|ExtractAudio)]\\s+Destination:\\s+(?<path>.+)$");
    private static final Pattern ALREADY_DOWNLOADED = Pattern.compile(
            "^\\[download]\\s+(?<path>.+) has already been downloaded$");
    private static final Pattern STRUCTURED_PROGRESS = Pattern.compile(
            "^musicwave_progress:\\s*(?<percent>\\d{1,3}(?:\\.\\d+)?)%?\\|(?<speed>[^|]*)\\|(?<eta>.*)$");
    private static final Pattern FINAL_ARTIFACT = Pattern.compile(
            "^musicwave_artifact:(?<path>.+)$");

    public List<DownloadOutputEvent> parse(String line) {
        if (line == null || line.isBlank()) {
            return List.of();
        }

        List<DownloadOutputEvent> events = new ArrayList<>();
        Matcher structuredProgress = STRUCTURED_PROGRESS.matcher(line);
        if (structuredProgress.matches()) {
            BigDecimal value = new BigDecimal(structuredProgress.group("percent"));
            if (value.compareTo(BigDecimal.valueOf(100)) <= 0) {
                events.add(new DownloadOutputEvent.Progress(
                        value,
                        structuredProgress.group("speed").trim(),
                        structuredProgress.group("eta").trim()));
            }
            return List.copyOf(events);
        }
        Matcher finalArtifact = FINAL_ARTIFACT.matcher(line);
        if (finalArtifact.matches()) {
            String path = finalArtifact.group("path").trim();
            events.add(new DownloadOutputEvent.DestinationDetected(path, titleFrom(path)));
            return List.copyOf(events);
        }
        if (line.startsWith("ERROR:")) {
            String message = line.substring("ERROR:".length()).trim();
            events.add(new DownloadOutputEvent.FailureDetected(
                    message.isBlank() ? "yt-dlp reported an unspecified error" : message));
            return List.copyOf(events);
        }

        if (line.startsWith("[ExtractAudio]")) {
            events.add(new DownloadOutputEvent.ConversionStarted());
        }

        Matcher destination = DESTINATION.matcher(line);
        if (destination.matches()) {
            String path = destination.group("path").trim();
            events.add(new DownloadOutputEvent.DestinationDetected(path, titleFrom(path)));
        } else {
            Matcher alreadyDownloaded = ALREADY_DOWNLOADED.matcher(line);
            if (alreadyDownloaded.matches()) {
                String path = alreadyDownloaded.group("path").trim();
                events.add(new DownloadOutputEvent.DestinationDetected(path, titleFrom(path)));
            }
        }

        if (line.startsWith("[download]")) {
            Matcher percent = PERCENT.matcher(line);
            if (percent.find()) {
                BigDecimal value = new BigDecimal(percent.group("percent"));
                if (value.compareTo(BigDecimal.valueOf(100)) <= 0) {
                    events.add(new DownloadOutputEvent.Progress(
                            value,
                            findGroup(SPEED, line, "speed"),
                            findGroup(ETA, line, "eta")));
                }
            }
        }
        return List.copyOf(events);
    }

    private String findGroup(Pattern pattern, String line, String group) {
        Matcher matcher = pattern.matcher(line);
        return matcher.find() ? matcher.group(group) : "";
    }

    private String titleFrom(String path) {
        int separator = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String filename = separator >= 0 ? path.substring(separator + 1) : path;
        int extension = filename.lastIndexOf('.');
        return extension > 0 ? filename.substring(0, extension) : filename;
    }
}
