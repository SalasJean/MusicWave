package com.salas.musicwave.application.execution;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class YtDlpCommandFactory {

    private final String ytDlpCommand;
    private final String ffmpegLocation;
    private final String audioQuality;

    public YtDlpCommandFactory(String ytDlpCommand,
                               String ffmpegLocation,
                               String audioQuality) {
        this.ytDlpCommand = requireText(ytDlpCommand, "ytDlpCommand");
        this.ffmpegLocation = requireText(ffmpegLocation, "ffmpegLocation");
        this.audioQuality = requireText(audioQuality, "audioQuality");
        if (!audioQuality.matches("(?:[0-9]{2,3}K|[0-9])")) {
            throw new IllegalArgumentException("audioQuality must be a yt-dlp quality value");
        }
    }

    public List<String> create(QueuedDownloadWork work, Path workspace) {
        List<String> command = new ArrayList<>();
        command.add(ytDlpCommand);
        if (!"ffmpeg".equals(ffmpegLocation)) {
            command.add("--ffmpeg-location");
            command.add(ffmpegLocation);
        }
        command.addAll(List.of(
                "--js-runtimes", "deno",
                "--format", "bestaudio/best",
                "--extract-audio",
                "--audio-format", "mp3",
                "--audio-quality", work.audioQuality() == null ? audioQuality : work.audioQuality(),
                "--paths", workspace.toAbsolutePath().normalize().toString(),
                "--output", "%(title).180B [%(id)s].%(ext)s",
                "--no-playlist",
                "--no-simulate",
                "--progress",
                "--newline",
                "--no-color",
                "--progress-template",
                "download:musicwave_progress:%(progress._percent_str)s|%(progress._speed_str)s|%(progress._eta_str)s",
                "--print", "after_move:musicwave_artifact:%(filepath)s",
                work.sourceUrl()));
        return List.copyOf(command);
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
