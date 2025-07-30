package com.codepromptu.api.controller;

import com.codepromptu.api.service.PromptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * Internal REST controller for system-to-system communication.
 * Used by the Gateway service to capture prompt usage data.
 */
@RestController
@RequestMapping("/internal")
@Validated
@Tag(name = "Internal", description = "Internal system operations")
public class InternalController {

    private static final Logger logger = LoggerFactory.getLogger(InternalController.class);

    private final PromptService promptService;

    @Autowired
    public InternalController(PromptService promptService) {
        this.promptService = promptService;
        logger.info("InternalController initialized");
    }

    /**
     * DTO for prompt usage capture from Gateway service
     */
    public static class PromptUsageDto {
        private String rawContent;
        private String provider;
        private String model;
        private Instant requestTimestamp;
        private Instant responseTimestamp;
        private String clientIp;
        private String userAgent;
        private String apiKeyHash;
        private Map<String, Object> metadata;

        // Constructors
        public PromptUsageDto() {}

        public PromptUsageDto(String rawContent, String provider, String model, 
                             Instant requestTimestamp, Instant responseTimestamp,
                             String clientIp, String userAgent, String apiKeyHash,
                             Map<String, Object> metadata) {
            this.rawContent = rawContent;
            this.provider = provider;
            this.model = model;
            this.requestTimestamp = requestTimestamp;
            this.responseTimestamp = responseTimestamp;
            this.clientIp = clientIp;
            this.userAgent = userAgent;
            this.apiKeyHash = apiKeyHash;
            this.metadata = metadata;
        }

        // Getters and setters
        public String getRawContent() { return rawContent; }
        public void setRawContent(String rawContent) { this.rawContent = rawContent; }

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public Instant getRequestTimestamp() { return requestTimestamp; }
        public void setRequestTimestamp(Instant requestTimestamp) { this.requestTimestamp = requestTimestamp; }

        public Instant getResponseTimestamp() { return responseTimestamp; }
        public void setResponseTimestamp(Instant responseTimestamp) { this.responseTimestamp = responseTimestamp; }

        public String getClientIp() { return clientIp; }
        public void setClientIp(String clientIp) { this.clientIp = clientIp; }

        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

        public String getApiKeyHash() { return apiKeyHash; }
        public void setApiKeyHash(String apiKeyHash) { this.apiKeyHash = apiKeyHash; }

        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }

    @Operation(summary = "Capture prompt usage", description = "Internal endpoint for capturing LLM prompt usage from Gateway")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Prompt usage captured successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid usage data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/prompt-usage")
    public ResponseEntity<Void> capturePromptUsage(
            @Parameter(description = "Prompt usage data") @Valid @RequestBody PromptUsageDto usage) {
        try {
            logger.debug("Capturing prompt usage: provider={}, model={}, contentLength={}", 
                        usage.getProvider(), usage.getModel(), 
                        usage.getRawContent() != null ? usage.getRawContent().length() : 0);

            // TODO: Implement actual prompt usage storage
            // For now, just log the capture
            logger.info("Prompt usage captured: provider={}, model={}, timestamp={}", 
                       usage.getProvider(), usage.getModel(), usage.getRequestTimestamp());

            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid prompt usage data: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Failed to capture prompt usage: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Health check", description = "Internal health check endpoint")
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> internalHealth() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "api-internal",
            "timestamp", Instant.now().toString()
        ));
    }
}
