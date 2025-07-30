package com.codepromptu.gateway.service;

import com.codepromptu.gateway.model.LLMProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Service for detecting LLM providers based on request patterns.
 * Analyzes request paths, headers, and other characteristics to identify
 * which LLM provider the request is intended for.
 */
@Service
public class LLMProviderDetector {
    
    // Path patterns for different providers
    private static final Pattern OPENAI_CHAT_PATTERN = Pattern.compile("^/v1/chat/completions$");
    private static final Pattern OPENAI_COMPLETIONS_PATTERN = Pattern.compile("^/v1/completions$");
    private static final Pattern OPENAI_EMBEDDINGS_PATTERN = Pattern.compile("^/v1/embeddings$");
    
    private static final Pattern ANTHROPIC_MESSAGES_PATTERN = Pattern.compile("^/v1/messages$");
    private static final Pattern ANTHROPIC_COMPLETE_PATTERN = Pattern.compile("^/v1/complete$");
    
    private static final Pattern GOOGLE_AI_PATTERN = Pattern.compile("^/v1beta/models/.*/generateContent$");
    
    /**
     * Detects the LLM provider based on request characteristics.
     * 
     * @param exchange the server web exchange containing request details
     * @return the detected LLM provider
     */
    public LLMProvider detectProvider(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        HttpHeaders headers = exchange.getRequest().getHeaders();
        
        // Check for OpenAI patterns
        if (isOpenAIRequest(path, headers)) {
            return LLMProvider.OPENAI;
        }
        
        // Check for Anthropic patterns
        if (isAnthropicRequest(path, headers)) {
            return LLMProvider.ANTHROPIC;
        }
        
        // Check for Google AI patterns
        if (isGoogleAIRequest(path, headers)) {
            return LLMProvider.GOOGLE_AI;
        }
        
        return LLMProvider.UNKNOWN;
    }
    
    /**
     * Extracts API key from request headers based on provider type.
     * 
     * @param exchange the server web exchange
     * @param provider the detected LLM provider
     * @return the extracted API key, or null if not found
     */
    public String extractApiKey(ServerWebExchange exchange, LLMProvider provider) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        
        switch (provider) {
            case OPENAI:
                return extractBearerToken(headers.getFirst("Authorization"));
                
            case ANTHROPIC:
                return headers.getFirst("x-api-key");
                
            case GOOGLE_AI:
                String apiKey = headers.getFirst("x-goog-api-key");
                if (apiKey == null) {
                    // Google AI also supports API key in query parameter
                    Map<String, String> queryParams = exchange.getRequest().getQueryParams().toSingleValueMap();
                    apiKey = queryParams.get("key");
                }
                return apiKey;
                
            default:
                return null;
        }
    }
    
    /**
     * Gets the target URI for the detected provider.
     * 
     * @param provider the LLM provider
     * @return the base URI for the provider's API
     */
    public String getProviderBaseUri(LLMProvider provider) {
        switch (provider) {
            case OPENAI:
                return "https://api.openai.com";
                
            case ANTHROPIC:
                return "https://api.anthropic.com";
                
            case GOOGLE_AI:
                return "https://generativelanguage.googleapis.com";
                
            default:
                return null;
        }
    }
    
    /**
     * Checks if the request is intended for OpenAI.
     */
    private boolean isOpenAIRequest(String path, HttpHeaders headers) {
        // Check path patterns
        boolean pathMatches = OPENAI_CHAT_PATTERN.matcher(path).matches() ||
                             OPENAI_COMPLETIONS_PATTERN.matcher(path).matches() ||
                             OPENAI_EMBEDDINGS_PATTERN.matcher(path).matches();
        
        if (!pathMatches) {
            return false;
        }
        
        // Check for Authorization header with Bearer token
        String authHeader = headers.getFirst("Authorization");
        return authHeader != null && authHeader.startsWith("Bearer ");
    }
    
    /**
     * Checks if the request is intended for Anthropic.
     */
    private boolean isAnthropicRequest(String path, HttpHeaders headers) {
        // Check path patterns
        boolean pathMatches = ANTHROPIC_MESSAGES_PATTERN.matcher(path).matches() ||
                             ANTHROPIC_COMPLETE_PATTERN.matcher(path).matches();
        
        if (!pathMatches) {
            return false;
        }
        
        // Check for x-api-key header
        return headers.containsKey("x-api-key");
    }
    
    /**
     * Checks if the request is intended for Google AI.
     */
    private boolean isGoogleAIRequest(String path, HttpHeaders headers) {
        // Check path pattern
        boolean pathMatches = GOOGLE_AI_PATTERN.matcher(path).matches();
        
        if (!pathMatches) {
            return false;
        }
        
        // Check for API key in header or will be in query param
        return headers.containsKey("x-goog-api-key") || path.contains("generateContent");
    }
    
    /**
     * Extracts bearer token from Authorization header.
     */
    private String extractBearerToken(String authHeader) {
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7); // Remove "Bearer " prefix
        }
        return null;
    }
    
    /**
     * Validates that the API key format is reasonable for the provider.
     * This is a basic validation to catch obvious errors.
     */
    public boolean isValidApiKeyFormat(String apiKey, LLMProvider provider) {
        if (!StringUtils.hasText(apiKey)) {
            return false;
        }
        
        switch (provider) {
            case OPENAI:
                // OpenAI keys typically start with "sk-" and are 51 characters
                return apiKey.startsWith("sk-") && apiKey.length() >= 20;
                
            case ANTHROPIC:
                // Anthropic keys typically start with "sk-ant-" 
                return apiKey.startsWith("sk-ant-") && apiKey.length() >= 20;
                
            case GOOGLE_AI:
                // Google AI keys are typically 39 characters
                return apiKey.length() >= 20 && apiKey.length() <= 50;
                
            default:
                return apiKey.length() >= 10; // Basic length check
        }
    }
    
    /**
     * Gets additional headers required for the provider.
     */
    public Map<String, String> getRequiredHeaders(LLMProvider provider) {
        switch (provider) {
            case ANTHROPIC:
                return Map.of(
                    "anthropic-version", "2023-06-01",
                    "content-type", "application/json"
                );
                
            case GOOGLE_AI:
                return Map.of(
                    "content-type", "application/json"
                );
                
            case OPENAI:
            default:
                return Map.of(
                    "content-type", "application/json"
                );
        }
    }
}
