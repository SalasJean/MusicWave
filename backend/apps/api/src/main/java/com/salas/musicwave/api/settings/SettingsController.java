package com.salas.musicwave.api.settings;

import com.salas.musicwave.api.security.CurrentOwnerProvider;
import com.salas.musicwave.application.settings.UserSettings;
import com.salas.musicwave.application.settings.UserSettingsService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final UserSettingsService service;
    private final CurrentOwnerProvider ownerProvider;

    public SettingsController(UserSettingsService service, CurrentOwnerProvider ownerProvider) {
        this.service = service;
        this.ownerProvider = ownerProvider;
    }

    @GetMapping
    public SettingsResponse get() {
        return response(service.get(ownerProvider.currentOwner()));
    }

    @PutMapping
    public SettingsResponse update(@Valid @RequestBody UpdateSettingsRequest request) {
        return response(service.update(
                ownerProvider.currentOwner(), request.maxConcurrency(), request.audioQuality()));
    }

    private SettingsResponse response(UserSettings settings) {
        return new SettingsResponse(settings.maxConcurrency(), settings.audioQuality());
    }

    public record UpdateSettingsRequest(
            @Min(1) @Max(10) int maxConcurrency,
            @NotBlank String audioQuality) {
    }

    public record SettingsResponse(int maxConcurrency, String audioQuality) {
    }
}
