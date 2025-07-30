package com.codepromptu.gateway.service;

import com.codepromptu.gateway.model.LLMProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class LLMProviderDetectorTest {

    private LLMProviderDetector detector;

    @BeforeEach
    void setUp() {
        detector = new LLMProviderDetector();
    }

    @Test
    void shouldDetectOpenAIChatCompletions() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1/chat/completions")
                .header("Authorization", "Bearer sk-test-key")
        );

        // When
        LLMProvider provider = detector.detectProvider(exchange);

        // Then
        assertThat(provider).isEqualTo(LLMProvider.OPENAI);
    }

    @Test
    void shouldDetectOpenAICompletions() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1/completions")
                .header("Authorization", "Bearer sk-test-key")
        );

        // When
        LLMProvider provider = detector.detectProvider(exchange);

        // Then
        assertThat(provider).isEqualTo(LLMProvider.OPENAI);
    }

    @Test
    void shouldDetectOpenAIEmbeddings() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1/embeddings")
                .header("Authorization", "Bearer sk-test-key")
        );

        // When
        LLMProvider provider = detector.detectProvider(exchange);

        // Then
        assertThat(provider).isEqualTo(LLMProvider.OPENAI);
    }

    @Test
    void shouldDetectAnthropicMessages() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1/messages")
                .header("x-api-key", "sk-ant-test-key")
        );

        // When
        LLMProvider provider = detector.detectProvider(exchange);

        // Then
        assertThat(provider).isEqualTo(LLMProvider.ANTHROPIC);
    }

    @Test
    void shouldDetectAnthropicComplete() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1/complete")
                .header("x-api-key", "sk-ant-test-key")
        );

        // When
        LLMProvider provider = detector.detectProvider(exchange);

        // Then
        assertThat(provider).isEqualTo(LLMProvider.ANTHROPIC);
    }

    @Test
    void shouldDetectGoogleAI() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1beta/models/gemini-pro/generateContent")
                .header("x-goog-api-key", "test-google-key")
        );

        // When
        LLMProvider provider = detector.detectProvider(exchange);

        // Then
        assertThat(provider).isEqualTo(LLMProvider.GOOGLE_AI);
    }

    @Test
    void shouldReturnUnknownForInvalidPath() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/invalid/path")
                .header("Authorization", "Bearer sk-test-key")
        );

        // When
        LLMProvider provider = detector.detectProvider(exchange);

        // Then
        assertThat(provider).isEqualTo(LLMProvider.UNKNOWN);
    }

    @Test
    void shouldReturnUnknownForMissingHeaders() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1/chat/completions")
        );

        // When
        LLMProvider provider = detector.detectProvider(exchange);

        // Then
        assertThat(provider).isEqualTo(LLMProvider.UNKNOWN);
    }

    @Test
    void shouldExtractOpenAIApiKey() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1/chat/completions")
                .header("Authorization", "Bearer sk-test-openai-key")
        );

        // When
        String apiKey = detector.extractApiKey(exchange, LLMProvider.OPENAI);

        // Then
        assertThat(apiKey).isEqualTo("sk-test-openai-key");
    }

    @Test
    void shouldExtractAnthropicApiKey() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1/messages")
                .header("x-api-key", "sk-ant-test-key")
        );

        // When
        String apiKey = detector.extractApiKey(exchange, LLMProvider.ANTHROPIC);

        // Then
        assertThat(apiKey).isEqualTo("sk-ant-test-key");
    }

    @Test
    void shouldExtractGoogleAIApiKeyFromHeader() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1beta/models/gemini-pro/generateContent")
                .header("x-goog-api-key", "google-test-key")
        );

        // When
        String apiKey = detector.extractApiKey(exchange, LLMProvider.GOOGLE_AI);

        // Then
        assertThat(apiKey).isEqualTo("google-test-key");
    }

    @Test
    void shouldExtractGoogleAIApiKeyFromQueryParam() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1beta/models/gemini-pro/generateContent?key=google-query-key")
        );

        // When
        String apiKey = detector.extractApiKey(exchange, LLMProvider.GOOGLE_AI);

        // Then
        assertThat(apiKey).isEqualTo("google-query-key");
    }

    @Test
    void shouldReturnNullForMissingApiKey() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1/chat/completions")
        );

        // When
        String apiKey = detector.extractApiKey(exchange, LLMProvider.OPENAI);

        // Then
        assertThat(apiKey).isNull();
    }

    @Test
    void shouldReturnNullForInvalidBearerToken() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1/chat/completions")
                .header("Authorization", "Invalid sk-test-key")
        );

        // When
        String apiKey = detector.extractApiKey(exchange, LLMProvider.OPENAI);

        // Then
        assertThat(apiKey).isNull();
    }

    @Test
    void shouldGetCorrectProviderBaseUris() {
        assertThat(detector.getProviderBaseUri(LLMProvider.OPENAI))
            .isEqualTo("https://api.openai.com");
        
        assertThat(detector.getProviderBaseUri(LLMProvider.ANTHROPIC))
            .isEqualTo("https://api.anthropic.com");
        
        assertThat(detector.getProviderBaseUri(LLMProvider.GOOGLE_AI))
            .isEqualTo("https://generativelanguage.googleapis.com");
        
        assertThat(detector.getProviderBaseUri(LLMProvider.UNKNOWN))
            .isNull();
    }

    @Test
    void shouldValidateOpenAIApiKeyFormat() {
        assertThat(detector.isValidApiKeyFormat("sk-test-key-with-sufficient-length", LLMProvider.OPENAI))
            .isTrue();
        
        assertThat(detector.isValidApiKeyFormat("sk-short", LLMProvider.OPENAI))
            .isFalse();
        
        assertThat(detector.isValidApiKeyFormat("invalid-prefix-key", LLMProvider.OPENAI))
            .isFalse();
        
        assertThat(detector.isValidApiKeyFormat("", LLMProvider.OPENAI))
            .isFalse();
        
        assertThat(detector.isValidApiKeyFormat(null, LLMProvider.OPENAI))
            .isFalse();
    }

    @Test
    void shouldValidateAnthropicApiKeyFormat() {
        assertThat(detector.isValidApiKeyFormat("sk-ant-test-key-with-sufficient-length", LLMProvider.ANTHROPIC))
            .isTrue();
        
        assertThat(detector.isValidApiKeyFormat("sk-ant-short", LLMProvider.ANTHROPIC))
            .isFalse();
        
        assertThat(detector.isValidApiKeyFormat("sk-test-key", LLMProvider.ANTHROPIC))
            .isFalse();
        
        assertThat(detector.isValidApiKeyFormat("", LLMProvider.ANTHROPIC))
            .isFalse();
    }

    @Test
    void shouldValidateGoogleAIApiKeyFormat() {
        assertThat(detector.isValidApiKeyFormat("google-api-key-with-good-length", LLMProvider.GOOGLE_AI))
            .isTrue();
        
        assertThat(detector.isValidApiKeyFormat("short", LLMProvider.GOOGLE_AI))
            .isFalse();
        
        assertThat(detector.isValidApiKeyFormat("this-key-is-way-too-long-for-google-ai-format-validation", LLMProvider.GOOGLE_AI))
            .isFalse();
        
        assertThat(detector.isValidApiKeyFormat("", LLMProvider.GOOGLE_AI))
            .isFalse();
    }

    @Test
    void shouldGetRequiredHeadersForOpenAI() {
        // When
        Map<String, String> headers = detector.getRequiredHeaders(LLMProvider.OPENAI);

        // Then
        assertThat(headers).containsEntry("content-type", "application/json");
        assertThat(headers).hasSize(1);
    }

    @Test
    void shouldGetRequiredHeadersForAnthropic() {
        // When
        Map<String, String> headers = detector.getRequiredHeaders(LLMProvider.ANTHROPIC);

        // Then
        assertThat(headers).containsEntry("anthropic-version", "2023-06-01");
        assertThat(headers).containsEntry("content-type", "application/json");
        assertThat(headers).hasSize(2);
    }

    @Test
    void shouldGetRequiredHeadersForGoogleAI() {
        // When
        Map<String, String> headers = detector.getRequiredHeaders(LLMProvider.GOOGLE_AI);

        // Then
        assertThat(headers).containsEntry("content-type", "application/json");
        assertThat(headers).hasSize(1);
    }

    @Test
    void shouldHandleEdgeCasePathsCorrectly() {
        // Test paths that are similar but not exact matches
        ServerWebExchange exchange1 = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1/chat/completions/extra")
                .header("Authorization", "Bearer sk-test-key")
        );
        assertThat(detector.detectProvider(exchange1)).isEqualTo(LLMProvider.UNKNOWN);

        ServerWebExchange exchange2 = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v2/chat/completions")
                .header("Authorization", "Bearer sk-test-key")
        );
        assertThat(detector.detectProvider(exchange2)).isEqualTo(LLMProvider.UNKNOWN);

        ServerWebExchange exchange3 = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1beta/models/gemini-pro/generateContent/extra")
                .header("x-goog-api-key", "test-key")
        );
        assertThat(detector.detectProvider(exchange3)).isEqualTo(LLMProvider.UNKNOWN);
    }

    @Test
    void shouldPrioritizeHeaderOverQueryParamForGoogleAI() {
        // Given - both header and query param present
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1beta/models/gemini-pro/generateContent?key=query-key")
                .header("x-goog-api-key", "header-key")
        );

        // When
        String apiKey = detector.extractApiKey(exchange, LLMProvider.GOOGLE_AI);

        // Then - should prefer header
        assertThat(apiKey).isEqualTo("header-key");
    }

    @Test
    void shouldHandleEmptyBearerToken() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1/chat/completions")
                .header("Authorization", "Bearer ")
        );

        // When
        String apiKey = detector.extractApiKey(exchange, LLMProvider.OPENAI);

        // Then
        assertThat(apiKey).isEmpty();
    }

    @Test
    void shouldHandleWhitespaceInBearerToken() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/v1/chat/completions")
                .header("Authorization", "Bearer   sk-test-key   ")
        );

        // When
        String apiKey = detector.extractApiKey(exchange, LLMProvider.OPENAI);

        // Then
        assertThat(apiKey).isEqualTo("  sk-test-key   ");
    }
}
