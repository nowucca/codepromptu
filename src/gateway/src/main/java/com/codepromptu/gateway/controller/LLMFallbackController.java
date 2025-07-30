package com.codepromptu.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/fallback")
@Slf4j
public class LLMFallbackController {
    
    @PostMapping("/openai")
    public Mono<ResponseEntity<String>> openAIFallback(ServerWebExchange exchange) {
        log.warn("OpenAI circuit breaker activated - service temporarily unavailable");
        
        String fallbackResponse = """
            {
                "error": {
                    "message": "OpenAI service is temporarily unavailable. Please try again later.",
                    "type": "service_unavailable",
                    "code": "circuit_breaker_open"
                }
            }
            """;
        
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header("Content-Type", "application/json")
            .body(fallbackResponse));
    }
    
    @PostMapping("/generic")
    public Mono<ResponseEntity<String>> genericFallback(ServerWebExchange exchange) {
        log.warn("Generic circuit breaker activated for path: {}", 
            exchange.getRequest().getPath().value());
        
        String fallbackResponse = """
            {
                "error": {
                    "message": "Service is temporarily unavailable. Please try again later.",
                    "type": "service_unavailable",
                    "code": "circuit_breaker_open"
                }
            }
            """;
        
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header("Content-Type", "application/json")
            .body(fallbackResponse));
    }
}
