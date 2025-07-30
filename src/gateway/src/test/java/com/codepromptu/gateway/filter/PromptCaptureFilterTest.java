package com.codepromptu.gateway.filter;

import com.codepromptu.gateway.model.CaptureContext;
import com.codepromptu.gateway.model.LLMProvider;
import com.codepromptu.gateway.service.OpenAIRequestParser;
import com.codepromptu.gateway.service.PromptCaptureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.atLeastOnce;

@ExtendWith(MockitoExtension.class)
class PromptCaptureFilterTest {
    
    @Mock
    private PromptCaptureService promptCaptureService;
    
    @Mock
    private OpenAIRequestParser openAIRequestParser;
    
    @Mock
    private GatewayFilterChain chain;
    
    private PromptCaptureFilter filter;
    
    @BeforeEach
    void setUp() {
        filter = new PromptCaptureFilter(promptCaptureService, openAIRequestParser);
    }
    
    @Test
    void shouldCaptureOpenAIRequest() {
        // Given
        String requestBody = """
            {
                "model": "gpt-4",
                "messages": [
                    {"role": "user", "content": "Hello, world!"}
                ]
            }
            """;
            
        DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap(requestBody.getBytes(StandardCharsets.UTF_8));
        
        MockServerHttpRequest request = MockServerHttpRequest.post("/v1/chat/completions")
            .header("Authorization", "Bearer sk-test-key")
            .header("Content-Type", "application/json")
            .body(Flux.just(dataBuffer));
            
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        when(promptCaptureService.hashApiKey("sk-test-key")).thenReturn("hashed-key");
        when(promptCaptureService.generateConversationId(any(CaptureContext.class))).thenReturn("conv-123");
        when(promptCaptureService.capturePromptUsage(any(CaptureContext.class), any(ServerHttpResponse.class)))
            .thenReturn(Mono.empty());
        when(openAIRequestParser.parseRequest(any(), anyString())).thenReturn(CaptureContext.builder());
        
        GatewayFilter gatewayFilter = filter.apply(new PromptCaptureFilter.Config());
        
        // When & Then
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
            .verifyComplete();
            
        verify(promptCaptureService).capturePromptUsage(any(CaptureContext.class), any(ServerHttpResponse.class));
        verify(openAIRequestParser).parseRequest(any(), eq(requestBody));
    }
    
    @Test
    void shouldSkipNonLLMRequests() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/health").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(chain.filter(exchange)).thenReturn(Mono.empty());
        
        GatewayFilter gatewayFilter = filter.apply(new PromptCaptureFilter.Config());
        
        // When & Then
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
            .verifyComplete();
            
