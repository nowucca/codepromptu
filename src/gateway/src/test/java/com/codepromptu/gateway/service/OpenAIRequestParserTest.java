package com.codepromptu.gateway.service;

import com.codepromptu.gateway.model.CaptureContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAIRequestParserTest {
    
    private OpenAIRequestParser parser;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        parser = new OpenAIRequestParser(objectMapper);
    }
    
    @Test
    void shouldParseChatCompletionRequest() {
        // Given
        String requestBody = """
            {
                "model": "gpt-4",
                "messages": [
                    {"role": "system", "content": "You are a helpful assistant."},
                    {"role": "user", "content": "Hello, world!"}
                ],
                "temperature": 0.7,
                "max_tokens": 150
            }
            """;
        
        CaptureContext.CaptureContextBuilder builder = CaptureContext.builder();
        
        // When
        CaptureContext.CaptureContextBuilder result = parser.parseRequest(builder, requestBody);
        CaptureContext context = result.build();
        
        // Then
        assertThat(context.getModel()).isEqualTo("gpt-4");
        assertThat(context.getTemperature()).isEqualTo(0.7);
        assertThat(context.getMaxTokens()).isEqualTo(150);
        assertThat(context.getSystemPrompt()).isEqualTo("You are a helpful assistant.");
        assertThat(context.getUserPrompt()).isEqualTo("Hello, world!");
    }
    
    @Test
    void shouldParseLegacyCompletionRequest() {
        // Given
        String requestBody = """
            {
                "model": "gpt-3.5-turbo-instruct",
                "prompt": "Complete this sentence: The weather today is",
                "temperature": 0.5,
                "max_tokens": 50
            }
            """;
        
        CaptureContext.CaptureContextBuilder builder = CaptureContext.builder();
        
        // When
        CaptureContext.CaptureContextBuilder result = parser.parseRequest(builder, requestBody);
        CaptureContext context = result.build();
        
        // Then
        assertThat(context.getModel()).isEqualTo("gpt-3.5-turbo-instruct");
        assertThat(context.getTemperature()).isEqualTo(0.5);
        assertThat(context.getMaxTokens()).isEqualTo(50);
        assertThat(context.getUserPrompt()).isEqualTo("Complete this sentence: The weather today is");
    }
    
    @Test
    void shouldParseMultipleMessages() {
        // Given
        String requestBody = """
            {
                "model": "gpt-4",
                "messages": [
                    {"role": "system", "content": "You are a coding assistant."},
                    {"role": "user", "content": "How do I create a REST API?"},
                    {"role": "assistant", "content": "You can use Spring Boot..."},
                    {"role": "user", "content": "Can you show me an example?"}
                ]
            }
            """;
        
        CaptureContext.CaptureContextBuilder builder = CaptureContext.builder();
        
        // When
        CaptureContext.CaptureContextBuilder result = parser.parseRequest(builder, requestBody);
        CaptureContext context = result.build();
        
        // Then
        assertThat(context.getSystemPrompt()).isEqualTo("You are a coding assistant.");
        assertThat(context.getUserPrompt()).contains("How do I create a REST API?");
        assertThat(context.getUserPrompt()).contains("[Assistant]: You can use Spring Boot...");
        assertThat(context.getUserPrompt()).contains("Can you show me an example?");
    }
    
    @Test
    void shouldParseAdditionalParameters() {
        // Given
        String requestBody = """
            {
                "model": "gpt-4",
                "messages": [
                    {"role": "user", "content": "Test message"}
                ],
                "top_p": 0.9,
                "frequency_penalty": 0.1,
                "presence_penalty": 0.2,
                "stream": true
            }
            """;
        
        CaptureContext.CaptureContextBuilder builder = CaptureContext.builder();
        
        // When
        CaptureContext.CaptureContextBuilder result = parser.parseRequest(builder, requestBody);
        CaptureContext context = result.build();
        
        // Then
        assertThat(context.getAdditionalParams()).containsEntry("top_p", 0.9);
        assertThat(context.getAdditionalParams()).containsEntry("frequency_penalty", 0.1);
        assertThat(context.getAdditionalParams()).containsEntry("presence_penalty", 0.2);
        assertThat(context.getAdditionalParams()).containsEntry("stream", true);
    }
    
    @Test
    void shouldHandleMinimalRequest() {
        // Given
        String requestBody = """
            {
                "messages": [
                    {"role": "user", "content": "Simple test"}
                ]
            }
            """;
        
        CaptureContext.CaptureContextBuilder builder = CaptureContext.builder();
        
        // When
        CaptureContext.CaptureContextBuilder result = parser.parseRequest(builder, requestBody);
        CaptureContext context = result.build();
        
        // Then
        assertThat(context.getModel()).isEqualTo("gpt-3.5-turbo"); // Default model
        assertThat(context.getUserPrompt()).isEqualTo("Simple test");
        assertThat(context.getTemperature()).isNull();
        assertThat(context.getMaxTokens()).isNull();
    }
    
    @Test
    void shouldHandleInvalidJson() {
        // Given
        String requestBody = "{ invalid json }";
        
        CaptureContext.CaptureContextBuilder builder = CaptureContext.builder();
        
        // When
        CaptureContext.CaptureContextBuilder result = parser.parseRequest(builder, requestBody);
        CaptureContext context = result.build();
        
        // Then - Should not crash, just return builder without modifications
        assertThat(context.getModel()).isNull();
        assertThat(context.getUserPrompt()).isNull();
    }
    
    @Test
    void shouldParseResponse() {
        // Given
        String responseBody = """
            {
                "id": "chatcmpl-123",
                "object": "chat.completion",
                "created": 1677652288,
                "model": "gpt-4",
                "choices": [
                    {
                        "index": 0,
                        "message": {
                            "role": "assistant",
                            "content": "Hello! How can I help you today?"
                        },
                        "finish_reason": "stop"
                    }
                ],
                "usage": {
                    "prompt_tokens": 10,
                    "completion_tokens": 9,
                    "total_tokens": 19
                }
            }
            """;
        
        CaptureContext context = CaptureContext.builder().build();
        
        // When
        parser.parseResponse(context, responseBody);
        
        // Then
        assertThat(context.getTokensInput()).isEqualTo(10);
        assertThat(context.getTokensOutput()).isEqualTo(9);
        assertThat(context.getResponseBody()).isEqualTo(responseBody);
        assertThat(context.getStatus()).isEqualTo("success");
    }
    
    @Test
    void shouldHandleResponseWithoutUsage() {
        // Given
        String responseBody = """
            {
                "id": "chatcmpl-123",
                "object": "chat.completion",
                "created": 1677652288,
                "model": "gpt-4",
                "choices": [
                    {
                        "index": 0,
                        "message": {
                            "role": "assistant",
                            "content": "Response without usage info"
                        },
                        "finish_reason": "stop"
                    }
                ]
            }
            """;
        
        CaptureContext context = CaptureContext.builder().build();
        
        // When
        parser.parseResponse(context, responseBody);
        
        // Then
        assertThat(context.getTokensInput()).isNull();
        assertThat(context.getTokensOutput()).isNull();
        assertThat(context.getResponseBody()).isEqualTo(responseBody);
        assertThat(context.getStatus()).isEqualTo("success");
    }
}
