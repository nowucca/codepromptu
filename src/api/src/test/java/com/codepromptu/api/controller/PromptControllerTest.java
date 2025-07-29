package com.codepromptu.api.controller;

import com.codepromptu.api.config.TestSecurityConfig;
import com.codepromptu.api.dto.CreatePromptRequest;
import com.codepromptu.api.dto.PromptResponseDto;
import com.codepromptu.api.dto.SimilarPrompt;
import com.codepromptu.api.dto.SimilaritySearchRequest;
import com.codepromptu.api.service.PromptService;
import com.codepromptu.api.service.SimilarityService;
import com.codepromptu.api.util.PromptMapper;
import com.codepromptu.shared.domain.Prompt;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive test suite for PromptController with DTO architecture.
 * Tests all endpoints with proper authentication and DTO serialization.
 */
@WebMvcTest(PromptController.class)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@DisplayName("Prompt Controller API Tests - DTO Architecture")
public class PromptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PromptService promptService;

    @MockBean
    private PromptMapper promptMapper;

    private Prompt samplePrompt;
    private PromptResponseDto sampleResponseDto;
    private CreatePromptRequest sampleCreateRequest;
    private UUID samplePromptId;

    @BeforeEach
    void setUp() {
        samplePromptId = UUID.randomUUID();
        
        // Sample entity
        samplePrompt = Prompt.builder()
                .id(samplePromptId)
                .content("You are a helpful coding assistant. Please help with: {question}")
                .author("jane.developer")
                .purpose("Code assistance for development team")
                .teamOwner("engineering-team")
                .modelTarget("gpt-4")
                .version(1)
                .isActive(true)
                .tags(new String[]{"coding", "assistance", "development"})
                .successCriteria("Should provide accurate, helpful coding guidance")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Sample response DTO
        sampleResponseDto = new PromptResponseDto();
        sampleResponseDto.setId(samplePromptId);
        sampleResponseDto.setContent("You are a helpful coding assistant. Please help with: {question}");
        sampleResponseDto.setAuthor("jane.developer");
        sampleResponseDto.setPurpose("Code assistance for development team");
        sampleResponseDto.setTeamOwner("engineering-team");
        sampleResponseDto.setModelTarget("gpt-4");
        sampleResponseDto.setVersion(1);
        sampleResponseDto.setIsActive(true);
        sampleResponseDto.setTags(new String[]{"coding", "assistance", "development"});
        sampleResponseDto.setSuccessCriteria("Should provide accurate, helpful coding guidance");
        sampleResponseDto.setCreatedAt(Instant.now());
        sampleResponseDto.setUpdatedAt(Instant.now());
        sampleResponseDto.setParentId(null);
        sampleResponseDto.setHasChildren(false);
        sampleResponseDto.setDepth(0);
        sampleResponseDto.setRoot(true);

        // Sample create request
        sampleCreateRequest = new CreatePromptRequest();
        sampleCreateRequest.setContent("You are a helpful coding assistant. Please help with: {question}");
        sampleCreateRequest.setAuthor("jane.developer");
        sampleCreateRequest.setPurpose("Code assistance for development team");
        sampleCreateRequest.setTeamOwner("engineering-team");
        sampleCreateRequest.setModelTarget("gpt-4");
        sampleCreateRequest.setTags(new String[]{"coding", "assistance", "development"});
        sampleCreateRequest.setSuccessCriteria("Should provide accurate, helpful coding guidance");
    }

    @Nested
    @DisplayName("GET /api/v1/prompts - Retrieve All Prompts")
    class GetAllPromptsTests {

        @Test
        @DisplayName("Should return list of prompts with DTO conversion")
        void shouldReturnListOfPromptsWithDtoConversion() throws Exception {
            // Given
            List<Prompt> promptList = Collections.singletonList(samplePrompt);
            
            when(promptService.getAllPrompts()).thenReturn(promptList);
            when(promptMapper.toResponseDto(samplePrompt)).thenReturn(sampleResponseDto);

            // When & Then
            mockMvc.perform(get("/api/v1/prompts")
                    .with(httpBasic("codepromptu", "codepromptu"))
                    .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id", is(samplePromptId.toString())))
                    .andExpect(jsonPath("$[0].content", is("You are a helpful coding assistant. Please help with: {question}")))
                    .andExpect(jsonPath("$[0].author", is("jane.developer")))
                    .andExpect(jsonPath("$[0].root", is(true)))
                    .andExpect(jsonPath("$[0].depth", is(0)));

            verify(promptService, times(1)).getAllPrompts();
            verify(promptMapper, times(1)).toResponseDto(samplePrompt);
        }

        @Test
        @DisplayName("Should return empty list when no prompts exist")
        void shouldReturnEmptyListWhenNoPromptsExist() throws Exception {
            // Given
            List<Prompt> emptyList = Collections.emptyList();
            
            when(promptService.getAllPrompts()).thenReturn(emptyList);

            // When & Then
            mockMvc.perform(get("/api/v1/prompts")
                    .with(httpBasic("codepromptu", "codepromptu"))
                    .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(0)));

            verify(promptService, times(1)).getAllPrompts();
            verify(promptMapper, never()).toResponseDto(any());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/prompts/{id} - Retrieve Specific Prompt")
    class GetPromptByIdTests {

        @Test
        @DisplayName("Should return prompt DTO when valid ID is provided")
        void shouldReturnPromptDtoWhenValidIdProvided() throws Exception {
            // Given
            when(promptService.getPromptById(samplePromptId)).thenReturn(Optional.of(samplePrompt));
            when(promptMapper.toResponseDto(samplePrompt)).thenReturn(sampleResponseDto);

            // When & Then
            mockMvc.perform(get("/api/v1/prompts/{id}", samplePromptId)
                    .with(httpBasic("codepromptu", "codepromptu"))
                    .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id", is(samplePromptId.toString())))
                    .andExpect(jsonPath("$.content", is("You are a helpful coding assistant. Please help with: {question}")))
                    .andExpect(jsonPath("$.author", is("jane.developer")))
                    .andExpect(jsonPath("$.purpose", is("Code assistance for development team")))
                    .andExpect(jsonPath("$.teamOwner", is("engineering-team")))
                    .andExpect(jsonPath("$.modelTarget", is("gpt-4")))
                    .andExpect(jsonPath("$.version", is(1)))
                    .andExpect(jsonPath("$.isActive", is(true)))
                    .andExpect(jsonPath("$.tags", hasSize(3)))
                    .andExpect(jsonPath("$.tags", containsInAnyOrder("coding", "assistance", "development")))
                    .andExpect(jsonPath("$.root", is(true)))
                    .andExpect(jsonPath("$.depth", is(0)));

            verify(promptService, times(1)).getPromptById(samplePromptId);
            verify(promptMapper, times(1)).toResponseDto(samplePrompt);
        }

        @Test
        @DisplayName("Should return 404 when prompt does not exist")
        void shouldReturn404WhenPromptDoesNotExist() throws Exception {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            when(promptService.getPromptById(nonExistentId)).thenReturn(Optional.empty());

            // When & Then
            mockMvc.perform(get("/api/v1/prompts/{id}", nonExistentId)
                    .with(httpBasic("codepromptu", "codepromptu"))
                    .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isNotFound());

            verify(promptService, times(1)).getPromptById(nonExistentId);
            verify(promptMapper, never()).toResponseDto(any());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/prompts - Create New Prompt")
    class CreatePromptTests {

        @Test
        @DisplayName("Should create prompt successfully and return DTO")
        void shouldCreatePromptSuccessfullyAndReturnDto() throws Exception {
            // Given
            when(promptService.createPrompt(any(Prompt.class))).thenReturn(samplePrompt);
            when(promptMapper.toResponseDto(samplePrompt)).thenReturn(sampleResponseDto);

            // When & Then
            mockMvc.perform(post("/api/v1/prompts")
                    .with(httpBasic("codepromptu", "codepromptu"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sampleCreateRequest)))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id", is(samplePromptId.toString())))
                    .andExpect(jsonPath("$.content", is("You are a helpful coding assistant. Please help with: {question}")))
                    .andExpect(jsonPath("$.author", is("jane.developer")))
                    .andExpect(jsonPath("$.version", is(1)))
                    .andExpect(jsonPath("$.isActive", is(true)))
                    .andExpect(jsonPath("$.root", is(true)));

            verify(promptService, times(1)).createPrompt(any(Prompt.class));
            verify(promptMapper, times(1)).toResponseDto(samplePrompt);
        }

        @Test
        @DisplayName("Should reject prompt creation with missing required fields")
        void shouldRejectPromptCreationWithMissingRequiredFields() throws Exception {
            // Given - Request with missing content
            CreatePromptRequest invalidRequest = new CreatePromptRequest();
            invalidRequest.setAuthor("test.user");
            invalidRequest.setPurpose("Testing");

            // When & Then
            mockMvc.perform(post("/api/v1/prompts")
                    .with(httpBasic("codepromptu", "codepromptu"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());

            verify(promptService, never()).createPrompt(any(Prompt.class));
            verify(promptMapper, never()).toResponseDto(any());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/prompts/{id} - Update Prompt")
    class UpdatePromptTests {

        @Test
        @DisplayName("Should update prompt successfully and return DTO")
        void shouldUpdatePromptSuccessfullyAndReturnDto() throws Exception {
            // Given
            when(promptService.updatePrompt(eq(samplePromptId), any(Prompt.class))).thenReturn(Optional.of(samplePrompt));
            when(promptMapper.toResponseDto(samplePrompt)).thenReturn(sampleResponseDto);

            // When & Then
            mockMvc.perform(put("/api/v1/prompts/{id}", samplePromptId)
                    .with(httpBasic("codepromptu", "codepromptu"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sampleCreateRequest)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id", is(samplePromptId.toString())))
                    .andExpect(jsonPath("$.content", is("You are a helpful coding assistant. Please help with: {question}")));

            verify(promptService, times(1)).updatePrompt(eq(samplePromptId), any(Prompt.class));
            verify(promptMapper, times(1)).toResponseDto(samplePrompt);
        }

        @Test
        @DisplayName("Should return 404 when updating non-existent prompt")
        void shouldReturn404WhenUpdatingNonExistentPrompt() throws Exception {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            when(promptService.updatePrompt(eq(nonExistentId), any(Prompt.class))).thenReturn(Optional.empty());

            // When & Then
            mockMvc.perform(put("/api/v1/prompts/{id}", nonExistentId)
                    .with(httpBasic("codepromptu", "codepromptu"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sampleCreateRequest)))
                    .andDo(print())
                    .andExpect(status().isNotFound());

            verify(promptService, times(1)).updatePrompt(eq(nonExistentId), any(Prompt.class));
            verify(promptMapper, never()).toResponseDto(any());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/prompts/{id} - Delete Prompt")
    class DeletePromptTests {

        @Test
        @DisplayName("Should delete prompt successfully")
        void shouldDeletePromptSuccessfully() throws Exception {
            // Given
            when(promptService.deletePrompt(samplePromptId)).thenReturn(true);

            // When & Then
            mockMvc.perform(delete("/api/v1/prompts/{id}", samplePromptId)
                    .with(httpBasic("codepromptu", "codepromptu")))
                    .andDo(print())
                    .andExpect(status().isNoContent());

            verify(promptService, times(1)).deletePrompt(samplePromptId);
        }

        @Test
        @DisplayName("Should return 404 when deleting non-existent prompt")
        void shouldReturn404WhenDeletingNonExistentPrompt() throws Exception {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            when(promptService.deletePrompt(nonExistentId)).thenReturn(false);

            // When & Then
            mockMvc.perform(delete("/api/v1/prompts/{id}", nonExistentId)
                    .with(httpBasic("codepromptu", "codepromptu")))
                    .andDo(print())
                    .andExpect(status().isNotFound());

            verify(promptService, times(1)).deletePrompt(nonExistentId);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/prompts/{id}/fork - Fork Prompt")
    class ForkPromptTests {

        @Test
        @DisplayName("Should fork prompt successfully and return DTO")
        void shouldForkPromptSuccessfullyAndReturnDto() throws Exception {
            // Given
            Prompt forkedPrompt = Prompt.builder()
                    .id(UUID.randomUUID())
                    .content("Modified content for fork")
                    .author("fork.author")
                    .parent(samplePrompt)
                    .version(2)
                    .isActive(true)
                    .build();

            PromptResponseDto forkedDto = new PromptResponseDto();
            forkedDto.setId(forkedPrompt.getId());
            forkedDto.setContent("Modified content for fork");
            forkedDto.setAuthor("fork.author");
            forkedDto.setParentId(samplePromptId);
            forkedDto.setVersion(2);
            forkedDto.setIsActive(true);
            forkedDto.setRoot(false);
            forkedDto.setDepth(1);

            when(promptService.forkPrompt(samplePromptId, "Modified content for fork", "fork.author"))
                    .thenReturn(Optional.of(forkedPrompt));
            when(promptMapper.toResponseDto(forkedPrompt)).thenReturn(forkedDto);

            // When & Then
            mockMvc.perform(post("/api/v1/prompts/{id}/fork", samplePromptId)
                    .with(httpBasic("codepromptu", "codepromptu"))
                    .param("content", "Modified content for fork")
                    .param("author", "fork.author"))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.content", is("Modified content for fork")))
                    .andExpect(jsonPath("$.author", is("fork.author")))
                    .andExpect(jsonPath("$.parentId", is(samplePromptId.toString())))
                    .andExpect(jsonPath("$.root", is(false)))
                    .andExpect(jsonPath("$.depth", is(1)));

            verify(promptService, times(1)).forkPrompt(samplePromptId, "Modified content for fork", "fork.author");
            verify(promptMapper, times(1)).toResponseDto(forkedPrompt);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/prompts/search - Search Prompts")
    class SearchPromptsTests {

        @Test
        @DisplayName("Should search prompts and return list of DTOs")
        void shouldSearchPromptsAndReturnListOfDtos() throws Exception {
            // Given
            String searchQuery = "coding assistant";
            List<Prompt> searchResults = Collections.singletonList(samplePrompt);
            
            when(promptService.searchPrompts(eq(searchQuery))).thenReturn(searchResults);
            when(promptMapper.toResponseDto(samplePrompt)).thenReturn(sampleResponseDto);

            // When & Then
            mockMvc.perform(get("/api/v1/prompts/search")
                    .with(httpBasic("codepromptu", "codepromptu"))
                    .param("q", searchQuery))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].content", containsString("coding assistant")));

            verify(promptService, times(1)).searchPrompts(eq(searchQuery));
            verify(promptMapper, times(1)).toResponseDto(samplePrompt);
        }

        @Test
        @DisplayName("Should return 400 for empty search query")
        void shouldReturn400ForEmptySearchQuery() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/v1/prompts/search")
                    .with(httpBasic("codepromptu", "codepromptu"))
                    .param("q", ""))
                    .andDo(print())
                    .andExpect(status().isBadRequest());

            verify(promptService, never()).searchPrompts(any());
        }
    }

    @Nested
    @DisplayName("Security and Authentication Tests")
    class SecurityTests {

        @Test
        @DisplayName("Should reject requests without authentication")
        void shouldRejectRequestsWithoutAuthentication() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/v1/prompts")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());

            verify(promptService, never()).getAllPrompts();
        }

        @Test
        @DisplayName("Should reject requests with invalid credentials")
        void shouldRejectRequestsWithInvalidCredentials() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/v1/prompts")
                    .with(httpBasic("invalid", "credentials"))
                    .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());

            verify(promptService, never()).getAllPrompts();
        }

        @Test
        @DisplayName("Should accept requests with valid hardcoded credentials")
        void shouldAcceptRequestsWithValidHardcodedCredentials() throws Exception {
            // Given
            List<Prompt> emptyList = Collections.emptyList();
            when(promptService.getAllPrompts()).thenReturn(emptyList);

            // When & Then
            mockMvc.perform(get("/api/v1/prompts")
                    .with(httpBasic("codepromptu", "codepromptu"))
                    .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk());

            verify(promptService, times(1)).getAllPrompts();
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle service exceptions gracefully")
        void shouldHandleServiceExceptionsGracefully() throws Exception {
            // Given
            when(promptService.getAllPrompts())
                    .thenThrow(new RuntimeException("Database connection failed"));

            // When & Then
            mockMvc.perform(get("/api/v1/prompts")
                    .with(httpBasic("codepromptu", "codepromptu"))
                    .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isInternalServerError());

            verify(promptService, times(1)).getAllPrompts();
        }

        @Test
        @DisplayName("Should handle invalid JSON in request body")
        void shouldHandleInvalidJsonInRequestBody() throws Exception {
            // When & Then
            mockMvc.perform(post("/api/v1/prompts")
                    .with(httpBasic("codepromptu", "codepromptu"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{ invalid json }"))
                    .andDo(print())
                    .andExpect(status().isBadRequest());

            verify(promptService, never()).createPrompt(any());
        }
    }
}
