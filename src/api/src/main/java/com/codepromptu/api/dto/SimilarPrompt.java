package com.codepromptu.api.dto;

import com.codepromptu.api.service.SimilarityService;
import com.codepromptu.shared.domain.Prompt;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for similar prompt search results with similarity score and classification.
 */
@Schema(description = "Similar prompt result with similarity score and classification")
public class SimilarPrompt {

    @Schema(description = "Prompt ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    @Schema(description = "Prompt content", example = "You are a helpful assistant...")
    private String content;

    @Schema(description = "Author of the prompt", example = "john.doe@company.com")
    private String author;

    @Schema(description = "Purpose or use case", example = "Educational content simplification")
    private String purpose;

    @Schema(description = "Team owner", example = "education-team")
    private String teamOwner;

    @Schema(description = "Target model", example = "gpt-4")
    private String modelTarget;

    @Schema(description = "Tags", example = "[\"education\", \"assistant\"]")
    private String[] tags;

    @Schema(description = "Additional metadata")
    private JsonNode metadata;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Similarity score (0.0 to 1.0)", example = "0.87")
    private double similarityScore;

    @Schema(description = "Classification based on similarity", example = "FORK")
    private SimilarityService.PromptClassification classification;

    @Schema(description = "Parent prompt ID if this is a fork", example = "456e7890-e89b-12d3-a456-426614174001")
    private UUID parentId;

    @Schema(description = "Prompt version", example = "2")
    private Integer version;

    // Default constructor
    public SimilarPrompt() {}

    // Constructor from SimilarPromptResult
    public SimilarPrompt(SimilarityService.SimilarPromptResult result) {
        Prompt prompt = result.getPrompt();
        this.id = prompt.getId();
        this.content = prompt.getContent();
        this.author = prompt.getAuthor();
        this.purpose = prompt.getPurpose();
        this.teamOwner = prompt.getTeamOwner();
        this.modelTarget = prompt.getModelTarget();
        this.tags = prompt.getTags();
        this.metadata = prompt.getMetadata();
        this.createdAt = prompt.getCreatedAt();
        this.similarityScore = result.getSimilarityScore();
        this.classification = result.getClassification();
        this.parentId = prompt.getParent() != null ? prompt.getParent().getId() : null;
        this.version = prompt.getVersion();
    }

    // Constructor with all fields
    public SimilarPrompt(UUID id, String content, String author, String purpose, 
                        String teamOwner, String modelTarget, String[] tags, 
                        JsonNode metadata, Instant createdAt, double similarityScore, 
                        SimilarityService.PromptClassification classification, 
                        UUID parentId, Integer version) {
        this.id = id;
        this.content = content;
        this.author = author;
        this.purpose = purpose;
        this.teamOwner = teamOwner;
        this.modelTarget = modelTarget;
        this.tags = tags;
        this.metadata = metadata;
        this.createdAt = createdAt;
        this.similarityScore = similarityScore;
        this.classification = classification;
        this.parentId = parentId;
        this.version = version;
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getTeamOwner() {
        return teamOwner;
    }

    public void setTeamOwner(String teamOwner) {
        this.teamOwner = teamOwner;
    }

    public String getModelTarget() {
        return modelTarget;
    }

    public void setModelTarget(String modelTarget) {
        this.modelTarget = modelTarget;
    }

    public String[] getTags() {
        return tags;
    }

    public void setTags(String[] tags) {
        this.tags = tags;
    }

    public JsonNode getMetadata() {
        return metadata;
    }

    public void setMetadata(JsonNode metadata) {
        this.metadata = metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public double getSimilarityScore() {
        return similarityScore;
    }

    public void setSimilarityScore(double similarityScore) {
        this.similarityScore = similarityScore;
    }

    public SimilarityService.PromptClassification getClassification() {
        return classification;
    }

    public void setClassification(SimilarityService.PromptClassification classification) {
        this.classification = classification;
    }

    public UUID getParentId() {
        return parentId;
    }

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "SimilarPrompt{" +
                "id=" + id +
                ", content='" + (content != null ? content.substring(0, Math.min(content.length(), 50)) + "..." : null) + '\'' +
                ", author='" + author + '\'' +
                ", similarityScore=" + similarityScore +
                ", classification=" + classification +
                ", version=" + version +
                '}';
    }
}
