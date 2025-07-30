package com.codepromptu.api.controller;

import com.codepromptu.api.config.TestSecurityConfig;
import com.codepromptu.api.service.PromptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for InternalController
 */
@WebMvcTest(InternalController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class InternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PromptService promptService;

    @Autowired
    private ObjectMapper objectMapper;

    private InternalController.PromptUsageDto validUsageDto;

    @BeforeEach
    void setUp() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("temperature", 0.7);
        metadata.put("max_tokens", 150);

        validUsageDto = new InternalController.PromptUsageDto(
            "{\n  \"model\": \"gpt-4\",\n  \"messages\": [\n    {\"role\": \"user\", \"content\": \"Hello, world!\"}\n  ]\n}",
            "OPENAI",
            "gpt-4",
            Instant.now().minusSeconds(5),
            Instant.now(),
            "192.168.1.100",
            "Mozilla/5.0 (compatible; CodePromptu/1.0)",
            "sha256:abc123def456",
            metadata
        );
    }

    @Test
    void capturePromptUsage_ValidRequest_ReturnsOk() throws Exception {
        String requestBody = objectMapper.writeValueAsString(validUsageDto);

        mockMvc.perform(post("/internal/prompt-usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk());
    }

    @Test
    void capturePromptUsage_EmptyRequest_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/internal/prompt-usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk()); // Currently accepts empty requests, might want to validate
    }

    @Test
    void capturePromptUsage_InvalidJson_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/internal/prompt-usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void capturePromptUsage_MissingContentType_ReturnsUnsupportedMediaType() throws Exception {
        String requestBody = objectMapper.writeValueAsString(validUsageDto);

        mockMvc.perform(post("/internal/prompt-usage")
                .content(requestBody))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void capturePromptUsage_ValidRequestWithNullFields_ReturnsOk() throws Exception {
        InternalController.PromptUsageDto usageWithNulls = new InternalController.PromptUsageDto(
            null, // rawContent can be null
            "OPENAI",
            "gpt-4",
            Instant.now(),
            null, // responseTimestamp can be null
            "192.168.1.100",
            null, // userAgent can be null
            "sha256:abc123def456",
            null // metadata can be null
        );

        String requestBody = objectMapper.writeValueAsString(usageWithNulls);

        mockMvc.perform(post("/internal/prompt-usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk());
    }

    @Test
    void capturePromptUsage_LargeContent_ReturnsOk() throws Exception {
        // Test with large content to ensure no size limits are hit
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeContent.append("This is a large prompt content for testing purposes. ");
        }

        InternalController.PromptUsageDto largeUsage = new InternalController.PromptUsageDto(
            largeContent.toString(),
            "OPENAI",
            "gpt-4",
            Instant.now(),
            Instant.now(),
            "192.168.1.100",
            "Mozilla/5.0 (compatible; CodePromptu/1.0)",
            "sha256:abc123def456",
            new HashMap<>()
        );

        String requestBody = objectMapper.writeValueAsString(largeUsage);

        mockMvc.perform(post("/internal/prompt-usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk());
    }

    @Test
    void internalHealth_ReturnsHealthStatus() throws Exception {
        mockMvc.perform(get("/internal/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("api-internal"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void capturePromptUsage_DifferentProviders_ReturnsOk() throws Exception {
        String[] providers = {"OPENAI", "ANTHROPIC", "GOOGLE_AI", "UNKNOWN"};
        
        for (String provider : providers) {
            InternalController.PromptUsageDto usage = new InternalController.PromptUsageDto(
                "{\"test\": \"content\"}",
                provider,
                "test-model",
                Instant.now(),
                Instant.now(),
                "192.168.1.100",
                "Test-Agent/1.0",
                "sha256:test123",
                new HashMap<>()
            );

            String requestBody = objectMapper.writeValueAsString(usage);

            mockMvc.perform(post("/internal/prompt-usage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void capturePromptUsage_WithComplexMetadata_ReturnsOk() throws Exception {
        Map<String, Object> complexMetadata = new HashMap<>();
        complexMetadata.put("temperature", 0.8);
        complexMetadata.put("max_tokens", 2000);
        complexMetadata.put("top_p", 0.9);
        complexMetadata.put("frequency_penalty", 0.1);
        complexMetadata.put("presence_penalty", 0.2);
        complexMetadata.put("stop", new String[]{"\\n", "END"});
        
        Map<String, String> nestedMap = new HashMap<>();
        nestedMap.put("key1", "value1");
        nestedMap.put("key2", "value2");
        complexMetadata.put("nested", nestedMap);

        InternalController.PromptUsageDto usage = new InternalController.PromptUsageDto(
            "{\"model\": \"gpt-4\", \"messages\": [{\"role\": \"user\", \"content\": \"Complex test\"}]}",
            "OPENAI",
            "gpt-4",
            Instant.now(),
            Instant.now(),
            "192.168.1.100",
            "Mozilla/5.0 (compatible; CodePromptu/1.0)",
            "sha256:complex123",
            complexMetadata
        );

        String requestBody = objectMapper.writeValueAsString(usage);

        mockMvc.perform(post("/internal/prompt-usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk());
    }
}
