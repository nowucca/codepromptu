package com.codepromptu.api.controller;

import com.codepromptu.api.service.EmbeddingIndexService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import com.codepromptu.api.config.CustomRedisHealthIndicator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CustomRedisHealthIndicator redisHealthIndicator;

    @Autowired
    private EmbeddingIndexService embeddingIndexService;

    @Value("${spring.cloud.config.uri:http://config:8888}")
    private String configServerUri;

    @Value("${spring.cloud.config.username:config}")
    private String configUsername;

    @Value("${spring.cloud.config.password:config123}")
    private String configPassword;

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping({"/health", "/actuator/health"})
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        Map<String, Object> components = new HashMap<>();
        boolean allHealthy = true;

        health.put("service", "api");
        health.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        health.put("version", "1.0.0-SNAPSHOT");

        // Check Database connectivity
        try {
            try (Connection connection = dataSource.getConnection()) {
                if (connection.isValid(5)) {
                    components.put("database", Map.of(
                        "status", "UP",
                        "details", Map.of(
                            "database", connection.getMetaData().getDatabaseProductName(),
                            "validationQuery", "Connection validated successfully"
                        )
                    ));
                } else {
                    components.put("database", Map.of(
                        "status", "DOWN",
                        "details", Map.of("error", "Connection validation failed")
                    ));
                    allHealthy = false;
                }
            }
        } catch (Exception e) {
            components.put("database", Map.of(
                "status", "DOWN",
                "details", Map.of("error", "Database connection failed: " + e.getMessage())
            ));
            allHealthy = false;
        }

        // Check Redis connectivity using our custom health indicator
        try {
            var redisHealth = redisHealthIndicator.health();
            String status = redisHealth.getStatus().getCode();
            
            Map<String, Object> redisComponent = new HashMap<>();
            redisComponent.put("status", status);
            redisComponent.put("details", redisHealth.getDetails());
            
            components.put("redis", redisComponent);
            
            if (!"UP".equals(status)) {
                allHealthy = false;
            }
        } catch (Exception e) {
            components.put("redis", Map.of(
                "status", "DOWN",
                "details", Map.of("error", "Redis health check failed: " + e.getMessage())
            ));
            allHealthy = false;
        }

        // Check Configuration Server connectivity
        try {
            String healthUrl = configServerUri + "/actuator/health";
            ResponseEntity<Map> configResponse = restTemplate.getForEntity(healthUrl, Map.class);
            
            if (configResponse.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> configHealth = configResponse.getBody();
                String configStatus = (String) configHealth.get("status");
                
                components.put("configServer", Map.of(
                    "status", "UP".equals(configStatus) ? "UP" : "DEGRADED",
                    "details", Map.of(
                        "url", healthUrl,
                        "configServerStatus", configStatus,
                        "responseTime", "< 5s"
                    )
                ));
                
                if (!"UP".equals(configStatus)) {
                    allHealthy = false;
                }
            } else {
                components.put("configServer", Map.of(
                    "status", "DOWN",
                    "details", Map.of(
                        "url", healthUrl,
                        "error", "Config server returned status: " + configResponse.getStatusCode()
                    )
                ));
                allHealthy = false;
            }
        } catch (Exception e) {
            components.put("configServer", Map.of(
                "status", "DOWN",
                "details", Map.of(
                    "url", configServerUri + "/actuator/health",
                    "error", "Config server connection failed: " + e.getMessage()
                )
            ));
            allHealthy = false;
        }

        health.put("components", components);
        health.put("status", allHealthy ? "UP" : "DOWN");

        return ResponseEntity.ok(health);
    }

    @GetMapping("/health/simple")
    public ResponseEntity<Map<String, Object>> simpleHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "api");
        health.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return ResponseEntity.ok(health);
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        Map<String, Object> info = new HashMap<>();
        info.put("app", Map.of(
                "name", "CodePromptu API Service",
                "description", "REST API service for CodePromptu",
                "version", "1.0.0-SNAPSHOT"
        ));
        info.put("build", Map.of(
                "time", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "artifact", "api",
                "group", "com.codepromptu"
        ));
        info.put("dependencies", Map.of(
                "database", "PostgreSQL",
                "cache", "Redis",
                "configServer", configServerUri
        ));
        
        return ResponseEntity.ok(info);
    }

    @GetMapping("/health/embedding-index")
    public ResponseEntity<Map<String, Object>> embeddingIndexHealth() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            EmbeddingIndexService.EmbeddingIndexStats stats = embeddingIndexService.getIndexStats();
            
            response.put("status", stats.isOptimal() ? "UP" : "DEGRADED");
            response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            response.put("stats", Map.of(
                "promptsWithEmbeddings", stats.getPromptsWithEmbeddings(),
                "indexExists", stats.isIndexExists(),
                "indexLists", stats.getIndexLists(),
                "minPromptsForIndex", stats.getMinPromptsForIndex(),
                "shouldHaveIndex", stats.shouldHaveIndex(),
                "isOptimal", stats.isOptimal()
            ));
            
            // Add recommendations
            if (!stats.isOptimal()) {
                if (stats.shouldHaveIndex() && !stats.isIndexExists()) {
                    response.put("recommendation", "Index should be created - sufficient data available");
                } else if (!stats.shouldHaveIndex() && stats.isIndexExists()) {
                    response.put("recommendation", "Index should be removed - insufficient data");
                }
            } else {
                response.put("recommendation", "Index configuration is optimal");
            }
            
        } catch (Exception e) {
            response.put("status", "DOWN");
            response.put("error", "Failed to get embedding index stats: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        
        return ResponseEntity.ok(response);
    }
}
