package com.salas.musicwave.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;

final class SecurityProblemWriter {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    SecurityProblemWriter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    void write(HttpServletRequest request,
               HttpServletResponse response,
               int status,
               String title,
               String code,
               String detail) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", title);
        problem.put("status", status);
        problem.put("detail", detail);
        problem.put("instance", request.getRequestURI());
        problem.put("code", code);
        problem.put("timestamp", clock.instant());
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
