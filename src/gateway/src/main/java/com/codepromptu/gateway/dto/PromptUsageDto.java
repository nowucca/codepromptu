package com.codepromptu.gateway.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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
public class PromptUsageDto {
    
    private String conversationId;
    private String rawContent;
    private String provider;
    private String model;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    private Instant requestTimestamp;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    private Instant responseTimestamp;
    
    private Integer tokensInput;
    private Integer tokensOutput;
    private String status;
    private String responseContent;
    private Long latencyMs;
    private String clientIp;
    private String userAgent;
    private String apiKeyHash;
    
    // Additional metadata
    private Map<String, Object> metadata;
    
    // Request details
    private Double temperature;
    private Integer maxTokens;
    private String systemPrompt;
    private String userPrompt;
}
