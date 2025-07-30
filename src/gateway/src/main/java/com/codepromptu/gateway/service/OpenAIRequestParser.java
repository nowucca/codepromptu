package com.codepromptu.gateway.service;

import com.codepromptu.gateway.model.CaptureContext;
import com.codepromptu.gateway.model.LLMProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAIRequestParser {
    
    private final ObjectMapper objectMapper;
    
    public CaptureContext.CaptureContextBuilder parseRequest(CaptureContext.CaptureContextBuilder builder, String requestBody) {
        try {
            JsonNode request = objectMapper.readTree(requestBody);
            
            // Extract model
            String model = request.path("model").asText("gpt-3.5-turbo");
            builder.model(model);
            
            // Extract temperature
            if (request.has("temperature")) {
                builder.temperature(request.get("temperature").asDouble());
            }
            
            // Extract max_tokens
            if (request.has("max_tokens")) {
                builder.maxTokens(request.get("max_tokens").asInt());
            }
            
            // Extract messages and parse prompts
            if (request.has("messages")) {
                parseMessages(builder, request.get("messages"));
            } else if (request.has("prompt")) {
                // Legacy completions API
                builder.userPrompt(request.get("prompt").asText());
            }
            
            // Extract additional parameters
            Map<String, Object> additionalParams = new HashMap<>();
            if (request.has("top_p")) {
                additionalParams.put("top_p", request.get("top_p").asDouble());
            }
            if (request.has("frequency_penalty")) {
                additionalParams.put("frequency_penalty", request.get("frequency_penalty").asDouble());
            }
            if (request.has("presence_penalty")) {
                additionalParams.put("presence_penalty", request.get("presence_penalty").asDouble());
            }
            if (request.has("stream")) {
                additionalParams.put("stream", request.get("stream").asBoolean());
            }
            
            builder.additionalParams(additionalParams);
            
        } catch (Exception e) {
            log.error("Failed to parse OpenAI request: {}", e.getMessage(), e);
            // Continue with basic parsing - don't fail the request
        }
        
        return builder;
    }
    
    private void parseMessages(CaptureContext.CaptureContextBuilder builder, JsonNode messages) {
        StringBuilder systemPrompt = new StringBuilder();
        StringBuilder userPrompt = new StringBuilder();
        
        for (JsonNode message : messages) {
            String role = message.path("role").asText();
            String content = message.path("content").asText();
            
            switch (role.toLowerCase()) {
                case "system":
                    if (systemPrompt.length() > 0) {
                        systemPrompt.append("\n");
                    }
                    systemPrompt.append(content);
                    break;
                case "user":
                    if (userPrompt.length() > 0) {
                        userPrompt.append("\n");
                    }
                    userPrompt.append(content);
                    break;
                case "assistant":
                    // For conversation context, we might want to include assistant messages
                    if (userPrompt.length() > 0) {
                        userPrompt.append("\n[Assistant]: ");
                    }
                    userPrompt.append(content);
                    break;
                default:
                    log.debug("Unknown message role: {}", role);
            }
        }
        
        if (systemPrompt.length() > 0) {
            builder.systemPrompt(systemPrompt.toString());
        }
        if (userPrompt.length() > 0) {
            builder.userPrompt(userPrompt.toString());
        }
    }
    
    public void parseResponse(CaptureContext context, String responseBody) {
        try {
            JsonNode response = objectMapper.readTree(responseBody);
            
            // Extract usage information
            if (response.has("usage")) {
                JsonNode usage = response.get("usage");
                if (usage.has("prompt_tokens")) {
                    context.setTokensInput(usage.get("prompt_tokens").asInt());
                }
                if (usage.has("completion_tokens")) {
                    context.setTokensOutput(usage.get("completion_tokens").asInt());
                }
            }
            
            // Store full response body
            context.setResponseBody(responseBody);
            context.setStatus("success");
            
        } catch (Exception e) {
            log.error("Failed to parse OpenAI response: {}", e.getMessage(), e);
            context.setStatus("parse_error");
        }
    }
}