        verify(promptCaptureService, never()).capturePromptUsage(any(), any());
        verify(chain).filter(exchange);
    }
    
    @Test
    void shouldHandleStorageFailureGracefully() {
        // Given
        String requestBody = """
            {
                "model": "gpt-4",
                "messages": [
                    {"role": "user", "content": "Test prompt"}
                ]
            }
            """;
            
        DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap(requestBody.getBytes(StandardCharsets.UTF_8));
        
        MockServerHttpRequest request = MockServerHttpRequest.post("/v1/chat/completions")
            .header("Authorization", "Bearer sk-test-key")
            .body(Flux.just(dataBuffer));
            
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        when(promptCaptureService.hashApiKey(anyString())).thenReturn("hashed-key");
        when(promptCaptureService.generateConversationId(any(CaptureContext.class))).thenReturn("conv-123");
        when(promptCaptureService.capturePromptUsage(any(CaptureContext.class), any(ServerHttpResponse.class)))
            .thenReturn(Mono.error(new RuntimeException("Storage failed")));
        when(openAIRequestParser.parseRequest(any(), anyString())).thenReturn(CaptureContext.builder());
        
        GatewayFilter gatewayFilter = filter.apply(new PromptCaptureFilter.Config());
        
        // When & Then - Should not fail the main request
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
            .verifyComplete();
            
        // The chain.filter should be called exactly once
        verify(chain).filter(any(ServerWebExchange.class));
    }
    
    @Test
    void shouldHandleLowercaseAuthorizationHeader() {
        // Given
        String requestBody = """
            {
                "model": "gpt-4",
                "messages": [
                    {"role": "user", "content": "Test with lowercase"}
                ]
            }
            """;
            
        DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap(requestBody.getBytes(StandardCharsets.UTF_8));
        
        MockServerHttpRequest request = MockServerHttpRequest.post("/v1/chat/completions")
            .header("authorization", "Bearer sk-lowercase-key")  // lowercase header
            .header("Content-Type", "application/json")
            .body(Flux.just(dataBuffer));
            
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        when(promptCaptureService.hashApiKey("sk-lowercase-key")).thenReturn("hashed-key");
        when(promptCaptureService.generateConversationId(any(CaptureContext.class))).thenReturn("conv-123");
        when(promptCaptureService.capturePromptUsage(any(CaptureContext.class), any(ServerHttpResponse.class)))
            .thenReturn(Mono.empty());
        when(openAIRequestParser.parseRequest(any(), anyString())).thenReturn(CaptureContext.builder());
        
        GatewayFilter gatewayFilter = filter.apply(new PromptCaptureFilter.Config());
        
        // When & Then
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
            .verifyComplete();
            
        verify(promptCaptureService).capturePromptUsage(any(CaptureContext.class), any(ServerHttpResponse.class));
        verify(promptCaptureService).hashApiKey("sk-lowercase-key");
    }
    
    @Test
    void shouldHandleUppercaseAuthorizationHeader() {
        // Given
        String requestBody = """
            {
                "model": "gpt-4",
                "messages": [
                    {"role": "user", "content": "Test with uppercase"}
                ]
            }
            """;
            
        DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap(requestBody.getBytes(StandardCharsets.UTF_8));
        
        MockServerHttpRequest request = MockServerHttpRequest.post("/v1/chat/completions")
            .header("AUTHORIZATION", "Bearer sk-uppercase-key")  // uppercase header
            .header("Content-Type", "application/json")
            .body(Flux.just(dataBuffer));
            
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        when(promptCaptureService.hashApiKey("sk-uppercase-key")).thenReturn("hashed-key");
        when(promptCaptureService.generateConversationId(any(CaptureContext.class))).thenReturn("conv-123");
        when(promptCaptureService.capturePromptUsage(any(CaptureContext.class), any(ServerHttpResponse.class)))
            .thenReturn(Mono.empty());
        when(openAIRequestParser.parseRequest(any(), anyString())).thenReturn(CaptureContext.builder());
        
        GatewayFilter gatewayFilter = filter.apply(new PromptCaptureFilter.Config());
        
        // When & Then
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
            .verifyComplete();
            
        verify(promptCaptureService).capturePromptUsage(any(CaptureContext.class), any(ServerHttpResponse.class));
        verify(promptCaptureService).hashApiKey("sk-uppercase-key");
    }
    
    @Test
    void shouldHandleMixedCaseAuthorizationHeader() {
        // Given
        String requestBody = """
            {
                "model": "gpt-4",
                "messages": [
                    {"role": "user", "content": "Test with mixed case"}
                ]
            }
            """;
            
        DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap(requestBody.getBytes(StandardCharsets.UTF_8));
        
        MockServerHttpRequest request = MockServerHttpRequest.post("/v1/chat/completions")
            .header("AuThOrIzAtIoN", "Bearer sk-mixedcase-key")  // mixed case header
            .header("Content-Type", "application/json")
            .body(Flux.just(dataBuffer));
            
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        when(promptCaptureService.hashApiKey("sk-mixedcase-key")).thenReturn("hashed-key");
        when(promptCaptureService.generateConversationId(any(CaptureContext.class))).thenReturn("conv-123");
        when(promptCaptureService.capturePromptUsage(any(CaptureContext.class), any(ServerHttpResponse.class)))
            .thenReturn(Mono.empty());
        when(openAIRequestParser.parseRequest(any(), anyString())).thenReturn(CaptureContext.builder());
        
        GatewayFilter gatewayFilter = filter.apply(new PromptCaptureFilter.Config());
        
        // When & Then
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
            .verifyComplete();
            
        verify(promptCaptureService).capturePromptUsage(any(CaptureContext.class), any(ServerHttpResponse.class));
        verify(promptCaptureService).hashApiKey("sk-mixedcase-key");
    }
    
    @Test
    void shouldSkipRequestWithoutAuthorizationHeader() {
        // Given
        String requestBody = """
            {
                "model": "gpt-4",
                "messages": [
                    {"role": "user", "content": "Test without auth"}
                ]
            }
            """;
            
        DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap(requestBody.getBytes(StandardCharsets.UTF_8));
        
        MockServerHttpRequest request = MockServerHttpRequest.post("/v1/chat/completions")
            .header("Content-Type", "application/json")  // No Authorization header
            .body(Flux.just(dataBuffer));
            
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        
        GatewayFilter gatewayFilter = filter.apply(new PromptCaptureFilter.Config());
        
        // When & Then
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
            .verifyComplete();
            
        // Should not capture since no valid authorization header
        verify(promptCaptureService, never()).capturePromptUsage(any(), any());
        verify(chain).filter(any(ServerWebExchange.class));
    }
}
