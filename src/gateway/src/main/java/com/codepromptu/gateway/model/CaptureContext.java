package com.codepromptu.gateway.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
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
}
