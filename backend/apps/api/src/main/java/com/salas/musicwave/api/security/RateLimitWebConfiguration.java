package com.salas.musicwave.api.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class RateLimitWebConfiguration implements WebMvcConfigurer {

    private final MutationRateLimitInterceptor interceptor;

    public RateLimitWebConfiguration(MutationRateLimitInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/v1/downloads/**", "/api/v1/queue/**", "/api/v1/settings/**");
    }
}
