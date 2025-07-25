package com.codepromptu.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class RateLimitingFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingFilter.class);
    private static final int REQUESTS_PER_MINUTE = 100;
    private static final Duration WINDOW_DURATION = Duration.ofMinutes(1);

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String clientIp = getClientIpAddress(request);
        String key = "rate_limit:" + clientIp;

        // Skip rate limiting for health checks and actuator endpoints
        String path = request.getPath().value();
        if (path.startsWith("/actuator") || path.equals("/health")) {
            return chain.filter(exchange);
        }

        return redisTemplate.opsForValue()
                .increment(key)
                .flatMap(count -> {
                    if (count == 1) {
                        // First request in the window, set expiration
                        return redisTemplate.expire(key, WINDOW_DURATION)
                                .then(Mono.just(count));
                    }
                    return Mono.just(count);
                })
                .flatMap(count -> {
                    if (count > REQUESTS_PER_MINUTE) {
                        logger.warn("Rate limit exceeded for IP: {} (count: {})", clientIp, count);
                        return handleRateLimitExceeded(exchange);
                    }
                    
                    // Add rate limit headers
                    ServerHttpResponse response = exchange.getResponse();
                    response.getHeaders().add("X-RateLimit-Limit", String.valueOf(REQUESTS_PER_MINUTE));
                    response.getHeaders().add("X-RateLimit-Remaining", 
                            String.valueOf(Math.max(0, REQUESTS_PER_MINUTE - count)));
                    
                    return chain.filter(exchange);
                })
                .onErrorResume(throwable -> {
                    logger.error("Error in rate limiting filter for IP: {}", clientIp, throwable);
                    // On Redis error, allow the request to proceed
                    return chain.filter(exchange);
                });
    }

    private Mono<Void> handleRateLimitExceeded(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().add("Content-Type", "application/json");
        response.getHeaders().add("X-RateLimit-Limit", String.valueOf(REQUESTS_PER_MINUTE));
        response.getHeaders().add("X-RateLimit-Remaining", "0");
        
        String body = "{\"error\":\"Rate limit exceeded\",\"message\":\"Too many requests. Please try again later.\"}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
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

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
