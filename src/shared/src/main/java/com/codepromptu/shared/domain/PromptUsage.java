package com.codepromptu.shared.domain;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;

import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing every captured LLM API call with prompt usage tracking.
 * Records the complete context of prompt usage for analysis and evaluation.
 * 
 * This is now a simple POJO without JPA/Hibernate annotations for use with JDBC Template.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PromptUsage {

    @EqualsAndHashCode.Include
    private UUID id;

    /**
     * Reference to the prompt that was used (if matched to existing prompt).
     */
    private Prompt prompt;

    /**
     * Reference to the template that was matched (if applicable).
     */
    private PromptTemplate template;

    /**
     * Conversation/session identifier for grouping related API calls.
     */
    private UUID conversationId;

    /**
     * The raw prompt content as sent to the LLM API.
     */
    private String rawContent;

    /**
     * Extracted variables if matched to a template.
     * Stored as JSON for flexible variable structures.
     */
    @Builder.Default
    private JsonNode variables = null;

    /**
     * Timestamp when the API request was received.
     */
    private Instant requestTimestamp;

    /**
     * Timestamp when the API response was sent.
     */
    private Instant responseTimestamp;

    /**
     * Number of input tokens in the request.
     */
    private Integer tokensInput;

    /**
     * Number of output tokens in the response.
     */
    private Integer tokensOutput;

    /**
     * LLM model used for this request (e.g., "gpt-4", "claude-3-opus").
     */
    private String modelUsed;

    /**
     * LLM provider (e.g., "openai", "anthropic", "azure").
     */
    private String provider;

    /**
     * Status of the API call (e.g., "success", "error", "timeout").
     */
    private String status;

    /**
     * LLM response content (may be truncated for large responses).
     */
    private String responseContent;

    /**
     * Response latency in milliseconds.
     */
    private Integer latencyMs;

    /**
     * Client IP address for usage tracking.
     */
    private InetAddress clientIp;

    /**
     * User agent string from the client.
     */
    private String userAgent;

    /**
     * Hash of the client's API key for tracking without storing the key.
     */
    private String apiKeyHash;

    /**
     * Additional metadata about the request context.
     */
    @Builder.Default
    private JsonNode requestMetadata = null;

    /**
     * Error message if the request failed.
     */
    private String errorMessage;

    /**
     * HTTP status code of the response.
     */
    private Integer httpStatus;

    /**
     * Evaluation records for this usage.
     */
    @Builder.Default
    private List<PromptEvaluation> evaluations = new ArrayList<>();

    /**
     * Calculate the total latency from request to response.
     */
    public Long calculateLatency() {
        if (requestTimestamp != null && responseTimestamp != null) {
            return responseTimestamp.toEpochMilli() - requestTimestamp.toEpochMilli();
        }
        return latencyMs != null ? latencyMs.longValue() : null;
    }

    /**
     * Check if this usage was successful.
     */
    public boolean isSuccessful() {
        return "success".equalsIgnoreCase(status) || 
               (httpStatus != null && httpStatus >= 200 && httpStatus < 300);
    }

    /**
     * Check if this usage resulted in an error.
     */
    public boolean isError() {
        return "error".equalsIgnoreCase(status) || 
               (httpStatus != null && httpStatus >= 400);
    }

    /**
     * Get the total token count (input + output).
     */
    public Integer getTotalTokens() {
        int input = tokensInput != null ? tokensInput : 0;
        int output = tokensOutput != null ? tokensOutput : 0;
        return input + output;
    }

    /**
     * Calculate cost estimate based on token usage and model pricing.
     * This is a simplified calculation - real pricing would be more complex.
     */
    public Double estimateCost() {
        if (tokensInput == null || modelUsed == null) {
            return null;
        }
        
        // Simplified pricing (would be configurable in real implementation)
        double inputCostPer1K = switch (modelUsed.toLowerCase()) {
            case "gpt-4" -> 0.03;
            case "gpt-3.5-turbo" -> 0.001;
            case "claude-3-opus" -> 0.015;
            case "claude-3-sonnet" -> 0.003;
            default -> 0.001; // Default rate
        };
        
        double outputCostPer1K = inputCostPer1K * 2; // Output typically costs 2x input
        
        double inputCost = (tokensInput / 1000.0) * inputCostPer1K;
        double outputCost = ((tokensOutput != null ? tokensOutput : 0) / 1000.0) * outputCostPer1K;
        
        return inputCost + outputCost;
    }

    /**
     * Check if this usage has extracted variables from a template.
     */
    public boolean hasVariables() {
        return variables != null && !variables.isNull() && variables.size() > 0;
    }

    /**
     * Get a summary description of this usage.
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(modelUsed != null ? modelUsed : "unknown model");
        
        if (isSuccessful()) {
            summary.append(" - SUCCESS");
        } else if (isError()) {
            summary.append(" - ERROR");
        } else {
            summary.append(" - ").append(status != null ? status.toUpperCase() : "UNKNOWN");
        }
        
        if (getTotalTokens() > 0) {
            summary.append(" (").append(getTotalTokens()).append(" tokens)");
        }
        
        if (calculateLatency() != null) {
            summary.append(" [").append(calculateLatency()).append("ms]");
        }
        
        return summary.toString();
    }

    /**
     * Mark this usage as completed with response details.
     */
    public void markCompleted(String responseContent, Integer tokensOut, Integer httpStatus) {
        this.responseTimestamp = Instant.now();
        this.responseContent = responseContent;
        this.tokensOutput = tokensOut;
        this.httpStatus = httpStatus;
        this.status = (httpStatus != null && httpStatus >= 200 && httpStatus < 300) ? "success" : "error";
        
        if (this.requestTimestamp != null) {
            this.latencyMs = (int) (this.responseTimestamp.toEpochMilli() - this.requestTimestamp.toEpochMilli());
        }
    }

    /**
     * Mark this usage as failed with error details.
     */
    public void markFailed(String errorMessage, Integer httpStatus) {
        this.responseTimestamp = Instant.now();
        this.errorMessage = errorMessage;
        this.httpStatus = httpStatus;
        this.status = "error";
        
        if (this.requestTimestamp != null) {
            this.latencyMs = (int) (this.responseTimestamp.toEpochMilli() - this.requestTimestamp.toEpochMilli());
        }
    }

    /**
     * Create a new usage record for an API call.
     */
    public static PromptUsage create(String rawContent, String modelUsed, String provider, 
                                   Integer tokensInput, UUID conversationId) {
        return PromptUsage.builder()
                .rawContent(rawContent)
                .modelUsed(modelUsed)
                .provider(provider)
                .tokensInput(tokensInput)
                .conversationId(conversationId)
                .status("pending")
                .requestTimestamp(Instant.now())
                .build();
    }
}
