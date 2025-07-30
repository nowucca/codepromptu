# Sprint 3: Gateway LLM Proxy Implementation Plan

## Overview

This document outlines the implementation approach for building LLM API proxy functionality in the CodePromptu Gateway service. The goal is to create transparent prompt capture that intercepts LLM API calls, stores prompts for analysis, and forwards requests to the actual LLM providers without disrupting client workflows.

## Current Gateway Architecture Analysis

### Existing Components
- **Spring Cloud Gateway** with reactive WebFlux
- **Request Logging Filter** for basic request/response logging
- **Rate Limiting Filter** for traffic control
- **Redis integration** for caching and session management
- **Spring Security** for authentication
- **Actuator endpoints** for health checks and metrics

### Current Routes
- `/api/**` → API service (port 8081)
- `/processor/**` → Processor service (port 8082) 
- `/worker/**` → Worker service (port 8084)

## Implementation Approach

### 1. LLM Provider Proxy Routes

Add new routes to handle LLM provider APIs transparently:

```yaml
# New routes to add to gateway.yml
spring:
  cloud:
    gateway:
      routes:
        # OpenAI API Proxy
        - id: openai-proxy
          uri: https://api.openai.com
          predicates:
            - Path=/v1/chat/completions,/v1/completions,/v1/embeddings
            - Header=Authorization, Bearer .*
          filters:
            - name: PromptCaptureFilter
            - name: ApiKeyPassThroughFilter
            - name: CircuitBreaker
              args:
                name: openai-circuit-breaker
                fallbackUri: forward:/fallback/openai
        
        # Anthropic API Proxy  
        - id: anthropic-proxy
          uri: https://api.anthropic.com
          predicates:
            - Path=/v1/messages,/v1/complete
            - Header=x-api-key, .*
          filters:
            - name: PromptCaptureFilter
            - name: ApiKeyPassThroughFilter
            - name: CircuitBreaker
              args:
                name: anthropic-circuit-breaker
                fallbackUri: forward:/fallback/anthropic
        
        # Google AI (Gemini) Proxy
        - id: google-ai-proxy
          uri: https://generativelanguage.googleapis.com
          predicates:
            - Path=/v1beta/models/*/generateContent
          filters:
            - name: PromptCaptureFilter
            - name: ApiKeyPassThroughFilter
```

### 2. Core Components to Implement

#### A. PromptCaptureFilter (Primary Component)
**Purpose**: Intercept and capture LLM requests/responses for analysis

**Key Features**:
- Extract prompt content from request bodies
- Capture response data and metadata
- Store captured data asynchronously to avoid latency
- Support multiple LLM provider formats
- Handle streaming responses appropriately

#### B. ApiKeyPassThroughFilter
**Purpose**: Securely pass client API keys to LLM providers

**Key Features**:
- Extract API keys from headers
- Hash/mask keys for logging security
- Forward keys to downstream providers
- Support multiple authentication schemes

#### C. LLM Provider Services
**Purpose**: Handle provider-specific request/response formats

**Key Features**:
- OpenAI format handling
- Anthropic format handling  
- Google AI format handling
- Extensible for new providers

#### D. Prompt Storage Service
**Purpose**: Asynchronously store captured prompts

**Key Features**:
- Non-blocking storage operations
- Integration with API service for persistence
- Conversation grouping and session tracking
- Error handling and retry logic

### 3. Detailed Implementation Plan

#### Phase 1: Core Infrastructure (Week 1)

**Task 1.1: PromptCaptureFilter Implementation**
```java
@Component
public class PromptCaptureFilter implements GatewayFilter, Ordered {
    
    private final PromptCaptureService promptCaptureService;
    private final LLMProviderDetector providerDetector;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return DataBufferUtils.join(exchange.getRequest().getBody())
            .cast(DataBuffer.class)
            .map(this::extractRequestBody)
            .flatMap(requestBody -> {
                // Capture request data
                CaptureContext context = createCaptureContext(exchange, requestBody);
                
                // Continue with modified request
                ServerHttpRequest modifiedRequest = rebuildRequest(exchange.getRequest(), requestBody);
                ServerWebExchange modifiedExchange = exchange.mutate().request(modifiedRequest).build();
                
                return chain.filter(modifiedExchange)
                    .then(captureResponse(context, modifiedExchange.getResponse()));
            });
    }
    
    private Mono<Void> captureResponse(CaptureContext context, ServerHttpResponse response) {
        // Capture response asynchronously
        return promptCaptureService.capturePromptUsage(context, response)
            .onErrorResume(error -> {
                log.error("Failed to capture prompt usage", error);
                return Mono.empty(); // Don't fail the main request
            })
            .then();
    }
}
```

