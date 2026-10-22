package com.salas.musicwave.api.tools;

import com.salas.musicwave.application.tools.ToolStatusQueryService;
import com.salas.musicwave.application.tools.WorkerToolInventory;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ApiToolConfiguration {

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    ToolStatusQueryService toolStatusQueryService(
            WorkerToolInventory inventory,
            Clock clock,
            @Value("${musicwave.tools.heartbeat-max-age:PT30S}") Duration heartbeatMaxAge) {
        return new ToolStatusQueryService(inventory, clock, heartbeatMaxAge);
    }
}
