package com.codepromptu.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new prompt.
 */
@Schema(description = "Request to create a new prompt")
public class CreatePromptRequest {

    @NotBlank(message = "Content is required")
    @Size(max = 10000, message = "Content must not exceed 10000 characters")
    @Schema(description = "The prompt content/text", example = "You are a helpful assistant that explains complex topics in simple terms.", required = true)
    private String content;

    @Schema(description = "Author/creator of the prompt", example = "john.doe@company.com")
    private String author;

    @Schema(description = "Purpose or use case description", example = "Educational content simplification")
    private String purpose;

    @Schema(description = "Success criteria for evaluating prompt effectiveness", example = "Response should be understandable by a 12-year-old")
    private String successCriteria;

    @Schema(description = "Tags for categorization", example = "[\"education\", \"simplification\", \"assistant\"]")
    private String[] tags;

    @Schema(description = "Team or organization owner", example = "education-team")
    private String teamOwner;

    @Schema(description = "Target LLM model", example = "gpt-4")
    private String modelTarget;

    @Schema(description = "Additional metadata as JSON", example = "{\"category\": \"educational\", \"difficulty\": \"beginner\"}")
    private JsonNode metadata;

    // Default constructor
    public CreatePromptRequest() {}

    // Constructor with required fields
    public CreatePromptRequest(String content, String author) {
        this.content = content;
        this.author = author;
    }

    // Getters and setters
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

    public String getSuccessCriteria() {
        return successCriteria;
    }

    public void setSuccessCriteria(String successCriteria) {
        this.successCriteria = successCriteria;
    }

    public String[] getTags() {
        return tags;
    }

    public void setTags(String[] tags) {
        this.tags = tags;
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

    public JsonNode getMetadata() {
        return metadata;
    }

    public void setMetadata(JsonNode metadata) {
        this.metadata = metadata;
    }

    @Override
    public String toString() {
        return "CreatePromptRequest{" +
                "content='" + (content != null ? content.substring(0, Math.min(content.length(), 50)) + "..." : null) + '\'' +
                ", author='" + author + '\'' +
                ", purpose='" + purpose + '\'' +
                ", teamOwner='" + teamOwner + '\'' +
                ", modelTarget='" + modelTarget + '\'' +
                '}';
    }
}
