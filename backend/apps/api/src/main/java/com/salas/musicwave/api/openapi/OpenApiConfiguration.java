package com.salas.musicwave.api.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI musicWaveOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MusicWave API")
                        .version("v1")
                        .description("Stable backend contract for MusicWave downloads, queue, settings and realtime events."))
                .components(new Components().addSecuritySchemes(
                        "bearer-jwt",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    @Bean
    OpenApiCustomizer protectedOperationsCustomizer() {
        return openApi -> openApi.getPaths().forEach((path, item) -> {
            if (!path.equals("/api/v1/system/status") && !path.equals("/api/v1/tools/status")) {
                item.readOperations().forEach(operation -> operation.addSecurityItem(
                        new SecurityRequirement().addList("bearer-jwt")));
            }
        });
    }
}