**Task 1.2: LLM Provider Detection and Parsing**
```java
@Service
public class LLMProviderDetector {
    
    public LLMProvider detectProvider(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        Map<String, String> headers = exchange.getRequest().getHeaders().toSingleValueMap();
        
        if (path.startsWith("/v1/chat/completions") && headers.containsKey("authorization")) {
            return LLMProvider.OPENAI;
        } else if (path.startsWith("/v1/messages") && headers.containsKey("x-api-key")) {
            return LLMProvider.ANTHROPIC;
        } else if (path.contains("generateContent")) {
            return LLMProvider.GOOGLE_AI;
        }
        
        return LLMProvider.UNKNOWN;
    }
}

@Service
public class OpenAIRequestParser implements LLMRequestParser {
    
    @Override
    public PromptData extractPromptData(String requestBody) {
        JsonNode request = objectMapper.readTree(requestBody);
        
        return PromptData.builder()
            .content(extractMessages(request.get("messages")))
            .model(request.get("model").asText())
            .temperature(request.path("temperature").asDouble(1.0))
            .maxTokens(request.path("max_tokens").asInt())
            .build();
    }
    
    private String extractMessages(JsonNode messages) {
        StringBuilder content = new StringBuilder();
        for (JsonNode message : messages) {
            String role = message.get("role").asText();
            String messageContent = message.get("content").asText();
            content.append(role).append(": ").append(messageContent).append("\n");
        }
        return content.toString();
    }
}
```

**Task 1.3: Asynchronous Prompt Storage**
```java
@Service
public class PromptCaptureService {
    
    private final WebClient apiServiceClient;
    private final RedisTemplate<String, Object> redisTemplate;
    
    public Mono<Void> capturePromptUsage(CaptureContext context, ServerHttpResponse response) {
        return Mono.fromCallable(() -> {
            PromptUsageDto usage = PromptUsageDto.builder()
                .rawContent(context.getRequestBody())
                .provider(context.getProvider().name())
                .model(context.getModel())
                .requestTimestamp(context.getRequestTime())
                .responseTimestamp(Instant.now())
                .clientIp(context.getClientIp())
                .userAgent(context.getUserAgent())
                .apiKeyHash(hashApiKey(context.getApiKey()))
                .build();
                
            return usage;
        })
        .flatMap(this::storePromptUsage)
        .subscribeOn(Schedulers.boundedElastic()); // Non-blocking storage
    }
    
    private Mono<Void> storePromptUsage(PromptUsageDto usage) {
        return apiServiceClient.post()
            .uri("/internal/prompt-usage")
            .bodyValue(usage)
            .retrieve()
            .bodyToMono(Void.class)
            .onErrorResume(error -> {
                // Fallback to Redis if API service is unavailable
                return storeInRedis(usage);
            });
    }
}
```

#### Phase 2: Provider Integration (Week 2)

**Task 2.1: API Key Management**
```java
@Component
public class ApiKeyPassThroughFilter implements GatewayFilter, Ordered {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        LLMProvider provider = detectProvider(exchange);
        
        // Extract and validate API key
        String apiKey = extractApiKey(request, provider);
        if (apiKey == null) {
            return handleMissingApiKey(exchange);
        }
        
        // Add to capture context for logging
        exchange.getAttributes().put("api-key-hash", hashApiKey(apiKey));
        
        // Forward to provider
        ServerHttpRequest modifiedRequest = request.mutate()
            .headers(headers -> configureProviderHeaders(headers, provider, apiKey))
            .build();
            
        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }
    
    private void configureProviderHeaders(HttpHeaders headers, LLMProvider provider, String apiKey) {
        switch (provider) {
            case OPENAI:
                headers.set("Authorization", "Bearer " + apiKey);
                break;
            case ANTHROPIC:
                headers.set("x-api-key", apiKey);
                headers.set("anthropic-version", "2023-06-01");
                break;
            case GOOGLE_AI:
                headers.set("x-goog-api-key", apiKey);
                break;
        }
    }
}
```

**Task 2.2: Circuit Breaker and Fallback**
```java
@RestController
public class LLMFallbackController {
    
    @PostMapping("/fallback/openai")
    public Mono<ResponseEntity<String>> openAIFallback(ServerHttpRequest request) {
        return Mono.just(ResponseEntity.status(503)
            .body("{\"error\": {\"message\": \"OpenAI service temporarily unavailable\", \"type\": \"service_unavailable\"}}"));
    }
    
    @PostMapping("/fallback/anthropic")
    public Mono<ResponseEntity<String>> anthropicFallback(ServerHttpRequest request) {
        return Mono.just(ResponseEntity.status(503)
            .body("{\"error\": {\"message\": \"Anthropic service temporarily unavailable\", \"type\": \"service_unavailable\"}}"));
    }
}
```

### 4. Testing Strategy

#### Unit Tests
```java
@ExtendWith(MockitoExtension.class)
class PromptCaptureFilterTest {
    
    @Mock
    private PromptCaptureService promptCaptureService;
    
    @Mock
    private LLMProviderDetector providerDetector;
    
    @InjectMocks
    private PromptCaptureFilter filter;
    
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
            
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1/chat/completions")
                .header("Authorization", "Bearer sk-test-key")
                .body(requestBody)
        );
        
        when(providerDetector.detectProvider(exchange)).thenReturn(LLMProvider.OPENAI);
        when(promptCaptureService.capturePromptUsage(any(), any())).thenReturn(Mono.empty());
        
        // When
        StepVerifier.create(filter.filter(exchange, chain))
            // Then
            .verifyComplete();
            
        verify(promptCaptureService).capturePromptUsage(any(CaptureContext.class), any());
    }
    
    @Test
    void shouldHandleStorageFailureGracefully() {
        // Test that storage failures don't break the main request flow
    }
    
    @Test
    void shouldPreserveRequestBodyForDownstream() {
        // Test that request body is properly reconstructed
    }
}
```

