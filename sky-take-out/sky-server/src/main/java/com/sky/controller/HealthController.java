package com.sky.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RedisTemplate redisTemplate;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("service", "sky-server");
        return result;
    }

    @GetMapping("/ready")
    public Map<String, Object> ready() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            redisTemplate.hasKey("health:probe");
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "A required dependency is unavailable",
                    exception
            );
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ready");
        result.put("service", "sky-server");
        result.put("dependencies_checked", true);
        return result;
    }
}
