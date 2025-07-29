package com.codepromptu.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for similarity search operations.
 */
@Schema(description = "Request to search for similar prompts")
public class SimilaritySearchRequest {

    @NotBlank(message = "Content is required for similarity search")
    @Size(max = 10000, message = "Content must not exceed 10000 characters")
    @Schema(description = "The content to find similar prompts for", 
            example = "You are a helpful assistant that explains complex topics.", 
            required = true)
    private String content;

    @Min(value = 1, message = "Limit must be at least 1")
    @Max(value = 100, message = "Limit must not exceed 100")
    @Schema(description = "Maximum number of similar prompts to return", 
            example = "10", 
            defaultValue = "10")
    private int limit = 10;

    @Min(value = 0, message = "Minimum similarity must be between 0.0 and 1.0")
    @Max(value = 1, message = "Minimum similarity must be between 0.0 and 1.0")
    @Schema(description = "Minimum similarity threshold (0.0 to 1.0)", 
            example = "0.7", 
            defaultValue = "0.0")
    private double minSimilarity = 0.0;

    @Schema(description = "Filter by team owner", example = "education-team")
    private String teamOwner;

    @Schema(description = "Filter by author", example = "john.doe@company.com")
    private String author;

    @Schema(description = "Filter by model target", example = "gpt-4")
    private String modelTarget;

    @Schema(description = "Filter by tag", example = "education")
    private String tag;

    @Schema(description = "Include only root prompts (no parents)", defaultValue = "false")
    private boolean rootOnly = false;

    // Default constructor
    public SimilaritySearchRequest() {}

    // Constructor with required fields
    public SimilaritySearchRequest(String content) {
        this.content = content;
    }

    // Constructor with content and limit
    public SimilaritySearchRequest(String content, int limit) {
        this.content = content;
        this.limit = limit;
    }

    // Getters and setters
    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public double getMinSimilarity() {
        return minSimilarity;
    }

    public void setMinSimilarity(double minSimilarity) {
        this.minSimilarity = minSimilarity;
    }

    public String getTeamOwner() {
        return teamOwner;
    }

    public void setTeamOwner(String teamOwner) {
        this.teamOwner = teamOwner;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getModelTarget() {
        return modelTarget;
    }

    public void setModelTarget(String modelTarget) {
        this.modelTarget = modelTarget;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public boolean isRootOnly() {
        return rootOnly;
    }

    public void setRootOnly(boolean rootOnly) {
        this.rootOnly = rootOnly;
    }

    @Override
    public String toString() {
        return "SimilaritySearchRequest{" +
                "content='" + (content != null ? content.substring(0, Math.min(content.length(), 50)) + "..." : null) + '\'' +
                ", limit=" + limit +
                ", minSimilarity=" + minSimilarity +
                ", teamOwner='" + teamOwner + '\'' +
                ", author='" + author + '\'' +
                ", modelTarget='" + modelTarget + '\'' +
                ", tag='" + tag + '\'' +
                ", rootOnly=" + rootOnly +
                '}';
    }
}
