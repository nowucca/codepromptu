package com.codepromptu.gateway.service;

import com.codepromptu.gateway.dto.PromptUsageDto;
import com.codepromptu.gateway.model.CaptureContext;
import com.codepromptu.gateway.model.LLMProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PromptCaptureService
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PromptCaptureServiceTest {

    @Mock
    private WebClient.Builder webClientBuilder;
    
    @Mock
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private PromptCaptureService promptCaptureService;

    private MockWebServer mockWebServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        
        // Initialize ObjectMapper
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // Register JavaTimeModule for Instant serialization
        
        // Create WebClient pointing to mock server
        WebClient webClient = WebClient.builder()
            .baseUrl(mockWebServer.url("/").toString())
            .build();
        
        // Mock the WebClient.Builder to return our test WebClient
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        
        // Set the apiBaseUrl field and inject ObjectMapper
        ReflectionTestUtils.setField(promptCaptureService, "apiBaseUrl", mockWebServer.url("/").toString());
        ReflectionTestUtils.setField(promptCaptureService, "captureEnabled", true);
        ReflectionTestUtils.setField(promptCaptureService, "redisFallbackEnabled", true);
        ReflectionTestUtils.setField(promptCaptureService, "objectMapper", objectMapper);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void capturePromptUsage_SuccessfulApiCall_CompletesSuccessfully() throws Exception {
        // Given
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader(HttpHeaders.CONTENT_TYPE, "application/json"));

        CaptureContext context = createTestCaptureContext();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getStatusCode()).thenReturn(HttpStatus.OK);

        // When
        Mono<Void> result = promptCaptureService.capturePromptUsage(context, response);

        // Then
        StepVerifier.create(result)
            .verifyComplete();

        // Verify the request was made to the API
        RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(recordedRequest).isNotNull();
        assertThat(recordedRequest.getPath()).isEqualTo("/internal/prompt-usage");
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
        assertThat(recordedRequest.getHeader("Content-Type")).isEqualTo("application/json");

        // Verify the request body contains expected data
        String requestBody = recordedRequest.getBody().readUtf8();
        PromptUsageDto sentUsage = objectMapper.readValue(requestBody, PromptUsageDto.class);
        assertThat(sentUsage.getRawContent()).isEqualTo(context.getRequestBody());
        assertThat(sentUsage.getProvider()).isEqualTo(context.getProvider().name());
        assertThat(sentUsage.getModel()).isEqualTo(context.getModel());
        assertThat(sentUsage.getClientIp()).isEqualTo(context.getClientIp());
        assertThat(sentUsage.getUserAgent()).isEqualTo(context.getUserAgent());
        assertThat(sentUsage.getApiKeyHash()).isEqualTo(context.getApiKeyHash());
    }

    @Test
    void capturePromptUsage_ApiServiceUnavailable_FallsBackToRedis() throws InterruptedException {
        // Given
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(503)
            .setBody("Service Unavailable"));

        ReactiveValueOperations<String, Object> valueOps = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.set(anyString(), any(), any())).thenReturn(Mono.just(true));

        CaptureContext context = createTestCaptureContext();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getStatusCode()).thenReturn(HttpStatus.OK);

        // When
        Mono<Void> result = promptCaptureService.capturePromptUsage(context, response);

        // Then
        StepVerifier.create(result)
            .verifyComplete();

        // Verify the API was called first
        RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(recordedRequest).isNotNull();
        assertThat(recordedRequest.getPath()).isEqualTo("/internal/prompt-usage");
    }

    @Test
    void capturePromptUsage_ApiTimeout_FallsBackToRedis() {
        // Given
        // Don't enqueue any response to simulate timeout
        
        ReactiveValueOperations<String, Object> valueOps = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.set(anyString(), any(), any())).thenReturn(Mono.just(true));

        CaptureContext context = createTestCaptureContext();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getStatusCode()).thenReturn(HttpStatus.OK);

        // When
        Mono<Void> result = promptCaptureService.capturePromptUsage(context, response);

        // Then - Should complete even with timeout (fallback to Redis)
        StepVerifier.create(result)
            .verifyComplete();
    }

    @Test
    void capturePromptUsage_NullContext_CompletesWithoutError() {
        // When
        Mono<Void> result = promptCaptureService.capturePromptUsage(null, mock(ServerHttpResponse.class));

        // Then
        StepVerifier.create(result)
            .verifyComplete();
    }

    @Test
    void capturePromptUsage_EmptyRequestBody_HandledGracefully() throws InterruptedException {
        // Given
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader(HttpHeaders.CONTENT_TYPE, "application/json"));

        CaptureContext context = createTestCaptureContext();
        context.setRequestBody(""); // Empty request body
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getStatusCode()).thenReturn(HttpStatus.OK);

        // When
        Mono<Void> result = promptCaptureService.capturePromptUsage(context, response);

        // Then
        StepVerifier.create(result)
            .verifyComplete();

        // Verify the request was still made
        RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(recordedRequest).isNotNull();
    }

    @Test
    void capturePromptUsage_DifferentProviders_HandledCorrectly() throws Exception {
        // Test different LLM providers
        LLMProvider[] providers = {LLMProvider.OPENAI, LLMProvider.ANTHROPIC, LLMProvider.GOOGLE_AI, LLMProvider.UNKNOWN};
        
        for (LLMProvider provider : providers) {
            // Given
            mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader(HttpHeaders.CONTENT_TYPE, "application/json"));

            CaptureContext context = createTestCaptureContext();
            context.setProvider(provider);
            ServerHttpResponse response = mock(ServerHttpResponse.class);
            when(response.getStatusCode()).thenReturn(HttpStatus.OK);

            // When
            Mono<Void> result = promptCaptureService.capturePromptUsage(context, response);

            // Then
            StepVerifier.create(result)
                .verifyComplete();

            // Verify the request contains the correct provider
            RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
            assertThat(recordedRequest).isNotNull();
            
            String requestBody = recordedRequest.getBody().readUtf8();
            PromptUsageDto sentUsage = objectMapper.readValue(requestBody, PromptUsageDto.class);
            assertThat(sentUsage.getProvider()).isEqualTo(provider.name());
        }
    }

    @Test
    void capturePromptUsage_WithMetadata_IncludesAllData() throws Exception {
        // Given
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader(HttpHeaders.CONTENT_TYPE, "application/json"));

        CaptureContext context = createTestCaptureContext();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("temperature", 0.8);
        metadata.put("max_tokens", 1000);
        metadata.put("custom_field", "test_value");
        context.setMetadata(metadata);

        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getStatusCode()).thenReturn(HttpStatus.OK);

        // When
        Mono<Void> result = promptCaptureService.capturePromptUsage(context, response);

        // Then
        StepVerifier.create(result)
            .verifyComplete();

        // Verify metadata is included
        RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        String requestBody = recordedRequest.getBody().readUtf8();
        PromptUsageDto sentUsage = objectMapper.readValue(requestBody, PromptUsageDto.class);
        
        assertThat(sentUsage.getMetadata()).isNotNull();
        assertThat(sentUsage.getMetadata()).containsEntry("temperature", 0.8);
        assertThat(sentUsage.getMetadata()).containsEntry("max_tokens", 1000);
        assertThat(sentUsage.getMetadata()).containsEntry("custom_field", "test_value");
    }

    private CaptureContext createTestCaptureContext() {
        CaptureContext context = new CaptureContext();
        context.setRequestBody("{\"model\": \"gpt-4\", \"messages\": [{\"role\": \"user\", \"content\": \"Test prompt\"}]}");
        context.setProvider(LLMProvider.OPENAI);
        context.setModel("gpt-4");
        context.setRequestTime(Instant.now().minusSeconds(5));
        context.setClientIp("192.168.1.100");
        context.setUserAgent("Mozilla/5.0 (compatible; CodePromptu/1.0)");
        context.setApiKeyHash("sha256:test123hash");
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("temperature", 0.7);
        metadata.put("max_tokens", 150);
        context.setMetadata(metadata);
        
        return context;
    }
}
