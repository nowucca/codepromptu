package com.codepromptu.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaptureContext {
    
    private String requestId;
    private String conversationId;
    private LLMProvider provider;
    private String requestBody;
    private String model;
    private String apiKey;
    private String apiKeyHash;
    private Instant requestTime;
    private String clientIp;
    private String userAgent;
    private String path;
    private Map<String, String> headers;
    
    // Parsed request data
    private String systemPrompt;
    private String userPrompt;
    private Double temperature;
    private Integer maxTokens;
    private Map<String, Object> additionalParams;
    
    // Response data (populated later)
    private String responseBody;
    private Integer tokensInput;
    private Integer tokensOutput;
    private String status;
    private Long latencyMs;
    
    // Convenience method for setting metadata
    public void setMetadata(Map<String, Object> metadata) {
        this.additionalParams = metadata;
    }
    
    public Map<String, Object> getMetadata() {
        return this.additionalParams;
    }
}
