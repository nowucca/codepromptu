package com.codepromptu.gateway.filter;

import com.codepromptu.gateway.model.LLMProvider;
import com.codepromptu.gateway.service.LLMProviderDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyPassThroughFilterTest {

    @Mock
    private LLMProviderDetector providerDetector;

    @Mock
    private GatewayFilterChain filterChain;

    private ApiKeyPassThroughFilter filter;
    private ApiKeyPassThroughFilter.Config config;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyPassThroughFilter(providerDetector);
        config = new ApiKeyPassThroughFilter.Config();
    }

    @Test
    void shouldPassThroughOpenAIRequest() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1/chat/completions")
                .header("Authorization", "Bearer sk-test-openai-key")
        );

        when(providerDetector.detectProvider(exchange)).thenReturn(LLMProvider.OPENAI);
        when(providerDetector.extractApiKey(exchange, LLMProvider.OPENAI)).thenReturn("sk-test-openai-key");
        when(providerDetector.isValidApiKeyFormat("sk-test-openai-key", LLMProvider.OPENAI)).thenReturn(true);
        when(providerDetector.getRequiredHeaders(LLMProvider.OPENAI)).thenReturn(Map.of("content-type", "application/json"));
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        GatewayFilter gatewayFilter = filter.apply(config);

        // When
        StepVerifier.create(gatewayFilter.filter(exchange, filterChain))
            // Then
            .verifyComplete();

        // Verify attributes were set
        assertThat(exchange.getAttributes().get("llm-provider")).isEqualTo(LLMProvider.OPENAI);
        assertThat(exchange.getAttributes().get("api-key-hash")).isNotNull();
    }

    @Test
    void shouldPassThroughAnthropicRequest() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1/messages")
                .header("x-api-key", "sk-ant-test-key")
        );

        when(providerDetector.detectProvider(exchange)).thenReturn(LLMProvider.ANTHROPIC);
        when(providerDetector.extractApiKey(exchange, LLMProvider.ANTHROPIC)).thenReturn("sk-ant-test-key");
        when(providerDetector.isValidApiKeyFormat("sk-ant-test-key", LLMProvider.ANTHROPIC)).thenReturn(true);
        when(providerDetector.getRequiredHeaders(LLMProvider.ANTHROPIC)).thenReturn(
            Map.of("anthropic-version", "2023-06-01", "content-type", "application/json"));
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        GatewayFilter gatewayFilter = filter.apply(config);

        // When
        StepVerifier.create(gatewayFilter.filter(exchange, filterChain))
            // Then
            .verifyComplete();

        // Verify attributes were set
        assertThat(exchange.getAttributes().get("llm-provider")).isEqualTo(LLMProvider.ANTHROPIC);
        assertThat(exchange.getAttributes().get("api-key-hash")).isNotNull();
    }

    @Test
    void shouldPassThroughGoogleAIRequest() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1beta/models/gemini-pro/generateContent")
                .header("x-goog-api-key", "google-test-key")
        );

        when(providerDetector.detectProvider(exchange)).thenReturn(LLMProvider.GOOGLE_AI);
        when(providerDetector.extractApiKey(exchange, LLMProvider.GOOGLE_AI)).thenReturn("google-test-key");
        when(providerDetector.isValidApiKeyFormat("google-test-key", LLMProvider.GOOGLE_AI)).thenReturn(true);
        when(providerDetector.getRequiredHeaders(LLMProvider.GOOGLE_AI)).thenReturn(Map.of("content-type", "application/json"));
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        GatewayFilter gatewayFilter = filter.apply(config);

        // When
        StepVerifier.create(gatewayFilter.filter(exchange, filterChain))
            // Then
            .verifyComplete();

        // Verify attributes were set
        assertThat(exchange.getAttributes().get("llm-provider")).isEqualTo(LLMProvider.GOOGLE_AI);
        assertThat(exchange.getAttributes().get("api-key-hash")).isNotNull();
    }

    @Test
    void shouldReturnBadRequestForUnknownProvider() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/unknown/endpoint")
                .header("Authorization", "Bearer some-key")
        );

        when(providerDetector.detectProvider(exchange)).thenReturn(LLMProvider.UNKNOWN);

        GatewayFilter gatewayFilter = filter.apply(config);

        // When
        StepVerifier.create(gatewayFilter.filter(exchange, filterChain))
            // Then
            .verifyComplete();

        // Verify error response
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exchange.getResponse().getHeaders().getFirst("Content-Type")).isEqualTo("application/json");
    }

    @Test
    void shouldReturnUnauthorizedForMissingApiKey() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1/chat/completions")
        );

        when(providerDetector.detectProvider(exchange)).thenReturn(LLMProvider.OPENAI);
        when(providerDetector.extractApiKey(exchange, LLMProvider.OPENAI)).thenReturn(null);

        GatewayFilter gatewayFilter = filter.apply(config);

        // When
        StepVerifier.create(gatewayFilter.filter(exchange, filterChain))
            // Then
            .verifyComplete();

        // Verify error response
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("Content-Type")).isEqualTo("application/json");
    }

    @Test
    void shouldReturnUnauthorizedForEmptyApiKey() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1/chat/completions")
                .header("Authorization", "Bearer ")
        );

        when(providerDetector.detectProvider(exchange)).thenReturn(LLMProvider.OPENAI);
        when(providerDetector.extractApiKey(exchange, LLMProvider.OPENAI)).thenReturn("");

        GatewayFilter gatewayFilter = filter.apply(config);

        // When
        StepVerifier.create(gatewayFilter.filter(exchange, filterChain))
            // Then
            .verifyComplete();

        // Verify error response
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturnUnauthorizedForInvalidApiKeyFormat() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1/chat/completions")
                .header("Authorization", "Bearer invalid-key")
        );

        when(providerDetector.detectProvider(exchange)).thenReturn(LLMProvider.OPENAI);
        when(providerDetector.extractApiKey(exchange, LLMProvider.OPENAI)).thenReturn("invalid-key");
        when(providerDetector.isValidApiKeyFormat("invalid-key", LLMProvider.OPENAI)).thenReturn(false);

        GatewayFilter gatewayFilter = filter.apply(config);

        // When
        StepVerifier.create(gatewayFilter.filter(exchange, filterChain))
            // Then
            .verifyComplete();

        // Verify error response
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("Content-Type")).isEqualTo("application/json");
    }

    @Test
    void shouldConfigureOpenAIHeaders() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1/chat/completions")
                .header("Authorization", "Bearer sk-test-key")
        );

        when(providerDetector.detectProvider(exchange)).thenReturn(LLMProvider.OPENAI);
        when(providerDetector.extractApiKey(exchange, LLMProvider.OPENAI)).thenReturn("sk-test-key");
        when(providerDetector.isValidApiKeyFormat("sk-test-key", LLMProvider.OPENAI)).thenReturn(true);
        when(providerDetector.getRequiredHeaders(LLMProvider.OPENAI)).thenReturn(Map.of("content-type", "application/json"));
        when(filterChain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            ServerWebExchange modifiedExchange = invocation.getArgument(0);
            
            // Verify headers were configured correctly
            assertThat(modifiedExchange.getRequest().getHeaders().getFirst("Authorization"))
                .isEqualTo("Bearer sk-test-key");
            assertThat(modifiedExchange.getRequest().getHeaders().getFirst("Content-Type"))
                .isEqualTo("application/json");
            assertThat(modifiedExchange.getRequest().getHeaders().getFirst("User-Agent"))
                .isEqualTo("CodePromptu-Gateway/1.0");
            
            return Mono.empty();
        });

        GatewayFilter gatewayFilter = filter.apply(config);

        // When
        StepVerifier.create(gatewayFilter.filter(exchange, filterChain))
            // Then
            .verifyComplete();
    }

    @Test
    void shouldConfigureAnthropicHeaders() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1/messages")
                .header("x-api-key", "sk-ant-test-key")
        );

        when(providerDetector.detectProvider(exchange)).thenReturn(LLMProvider.ANTHROPIC);
        when(providerDetector.extractApiKey(exchange, LLMProvider.ANTHROPIC)).thenReturn("sk-ant-test-key");
        when(providerDetector.isValidApiKeyFormat("sk-ant-test-key", LLMProvider.ANTHROPIC)).thenReturn(true);
        when(providerDetector.getRequiredHeaders(LLMProvider.ANTHROPIC)).thenReturn(
            Map.of("anthropic-version", "2023-06-01", "content-type", "application/json"));
        when(filterChain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            ServerWebExchange modifiedExchange = invocation.getArgument(0);
            
            // Verify headers were configured correctly
            assertThat(modifiedExchange.getRequest().getHeaders().getFirst("x-api-key"))
                .isEqualTo("sk-ant-test-key");
            assertThat(modifiedExchange.getRequest().getHeaders().getFirst("anthropic-version"))
                .isEqualTo("2023-06-01");
            assertThat(modifiedExchange.getRequest().getHeaders().getFirst("Content-Type"))
                .isEqualTo("application/json");
            
            return Mono.empty();
        });

        GatewayFilter gatewayFilter = filter.apply(config);

        // When
        StepVerifier.create(gatewayFilter.filter(exchange, filterChain))
            // Then
            .verifyComplete();
    }

    @Test
    void shouldConfigureGoogleAIHeaders() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1beta/models/gemini-pro/generateContent")
                .header("x-goog-api-key", "google-test-key")
        );

        when(providerDetector.detectProvider(exchange)).thenReturn(LLMProvider.GOOGLE_AI);
        when(providerDetector.extractApiKey(exchange, LLMProvider.GOOGLE_AI)).thenReturn("google-test-key");
        when(providerDetector.isValidApiKeyFormat("google-test-key", LLMProvider.GOOGLE_AI)).thenReturn(true);
        when(providerDetector.getRequiredHeaders(LLMProvider.GOOGLE_AI)).thenReturn(Map.of("content-type", "application/json"));
        when(filterChain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            ServerWebExchange modifiedExchange = invocation.getArgument(0);
            
            // Verify headers were configured correctly
            assertThat(modifiedExchange.getRequest().getHeaders().getFirst("x-goog-api-key"))
                .isEqualTo("google-test-key");
            assertThat(modifiedExchange.getRequest().getHeaders().getFirst("Content-Type"))
                .isEqualTo("application/json");
            
            return Mono.empty();
        });

        GatewayFilter gatewayFilter = filter.apply(config);

        // When
        StepVerifier.create(gatewayFilter.filter(exchange, filterChain))
            // Then
            .verifyComplete();
    }

    @Test
    void shouldHashApiKeyForLogging() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1/chat/completions")
                .header("Authorization", "Bearer sk-test-key-for-hashing")
        );

        when(providerDetector.detectProvider(exchange)).thenReturn(LLMProvider.OPENAI);
        when(providerDetector.extractApiKey(exchange, LLMProvider.OPENAI)).thenReturn("sk-test-key-for-hashing");
        when(providerDetector.isValidApiKeyFormat("sk-test-key-for-hashing", LLMProvider.OPENAI)).thenReturn(true);
        when(providerDetector.getRequiredHeaders(LLMProvider.OPENAI)).thenReturn(Map.of("content-type", "application/json"));
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        GatewayFilter gatewayFilter = filter.apply(config);

        // When
        StepVerifier.create(gatewayFilter.filter(exchange, filterChain))
            // Then
            .verifyComplete();

        // Verify API key was hashed (not the original key)
        String hashedKey = (String) exchange.getAttributes().get("api-key-hash");
        assertThat(hashedKey).isNotNull();
        assertThat(hashedKey).isNotEqualTo("sk-test-key-for-hashing");
        assertThat(hashedKey).hasSize(8); // First 8 hex characters
    }

    @Test
    void shouldHandleNullApiKeyInHashing() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1/chat/completions")
        );

        when(providerDetector.detectProvider(exchange)).thenReturn(LLMProvider.OPENAI);
        when(providerDetector.extractApiKey(exchange, LLMProvider.OPENAI)).thenReturn(null);

        GatewayFilter gatewayFilter = filter.apply(config);

        // When
        StepVerifier.create(gatewayFilter.filter(exchange, filterChain))
            // Then
            .verifyComplete();

        // Should return unauthorized for null API key
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldPreserveOriginalRequestPath() {
        // Given
        String originalPath = "/v1/chat/completions";
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post(originalPath)
                .header("Authorization", "Bearer sk-test-key")
        );

        when(providerDetector.detectProvider(exchange)).thenReturn(LLMProvider.OPENAI);
        when(providerDetector.extractApiKey(exchange, LLMProvider.OPENAI)).thenReturn("sk-test-key");
        when(providerDetector.isValidApiKeyFormat("sk-test-key", LLMProvider.OPENAI)).thenReturn(true);
        when(providerDetector.getRequiredHeaders(LLMProvider.OPENAI)).thenReturn(Map.of("content-type", "application/json"));
        when(filterChain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            ServerWebExchange modifiedExchange = invocation.getArgument(0);
            
            // Verify path is preserved
            assertThat(modifiedExchange.getRequest().getPath().value()).isEqualTo(originalPath);
            
            return Mono.empty();
        });

        GatewayFilter gatewayFilter = filter.apply(config);

        // When
        StepVerifier.create(gatewayFilter.filter(exchange, filterChain))
            // Then
            .verifyComplete();
    }

    @Test
    void shouldHaveCorrectFilterOrder() {
        // Given
        GatewayFilter gatewayFilter = filter.apply(config);

        // When
        int order = ((ApiKeyPassThroughFilter.ApiKeyPassThroughGatewayFilter) gatewayFilter).getOrder();

        // Then
        assertThat(order).isEqualTo(-2147483548); // HIGHEST_PRECEDENCE + 100
    }

    @Test
    void shouldConfigureFilterWithCustomConfig() {
        // Given
        ApiKeyPassThroughFilter.Config customConfig = new ApiKeyPassThroughFilter.Config();
        customConfig.setValidateApiKeys(false);
        customConfig.setLogApiKeyHashes(false);
        customConfig.setMaxApiKeyLength(100);

        // When
        GatewayFilter gatewayFilter = filter.apply(customConfig);

        // Then
        assertThat(gatewayFilter).isNotNull();
        assertThat(customConfig.isValidateApiKeys()).isFalse();
        assertThat(customConfig.isLogApiKeyHashes()).isFalse();
        assertThat(customConfig.getMaxApiKeyLength()).isEqualTo(100);
    }

    @Test
    void shouldHandleMultipleHeadersCorrectly() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1/messages")
                .header("x-api-key", "sk-ant-test-key")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Test-Client/1.0")
        );

        when(providerDetector.detectProvider(exchange)).thenReturn(LLMProvider.ANTHROPIC);
        when(providerDetector.extractApiKey(exchange, LLMProvider.ANTHROPIC)).thenReturn("sk-ant-test-key");
        when(providerDetector.isValidApiKeyFormat("sk-ant-test-key", LLMProvider.ANTHROPIC)).thenReturn(true);
        when(providerDetector.getRequiredHeaders(LLMProvider.ANTHROPIC)).thenReturn(
            Map.of("anthropic-version", "2023-06-01"));
        when(filterChain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            ServerWebExchange modifiedExchange = invocation.getArgument(0);
            
            // Verify all headers are present
            assertThat(modifiedExchange.getRequest().getHeaders().getFirst("x-api-key"))
                .isEqualTo("sk-ant-test-key");
            assertThat(modifiedExchange.getRequest().getHeaders().getFirst("anthropic-version"))
                .isEqualTo("2023-06-01");
            assertThat(modifiedExchange.getRequest().getHeaders().getFirst("Content-Type"))
                .isEqualTo("application/json");
            assertThat(modifiedExchange.getRequest().getHeaders().getFirst("User-Agent"))
                .isEqualTo("CodePromptu-Gateway/1.0"); // Should be overridden
            
            return Mono.empty();
        });

        GatewayFilter gatewayFilter = filter.apply(config);

        // When
        StepVerifier.create(gatewayFilter.filter(exchange, filterChain))
            // Then
            .verifyComplete();
    }
}
