package com.codepromptu.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for prompt API responses.
 * This avoids Jackson serialization issues with the entity's circular references.
 */
public class PromptResponseDto {
    
    private UUID id;
    private String content;
    private JsonNode metadata;
    private String author;
    private String purpose;
    private String successCriteria;
    private String[] tags;
    private String teamOwner;
    private String modelTarget;
    private Integer version;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
    
    // Parent/child relationships represented as IDs only to avoid circular references
    private UUID parentId;
    private boolean isRoot;
    private boolean hasChildren;
    private int depth;

    public PromptResponseDto() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public JsonNode getMetadata() { return metadata; }
    public void setMetadata(JsonNode metadata) { this.metadata = metadata; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public String getSuccessCriteria() { return successCriteria; }
    public void setSuccessCriteria(String successCriteria) { this.successCriteria = successCriteria; }

    public String[] getTags() { return tags; }
    public void setTags(String[] tags) { this.tags = tags; }

    public String getTeamOwner() { return teamOwner; }
    public void setTeamOwner(String teamOwner) { this.teamOwner = teamOwner; }

    public String getModelTarget() { return modelTarget; }
    public void setModelTarget(String modelTarget) { this.modelTarget = modelTarget; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }

    public boolean isRoot() { return isRoot; }
    public void setRoot(boolean isRoot) { this.isRoot = isRoot; }

    public boolean isHasChildren() { return hasChildren; }
    public void setHasChildren(boolean hasChildren) { this.hasChildren = hasChildren; }

    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }
}
