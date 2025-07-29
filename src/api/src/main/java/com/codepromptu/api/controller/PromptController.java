package com.codepromptu.api.controller;

import com.codepromptu.api.dto.CreatePromptRequest;
import com.codepromptu.api.dto.PromptResponseDto;
import com.codepromptu.api.dto.SimilarPrompt;
import com.codepromptu.api.dto.SimilaritySearchRequest;
import com.codepromptu.api.service.PromptService;
import com.codepromptu.api.service.SimilarityService;
import com.codepromptu.api.util.PromptMapper;
import com.codepromptu.shared.domain.Prompt;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for prompt management operations.
 * Provides CRUD operations, similarity search, and forking functionality.
 */
@RestController
@RequestMapping("/api/v1/prompts")
@Validated
@Tag(name = "Prompts", description = "Prompt management operations")
public class PromptController {

    private static final Logger logger = LoggerFactory.getLogger(PromptController.class);

    private final PromptService promptService;
    private final PromptMapper promptMapper;

    @Autowired
    public PromptController(PromptService promptService, PromptMapper promptMapper) {
        this.promptService = promptService;
        this.promptMapper = promptMapper;
        logger.info("PromptController initialized");
    }

    @Operation(summary = "Get all prompts", description = "Retrieve all active prompts")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved prompts"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping
    public ResponseEntity<List<PromptResponseDto>> getAllPrompts() {
        try {
            List<Prompt> prompts = promptService.getAllPrompts();
            List<PromptResponseDto> promptDtos = prompts.stream()
                .map(promptMapper::toResponseDto)
                .collect(Collectors.toList());
            return ResponseEntity.ok(promptDtos);
        } catch (Exception e) {
            logger.error("Failed to retrieve prompts: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get prompt by ID", description = "Retrieve a specific prompt by its ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Prompt found"),
        @ApiResponse(responseCode = "404", description = "Prompt not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{id}")
    public ResponseEntity<PromptResponseDto> getPromptById(
            @Parameter(description = "Prompt ID") @PathVariable UUID id) {
        try {
            Optional<Prompt> prompt = promptService.getPromptById(id);
            return prompt.map(p -> ResponseEntity.ok(promptMapper.toResponseDto(p)))
                         .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (Exception e) {
            logger.error("Failed to retrieve prompt {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Create a new prompt", description = "Create a new prompt with automatic embedding generation")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Prompt created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping
    public ResponseEntity<PromptResponseDto> createPrompt(
            @Parameter(description = "Prompt creation request") @Valid @RequestBody CreatePromptRequest request) {
        try {
            // Convert DTO to entity
            Prompt prompt = new Prompt();
            prompt.setContent(request.getContent());
            prompt.setAuthor(request.getAuthor());
            prompt.setPurpose(request.getPurpose());
            prompt.setSuccessCriteria(request.getSuccessCriteria());
            prompt.setTags(request.getTags());
            prompt.setTeamOwner(request.getTeamOwner());
            prompt.setModelTarget(request.getModelTarget());
            prompt.setMetadata(request.getMetadata());

            Prompt created = promptService.createPrompt(prompt);
            PromptResponseDto responseDto = promptMapper.toResponseDto(created);
            return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid prompt creation request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Failed to create prompt: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Update a prompt", description = "Update an existing prompt with automatic embedding regeneration if content changed")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Prompt updated successfully"),
        @ApiResponse(responseCode = "404", description = "Prompt not found"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PutMapping("/{id}")
    public ResponseEntity<PromptResponseDto> updatePrompt(
            @Parameter(description = "Prompt ID") @PathVariable UUID id,
            @Parameter(description = "Updated prompt data") @Valid @RequestBody CreatePromptRequest request) {
        try {
            // Convert DTO to entity
            Prompt updatedPrompt = new Prompt();
            updatedPrompt.setContent(request.getContent());
            updatedPrompt.setAuthor(request.getAuthor());
            updatedPrompt.setPurpose(request.getPurpose());
            updatedPrompt.setSuccessCriteria(request.getSuccessCriteria());
            updatedPrompt.setTags(request.getTags());
            updatedPrompt.setTeamOwner(request.getTeamOwner());
            updatedPrompt.setModelTarget(request.getModelTarget());
            updatedPrompt.setMetadata(request.getMetadata());

            Optional<Prompt> updated = promptService.updatePrompt(id, updatedPrompt);
            return updated.map(p -> ResponseEntity.ok(promptMapper.toResponseDto(p)))
                          .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid prompt update request for {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Failed to update prompt {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Delete a prompt", description = "Soft delete a prompt by setting it as inactive")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Prompt deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Prompt not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePrompt(
            @Parameter(description = "Prompt ID") @PathVariable UUID id) {
        try {
            boolean deleted = promptService.deletePrompt(id);
            return deleted ? ResponseEntity.noContent().build() 
                           : ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Failed to delete prompt {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Fork a prompt", description = "Create a child prompt (fork) from an existing prompt")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Prompt forked successfully"),
        @ApiResponse(responseCode = "404", description = "Parent prompt not found"),
        @ApiResponse(responseCode = "400", description = "Invalid fork request"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/{id}/fork")
    public ResponseEntity<PromptResponseDto> forkPrompt(
            @Parameter(description = "Parent prompt ID") @PathVariable UUID id,
            @Parameter(description = "New content for the fork") @RequestParam String content,
            @Parameter(description = "Author of the fork") @RequestParam String author) {
        try {
            if (content == null || content.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            if (author == null || author.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            Optional<Prompt> forked = promptService.forkPrompt(id, content, author);
            return forked.map(prompt -> ResponseEntity.status(HttpStatus.CREATED).body(promptMapper.toResponseDto(prompt)))
                         .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (Exception e) {
            logger.error("Failed to fork prompt {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Find similar prompts", description = "Find prompts similar to a given prompt")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Similar prompts found"),
        @ApiResponse(responseCode = "404", description = "Prompt not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{id}/similar")
    public ResponseEntity<List<SimilarPrompt>> findSimilarPrompts(
            @Parameter(description = "Prompt ID") @PathVariable UUID id,
            @Parameter(description = "Maximum number of results") @RequestParam(defaultValue = "10") int limit) {
        try {
            if (limit < 1 || limit > 100) {
                return ResponseEntity.badRequest().build();
            }

            List<SimilarityService.SimilarPromptResult> results = promptService.findSimilarPrompts(id, limit);
            List<SimilarPrompt> dtos = results.stream()
                    .map(SimilarPrompt::new)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            logger.error("Failed to find similar prompts for {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Search similar prompts", description = "Search for prompts similar to given content")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Similar prompts found"),
        @ApiResponse(responseCode = "400", description = "Invalid search request"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/search/similar")
    public ResponseEntity<List<SimilarPrompt>> searchSimilarPrompts(
            @Parameter(description = "Similarity search request") @Valid @RequestBody SimilaritySearchRequest request) {
        try {
            List<SimilarityService.SimilarPromptResult> results = 
                promptService.findSimilarPrompts(request.getContent(), request.getLimit());
            
            // Filter by minimum similarity if specified
            if (request.getMinSimilarity() > 0.0) {
                results = results.stream()
                        .filter(result -> result.getSimilarityScore() >= request.getMinSimilarity())
                        .collect(Collectors.toList());
            }

            List<SimilarPrompt> dtos = results.stream()
                    .map(SimilarPrompt::new)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(dtos);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid similarity search request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Failed to search similar prompts: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Classify prompt", description = "Classify a prompt based on similarity to existing prompts")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Prompt classified successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid classification request"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/classify")
    public ResponseEntity<SimilarityService.PromptClassificationResult> classifyPrompt(
            @Parameter(description = "Content to classify") @RequestParam String content) {
        try {
            if (content == null || content.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            SimilarityService.PromptClassificationResult result = promptService.classifyPrompt(content);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Failed to classify prompt: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get prompts by team", description = "Retrieve prompts owned by a specific team")
    @GetMapping("/team/{teamOwner}")
    public ResponseEntity<List<PromptResponseDto>> getPromptsByTeam(
            @Parameter(description = "Team owner") @PathVariable String teamOwner) {
        try {
            List<Prompt> prompts = promptService.getPromptsByTeam(teamOwner);
            List<PromptResponseDto> promptDtos = prompts.stream()
                .map(promptMapper::toResponseDto)
                .collect(Collectors.toList());
            return ResponseEntity.ok(promptDtos);
        } catch (Exception e) {
            logger.error("Failed to retrieve prompts for team {}: {}", teamOwner, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get prompts by author", description = "Retrieve prompts created by a specific author")
    @GetMapping("/author/{author}")
    public ResponseEntity<List<PromptResponseDto>> getPromptsByAuthor(
            @Parameter(description = "Author") @PathVariable String author) {
        try {
            List<Prompt> prompts = promptService.getPromptsByAuthor(author);
            List<PromptResponseDto> promptDtos = prompts.stream()
                .map(promptMapper::toResponseDto)
                .collect(Collectors.toList());
            return ResponseEntity.ok(promptDtos);
        } catch (Exception e) {
            logger.error("Failed to retrieve prompts for author {}: {}", author, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Search prompts", description = "Search prompts by content, purpose, or author")
    @GetMapping("/search")
    public ResponseEntity<List<PromptResponseDto>> searchPrompts(
            @Parameter(description = "Search term") @RequestParam String q) {
        try {
            if (q == null || q.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            List<Prompt> prompts = promptService.searchPrompts(q.trim());
            List<PromptResponseDto> promptDtos = prompts.stream()
                .map(promptMapper::toResponseDto)
                .collect(Collectors.toList());
            return ResponseEntity.ok(promptDtos);
        } catch (Exception e) {
            logger.error("Failed to search prompts with query '{}': {}", q, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
