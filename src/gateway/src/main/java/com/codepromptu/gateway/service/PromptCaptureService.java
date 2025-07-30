package com.codepromptu.gateway.service;

import com.codepromptu.gateway.dto.PromptUsageDto;
import com.codepromptu.gateway.model.CaptureContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PromptCaptureService {
    
    private final WebClient.Builder webClientBuilder;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${codepromptu.api.base-url:http://api:8081}")
    private String apiBaseUrl;
    
    @Value("${codepromptu.capture.enabled:true}")
    private boolean captureEnabled;
    
    @Value("${codepromptu.capture.redis-fallback:true}")
    private boolean redisFallbackEnabled;
    
    public Mono<Void> capturePromptUsage(CaptureContext context, ServerHttpResponse response) {
        if (!captureEnabled) {
            return Mono.empty();
        }
        
        return Mono.fromCallable(() -> {
            // Calculate latency
            long latencyMs = Duration.between(context.getRequestTime(), Instant.now()).toMillis();
            context.setLatencyMs(latencyMs);
            
            // Build usage DTO
            PromptUsageDto usage = buildPromptUsageDto(context);
            return usage;
        })
        .flatMap(this::storePromptUsage)
        .subscribeOn(Schedulers.boundedElastic()) // Non-blocking storage
        .onErrorResume(error -> {
            log.error("Failed to capture prompt usage for request {}: {}", 
                context.getRequestId(), error.getMessage(), error);
            return Mono.empty(); // Don't fail the main request
        });
    }
    
    private PromptUsageDto buildPromptUsageDto(CaptureContext context) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("path", context.getPath());
        metadata.put("headers", context.getHeaders());
        if (context.getAdditionalParams() != null) {
            metadata.putAll(context.getAdditionalParams());
        }
        
        return PromptUsageDto.builder()
            .conversationId(context.getConversationId())
            .rawContent(context.getRequestBody())
            .provider(context.getProvider().name())
            .model(context.getModel())
            .requestTimestamp(context.getRequestTime())
            .responseTimestamp(Instant.now())
            .tokensInput(context.getTokensInput())
            .tokensOutput(context.getTokensOutput())
            .status(context.getStatus())
            .responseContent(context.getResponseBody())
            .latencyMs(context.getLatencyMs())
            .clientIp(context.getClientIp())
            .userAgent(context.getUserAgent())
            .apiKeyHash(context.getApiKeyHash())
            .metadata(metadata)
            .temperature(context.getTemperature())
            .maxTokens(context.getMaxTokens())
            .systemPrompt(context.getSystemPrompt())
            .userPrompt(context.getUserPrompt())
            .build();
    }
    
    private Mono<Void> storePromptUsage(PromptUsageDto usage) {
        WebClient webClient = webClientBuilder.baseUrl(apiBaseUrl).build();
        
        return webClient.post()
            .uri("/internal/prompt-usage")
            .bodyValue(usage)
            .retrieve()
            .bodyToMono(Void.class)
            .timeout(Duration.ofSeconds(5))
            .onErrorResume(error -> {
                log.warn("API service unavailable, falling back to Redis: {}", error.getMessage());
                if (redisFallbackEnabled) {
                    return storeInRedis(usage);
                }
                return Mono.error(error);
            });
    }
    
    private Mono<Void> storeInRedis(PromptUsageDto usage) {
        try {
            String key = "prompt-usage:" + UUID.randomUUID().toString();
            String value = objectMapper.writeValueAsString(usage);
            
            return redisTemplate.opsForValue()
                .set(key, value, Duration.ofHours(24))
                .then()
                .doOnSuccess(v -> log.debug("Stored prompt usage in Redis with key: {}", key))
                .onErrorResume(redisError -> {
                    log.error("Failed to store in Redis fallback: {}", redisError.getMessage());
                    return Mono.empty(); // Final fallback - just log and continue
                });
        } catch (Exception e) {
            log.error("Failed to serialize prompt usage for Redis: {}", e.getMessage());
            return Mono.empty();
        }
    }
    
    public String hashApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return null;
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            
            // Convert to hex string and take first 16 characters for logging
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < Math.min(hash.length, 8); i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to hash API key: {}", e.getMessage());
            return "hash_error";
        }
    }
    
    public String generateConversationId(CaptureContext context) {
        // Simple conversation ID generation based on client IP and time window
        // In production, this could be more sophisticated
        String clientIdentifier = context.getClientIp() + ":" + context.getUserAgent();
        long timeWindow = Instant.now().getEpochSecond() / 300; // 5-minute windows
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = clientIdentifier + ":" + timeWindow;
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return "conv_" + hexString.toString();
        } catch (Exception e) {
            log.error("Failed to generate conversation ID: {}", e.getMessage());
            return "conv_" + UUID.randomUUID().toString().substring(0, 8);
        }
    }
}
