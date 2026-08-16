package com.generationb.foundation.internal;

import com.generationb.foundation.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Q-E28: Render needs a health endpoint to decide whether a deploy succeeded, and to keep the
 * free-tier instance awake. Actuator would be the usual answer, but adding it is a new dependency
 * (Q-Z2), and this covers the requirement in a dozen lines.
 */
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("timestamp", Instant.now().toString());
        body.put("database", checkDatabase());
        return ApiResponse.of(body);
    }

    /** Liveness only — deliberately does not touch the database. */
    @GetMapping("/live")
    public ApiResponse<Map<String, Object>> live() {
        return ApiResponse.of(Map.of("status", "UP"));
    }

    private String checkDatabase() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return "UP";
        } catch (Exception e) {
            return "DOWN";
        }
    }
}
