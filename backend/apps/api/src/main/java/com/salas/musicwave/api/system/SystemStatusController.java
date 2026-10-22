package com.salas.musicwave.api.system;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemStatusController {

    private final Clock clock;
    private final String version;

    public SystemStatusController(Clock clock,
                                  @Value("${musicwave.version:unknown}") String version) {
        this.clock = clock;
        this.version = version;
    }

    @GetMapping("/status")
    public SystemStatusResponse status() {
        return new SystemStatusResponse(
                "musicwave-api",
                "UP",
                version,
                clock.instant());
    }

    public record SystemStatusResponse(
            String service,
            String status,
            String version,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            Instant timestamp) {
    }
}
