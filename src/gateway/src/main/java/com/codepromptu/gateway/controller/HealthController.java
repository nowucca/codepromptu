package com.codepromptu.gateway.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "gateway");
        health.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        health.put("version", "1.0.0-SNAPSHOT");

        // Check Redis connectivity
        return redisTemplate.opsForValue()
                .get("health_check")
                .map(value -> {
                    health.put("redis", "UP");
                    return ResponseEntity.ok(health);
                })
                .onErrorReturn(ResponseEntity.ok(addRedisDown(health)))
                .switchIfEmpty(Mono.fromCallable(() -> {
                    health.put("redis", "UP");
                    return ResponseEntity.ok(health);
                }));
    }

    @GetMapping("/health/detailed")
    public Mono<ResponseEntity<Map<String, Object>>> detailedHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "gateway");
        health.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        health.put("version", "1.0.0-SNAPSHOT");
        
        Map<String, Object> components = new HashMap<>();
        
        // Check Redis
        return redisTemplate.opsForValue()
                .get("health_check")
                .map(value -> {
                    components.put("redis", Map.of("status", "UP", "details", "Connected"));
                    health.put("components", components);
                    return ResponseEntity.ok(health);
                })
                .onErrorReturn(ResponseEntity.ok(addDetailedRedisDown(health, components)))
                .switchIfEmpty(Mono.fromCallable(() -> {
                    components.put("redis", Map.of("status", "UP", "details", "Connected"));
                    health.put("components", components);
                    return ResponseEntity.ok(health);
                }));
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        Map<String, Object> info = new HashMap<>();
        info.put("app", Map.of(
                "name", "CodePromptu Gateway",
                "description", "API Gateway service for CodePromptu",
                "version", "1.0.0-SNAPSHOT"
        ));
        info.put("build", Map.of(
                "time", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "artifact", "gateway",
                "group", "com.codepromptu"
        ));
        info.put("git", Map.of(
                "branch", "main",
                "commit", Map.of(
                        "id", "unknown",
                        "time", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                )
        ));
        
        return ResponseEntity.ok(info);
    }

    private Map<String, Object> addRedisDown(Map<String, Object> health) {
        health.put("redis", "DOWN");
        health.put("status", "DEGRADED");
        return health;
    }

    private Map<String, Object> addDetailedRedisDown(Map<String, Object> health, Map<String, Object> components) {
        components.put("redis", Map.of("status", "DOWN", "details", "Connection failed"));
        health.put("components", components);
        health.put("status", "DEGRADED");
        return health;
    }
}
