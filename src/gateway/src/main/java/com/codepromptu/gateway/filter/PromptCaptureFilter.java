package com.codepromptu.gateway.filter;

import com.codepromptu.gateway.model.CaptureContext;
import com.codepromptu.gateway.model.LLMProvider;
import com.codepromptu.gateway.service.OpenAIRequestParser;
import com.codepromptu.gateway.service.PromptCaptureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class PromptCaptureFilter extends AbstractGatewayFilterFactory<PromptCaptureFilter.Config> {
    
    private final PromptCaptureService promptCaptureService;
    private final OpenAIRequestParser openAIRequestParser;
    
    public PromptCaptureFilter(PromptCaptureService promptCaptureService, OpenAIRequestParser openAIRequestParser) {
        super(Config.class);
        this.promptCaptureService = promptCaptureService;
        this.openAIRequestParser = openAIRequestParser;
    }
    
    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            
            // Only capture for LLM provider endpoints
            if (!isLLMProviderRequest(request)) {
                return chain.filter(exchange);
            }
            
            String requestId = UUID.randomUUID().toString();
            log.debug("Capturing prompt for request: {} {}", request.getMethod(), request.getPath());
            
            return DataBufferUtils.join(request.getBody())
                .cast(DataBuffer.class)
                .defaultIfEmpty(exchange.getResponse().bufferFactory().allocateBuffer(0))
                .flatMap(dataBuffer -> {
                    // Extract request body
                    String requestBody = extractRequestBody(dataBuffer);
                    
                    // Create capture context
                    CaptureContext context = createCaptureContext(exchange, requestId, requestBody);
                    
                    // Parse OpenAI request
                    if (context.getProvider() == LLMProvider.OPENAI) {
                        CaptureContext.CaptureContextBuilder builder = CaptureContext.builder()
                            .requestId(context.getRequestId())
                            .provider(context.getProvider())
                            .requestBody(context.getRequestBody())
                            .apiKey(context.getApiKey())
                            .apiKeyHash(context.getApiKeyHash())
                            .requestTime(context.getRequestTime())
                            .clientIp(context.getClientIp())
                            .userAgent(context.getUserAgent())
                            .path(context.getPath())
                            .headers(context.getHeaders())
                            .conversationId(context.getConversationId());
                        
                        CaptureContext.CaptureContextBuilder updatedBuilder = openAIRequestParser.parseRequest(builder, requestBody);
                        context = updatedBuilder.build();
                    }
                    
                    // Store context in exchange attributes for response capture
                    exchange.getAttributes().put("capture-context", context);
                    
                    // Create new request with cached body
                    ServerHttpRequest modifiedRequest = createModifiedRequest(request, requestBody);
                    ServerWebExchange modifiedExchange = exchange.mutate().request(modifiedRequest).build();
                    
                    // Continue with the request and capture response
                    return chain.filter(modifiedExchange)
                        .then(captureResponse(context, exchange))
                        .onErrorResume(captureError -> {
                            log.error("Error capturing response for request {}: {}", 
                                requestId, captureError.getMessage(), captureError);
                            // Don't fail the main request, just continue
                            return Mono.empty();
                        });
                })
                .onErrorResume(error -> {
                    log.error("Error in prompt capture filter for request {}: {}", 
                        requestId, error.getMessage(), error);
                    // Don't fail the main request, but we still need to call chain.filter
                    // Create a simple request without body caching
                    return chain.filter(exchange);
                });
        };
    }
    
    private boolean isLLMProviderRequest(ServerHttpRequest request) {
        String path = request.getPath().value();
        
        // Check if it's an OpenAI endpoint path
        boolean isOpenAIPath = path.startsWith("/v1/chat/completions") ||
                              path.startsWith("/v1/completions") ||
                              path.startsWith("/v1/embeddings");
        
        if (!isOpenAIPath) {
            return false;
        }
        
        // Also check if there's a valid authorization header
        String authHeader = getAuthorizationHeader(request);
        return authHeader != null && authHeader.startsWith("Bearer ");
    }
    
    private String extractRequestBody(DataBuffer dataBuffer) {
        try {
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            DataBufferUtils.release(dataBuffer);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to extract request body: {}", e.getMessage());
            return "";
        }
    }
    
    private CaptureContext createCaptureContext(ServerWebExchange exchange, String requestId, String requestBody) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Detect provider
        LLMProvider provider = detectProvider(request);
        
        // Extract API key
        String apiKey = extractApiKey(request, provider);
        String apiKeyHash = promptCaptureService.hashApiKey(apiKey);
        
        // Build context
        CaptureContext.CaptureContextBuilder builder = CaptureContext.builder()
            .requestId(requestId)
            .provider(provider)
            .requestBody(requestBody)
            .apiKey(apiKey)
            .apiKeyHash(apiKeyHash)
            .requestTime(Instant.now())
            .clientIp(getClientIpAddress(request))
            .userAgent(request.getHeaders().getFirst("User-Agent"))
            .path(request.getPath().value())
            .headers(extractHeaders(request));
        
        CaptureContext context = builder.build();
        
        // Generate conversation ID
        String conversationId = promptCaptureService.generateConversationId(context);
        context.setConversationId(conversationId);
        
        return context;
    }
    
    private LLMProvider detectProvider(ServerHttpRequest request) {
        String path = request.getPath().value();
        
        if (path.startsWith("/v1/chat/completions") || path.startsWith("/v1/completions") || path.startsWith("/v1/embeddings")) {
            String authHeader = getAuthorizationHeader(request);
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                return LLMProvider.OPENAI;
            }
        }
        
        return LLMProvider.UNKNOWN;
    }
    
    private String extractApiKey(ServerHttpRequest request, LLMProvider provider) {
        switch (provider) {
            case OPENAI:
                String authHeader = getAuthorizationHeader(request);
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    return authHeader.substring(7);
                }
                break;
            default:
                break;
        }
        return null;
    }
    
    private String getAuthorizationHeader(ServerHttpRequest request) {
        // HTTP headers are case-insensitive, so we need to check for all variations
        return request.getHeaders().entrySet().stream()
            .filter(entry -> "authorization".equalsIgnoreCase(entry.getKey()))
            .map(entry -> entry.getValue().isEmpty() ? null : entry.getValue().get(0))
            .findFirst()
            .orElse(null);
    }
    
    private Map<String, String> extractHeaders(ServerHttpRequest request) {
        Map<String, String> headers = new HashMap<>();
        request.getHeaders().forEach((key, values) -> {
            if (!key.equalsIgnoreCase("authorization") && !values.isEmpty()) {
                headers.put(key, values.get(0));
            }
        });
        return headers;
    }
    
    private String getClientIpAddress(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddress() != null ? 
                request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }
    
    private ServerHttpRequest createModifiedRequest(ServerHttpRequest original, String body) {
        DataBuffer bodyBuffer = original.getBody().blockFirst().factory().wrap(body.getBytes(StandardCharsets.UTF_8));
        Flux<DataBuffer> bodyFlux = Flux.just(bodyBuffer);
        
        return new ServerHttpRequestDecorator(original) {
            @Override
            public Flux<DataBuffer> getBody() {
                return bodyFlux;
            }
        };
    }
    
    private Mono<Void> captureResponse(CaptureContext context, ServerWebExchange exchange) {
        return Mono.fromRunnable(() -> {
            // In a real implementation, we'd need to capture the response body
            // For now, we'll capture basic response information
            int statusCode = exchange.getResponse().getStatusCode() != null ? 
                exchange.getResponse().getStatusCode().value() : 0;
            
            if (statusCode >= 200 && statusCode < 300) {
                context.setStatus("success");
            } else {
                context.setStatus("error_" + statusCode);
            }
        })
        .then(promptCaptureService.capturePromptUsage(context, exchange.getResponse()));
    }
    
    public static class Config {
        // Configuration properties can be added here if needed
    }
    
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1; // After request logging
    }
}
