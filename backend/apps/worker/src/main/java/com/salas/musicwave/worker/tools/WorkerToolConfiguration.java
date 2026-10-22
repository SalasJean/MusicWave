package com.salas.musicwave.worker.tools;

import com.salas.musicwave.application.process.ExternalProcessRunner;
import com.salas.musicwave.application.tools.ToolInspector;
import com.salas.musicwave.infrastructure.process.JvmExternalProcessRunner;
import com.salas.musicwave.infrastructure.tools.CommandToolInspector;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class WorkerToolConfiguration {

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    ExternalProcessRunner externalProcessRunner(
            @Value("${musicwave.process.max-captured-characters:262144}") int maxCapturedCharacters) {
        return new JvmExternalProcessRunner(maxCapturedCharacters);
    }

    @Bean
    ToolInspector toolInspector(
            ExternalProcessRunner processRunner,
            @Value("${musicwave.tools.yt-dlp-command:yt-dlp}") String ytDlpCommand,
            @Value("${musicwave.tools.ffmpeg-command:ffmpeg}") String ffmpegCommand,
            @Value("${musicwave.tools.probe-timeout:PT10S}") Duration probeTimeout) {
        return new CommandToolInspector(processRunner, ytDlpCommand, ffmpegCommand, probeTimeout);
    }
}
