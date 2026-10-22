package com.salas.musicwave.api.settings;

import com.salas.musicwave.application.settings.UserSettingsRepository;
import com.salas.musicwave.application.settings.UserSettingsService;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SettingsConfiguration {

    @Bean
    UserSettingsService userSettingsService(UserSettingsRepository repository, Clock clock) {
        return new UserSettingsService(repository, clock);
    }
}
