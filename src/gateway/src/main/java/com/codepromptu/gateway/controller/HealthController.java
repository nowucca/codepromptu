package com.codepromptu.gateway.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {

    @Autowired
    private HealthIndicator redisHealthIndicator;

    @Value("${spring.cloud.config.uri:http://config:8888}")
    private String configServerUri;

    @Value("${spring.cloud.config.username:config}")
    private String configUsername;

    @Value("${spring.cloud.config.password:config123}")
    private String configPassword;

    private final WebClient webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
            .build();

    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "gateway");
        health.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        health.put("version", "1.0.0-SNAPSHOT");

        // Check Redis connectivity using our custom health indicator
        return checkRedisHealthUsingIndicator()
                .flatMap(redisStatus -> {
                    health.put("redis", redisStatus);
                    
                    // Check Configuration Server connectivity
                    return checkConfigServerHealth()
                            .map(configStatus -> {
                                health.put("configServer", configStatus);
                                
                                // Determine overall health - simplified logic
                                try {
                                    boolean redisHealthy = isHealthy(redisStatus);
                                    boolean configHealthy = isHealthy(configStatus);
                                    
                                    if (!redisHealthy || !configHealthy) {
                                        health.put("status", "DOWN");
                                    }
                                    
                                    return ResponseEntity.ok(health);
                                } catch (Exception e) {
                                    // Log the exception for debugging
                                    System.err.println("Error in health check logic: " + e.getMessage());
                                    e.printStackTrace();
                                    health.put("status", "DOWN");
                                    health.put("error", "Health logic error: " + e.getMessage());
                                    return ResponseEntity.ok(health);
                                }
                            });
                })
                .onErrorReturn(ResponseEntity.ok(addErrorStatus(health, "Health check failed")));
    }

    @GetMapping("/health/detailed")
    public Mono<ResponseEntity<Map<String, Object>>> detailedHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("service", "gateway");
        health.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        health.put("version", "1.0.0-SNAPSHOT");
        
        Map<String, Object> components = new HashMap<>();
        
        // Check Redis with detailed information using our custom health indicator
        return checkRedisHealthDetailedUsingIndicator()
                .flatMap(redisComponent -> {
                    components.put("redis", redisComponent);
                    
                    // Check Configuration Server with detailed information
                    return checkConfigServerHealthDetailed()
                            .map(configComponent -> {
                                components.put("configServer", configComponent);
                                
                                health.put("components", components);
                                
                                // Determine overall health
                                boolean redisHealthy = isHealthy(redisComponent);
                                boolean configHealthy = isHealthy(configComponent);
                                
                                health.put("status", (redisHealthy && configHealthy) ? "UP" : "DOWN");
                                
                                return ResponseEntity.ok(health);
                            });
                })
                .onErrorReturn(ResponseEntity.ok(addDetailedErrorStatus(health, components, "Detailed health check failed")));
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
        info.put("dependencies", Map.of(
                "cache", "Redis",
                "configServer", configServerUri
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

    private Mono<Map<String, Object>> checkRedisHealthUsingIndicator() {
        return Mono.fromCallable(() -> {
            try {
                var redisHealth = redisHealthIndicator.health();
                String status = redisHealth.getStatus().getCode();
                
                Map<String, Object> result = new HashMap<>();
                result.put("status", status);
                
                return result;
            } catch (Exception e) {
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("status", "DOWN");
                return errorResult;
            }
        });
    }

    private Mono<Map<String, Object>> checkRedisHealthDetailedUsingIndicator() {
        return Mono.fromCallable(() -> {
            try {
                var redisHealth = redisHealthIndicator.health();
                String status = redisHealth.getStatus().getCode();
                
                Map<String, Object> result = new HashMap<>();
                result.put("status", status);
                result.put("details", redisHealth.getDetails());
                
                return result;
            } catch (Exception e) {
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("status", "DOWN");
                
                Map<String, Object> errorDetails = new HashMap<>();
                errorDetails.put("error", "Redis health check failed: " + e.getMessage());
                errorResult.put("details", errorDetails);
                
                return errorResult;
            }
        });
    }

    private Mono<Map<String, Object>> checkConfigServerHealth() {
        String healthUrl = configServerUri + "/actuator/health";
        
        return webClient.get()
                .uri(healthUrl)
                .headers(headers -> {
                    if (configUsername != null && configPassword != null) {
                        headers.setBasicAuth(configUsername, configPassword);
                    }
                })
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(response -> {
                    String status = (String) response.get("status");
                    Map<String, Object> result = new HashMap<>();
                    result.put("status", "UP".equals(status) ? "UP" : "DEGRADED");
                    return result;
                })
                .timeout(Duration.ofSeconds(10))
                .onErrorResume(throwable -> {
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("status", "DOWN");
                    return Mono.just(errorResult);
                });
    }

    private Mono<Map<String, Object>> checkConfigServerHealthDetailed() {
        String healthUrl = configServerUri + "/actuator/health";
        
        return webClient.get()
                .uri(healthUrl)
                .headers(headers -> {
                    if (configUsername != null && configPassword != null) {
                        headers.setBasicAuth(configUsername, configPassword);
                    }
                })
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(response -> {
                    String status = (String) response.get("status");
                    Map<String, Object> result = new HashMap<>();
                    result.put("status", "UP".equals(status) ? "UP" : "DEGRADED");
                    
                    Map<String, Object> details = new HashMap<>();
                    details.put("url", healthUrl);
                    details.put("configServerStatus", status);
                    details.put("responseTime", "< 10s");
                    details.put("authentication", configUsername != null ? "enabled" : "disabled");
                    result.put("details", details);
                    
                    return result;
                })
                .timeout(Duration.ofSeconds(10))
                .onErrorResume(throwable -> {
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("status", "DOWN");
                    
                    Map<String, Object> errorDetails = new HashMap<>();
                    errorDetails.put("url", healthUrl);
                    errorDetails.put("error", "Config server connection failed or timeout");
                    errorDetails.put("timeout", "10s");
                    errorResult.put("details", errorDetails);
                    
                    return Mono.just(errorResult);
                });
    }

    private Map<String, Object> addErrorStatus(Map<String, Object> health, String error) {
        health.put("status", "DOWN");
        health.put("error", error);
        return health;
    }

    private Map<String, Object> addDetailedErrorStatus(Map<String, Object> health, Map<String, Object> components, String error) {
        health.put("status", "DOWN");
        health.put("components", components);
        health.put("error", error);
        return health;
    }
    
    private boolean isHealthy(Object statusObject) {
        try {
            if (statusObject instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> statusMap = (Map<String, Object>) statusObject;
                Object status = statusMap.get("status");
                return "UP".equals(status) || "DEGRADED".equals(status);
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