#### Integration Tests
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class GatewayProxyIntegrationTest {
    
    @Container
    static WireMockContainer openAIMock = new WireMockContainer("wiremock/wiremock:2.35.0")
            .withMappingFromResource("openai-mock-mappings.json");
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void shouldProxyOpenAIRequestSuccessfully() {
        // Given
        String requestBody = """
            {
                "model": "gpt-4",
                "messages": [{"role": "user", "content": "Test prompt"}]
            }
            """;
            
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer sk-test-key");
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // When
        ResponseEntity<String> response = restTemplate.exchange(
            "/v1/chat/completions",
            HttpMethod.POST,
            new HttpEntity<>(requestBody, headers),
            String.class
        );
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Verify prompt was captured in database
        // Verify response matches expected format
    }
    
    @Test
    void shouldHandleProviderDowntime() {
        // Test circuit breaker functionality
    }
    
    @Test
    void shouldCaptureConversationContext() {
        // Test conversation grouping
    }
}
```

### 5. Configuration Updates

#### Gateway Configuration
```yaml
# Add to gateway.yml
spring:
  cloud:
    gateway:
      routes:
        # LLM Provider routes (as shown above)
      default-filters:
        - name: CircuitBreaker
          args:
            name: default-circuit-breaker
            fallbackUri: forward:/fallback/generic

# Circuit breaker configuration
resilience4j:
  circuitbreaker:
    instances:
      openai-circuit-breaker:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        sliding-window-size: 10
      anthropic-circuit-breaker:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        sliding-window-size: 10

# Prompt capture configuration
codepromptu:
  capture:
    enabled: true
    async-storage: true
    redis-fallback: true
    providers:
      openai:
        enabled: true
        endpoints: ["/v1/chat/completions", "/v1/completions"]
      anthropic:
        enabled: true
        endpoints: ["/v1/messages", "/v1/complete"]
      google-ai:
        enabled: true
        endpoints: ["/v1beta/models/*/generateContent"]
```

#### Maven Dependencies
```xml
<!-- Add to gateway/pom.xml -->
<dependencies>
    <!-- Circuit Breaker -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-circuitbreaker-reactor-resilience4j</artifactId>
    </dependency>
    
    <!-- WebClient for API calls -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
    
    <!-- JSON Processing -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
    
    <!-- Testing -->
    <dependency>
        <groupId>com.github.tomakehurst</groupId>
        <artifactId>wiremock-jre8</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### 6. Success Criteria

#### Functional Requirements
- ✅ Transparent proxy for OpenAI, Anthropic, and Google AI APIs
- ✅ Zero-latency impact on client requests (async capture)
- ✅ Secure API key pass-through without logging sensitive data
- ✅ Conversation grouping and session tracking
- ✅ Circuit breaker protection for provider downtime
- ✅ Comprehensive error handling and fallback mechanisms

#### Non-Functional Requirements
- ✅ <50ms additional latency for prompt capture
- ✅ 99.9% uptime even during storage failures
- ✅ Support for 1000+ concurrent requests
- ✅ Secure handling of API keys and sensitive data
- ✅ Comprehensive monitoring and alerting

#### Testing Requirements
- ✅ Unit test coverage >85%
- ✅ Integration tests for all supported providers
- ✅ Load testing for performance validation
- ✅ Chaos engineering tests for resilience

### 7. Risk Mitigation

#### Technical Risks
1. **Request Body Consumption**: Spring Cloud Gateway consumes request bodies, need to reconstruct for downstream
   - **Mitigation**: Use DataBufferUtils to cache and replay request body

2. **Async Storage Failures**: Storage failures could impact main request flow
   - **Mitigation**: Comprehensive error handling with Redis fallback

3. **Provider API Changes**: LLM provider APIs may change formats
   - **Mitigation**: Extensible parser architecture with version detection

4. **Performance Impact**: Additional processing could slow requests
   - **Mitigation**: Async processing, performance monitoring, circuit breakers

#### Security Risks
1. **API Key Exposure**: Risk of logging sensitive API keys
   - **Mitigation**: Hash keys for logging, secure header handling

2. **Request/Response Logging**: Sensitive data in prompts/responses
   - **Mitigation**: Configurable data masking, secure storage

### 8. Monitoring and Observability

#### Metrics to Track
- Request/response latency by provider
- Capture success/failure rates
- Circuit breaker state changes
- Storage operation performance
- API key usage patterns (hashed)

#### Alerts to Configure
- High error rates from LLM providers
- Storage service unavailability
- Circuit breaker state changes
- Unusual latency patterns
- Failed prompt capture operations

This implementation plan provides a comprehensive approach to building the LLM API proxy functionality while maintaining the existing gateway architecture and ensuring robust, secure, and performant operation.
