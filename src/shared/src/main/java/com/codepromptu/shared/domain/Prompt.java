package com.codepromptu.shared.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.databind.JsonNode;
import com.pgvector.PGvector;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Core domain entity representing a prompt with its metadata, versioning, and vector embedding.
 * Supports hierarchical relationships through parent-child links for prompt evolution tracking.
 * 
 * This is now a simple POJO without JPA/Hibernate annotations for use with JDBC Template.
 */
@JsonIgnoreType
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Prompt {

    @EqualsAndHashCode.Include
    private UUID id;

    /**
     * Parent prompt for tracking evolution and forking relationships.
     * Null for root prompts.
     */
    @JsonIgnore
    private Prompt parent;

    /**
     * Child prompts that were forked or evolved from this prompt.
     */
    @JsonIgnore
    @Builder.Default
    private List<Prompt> children = new ArrayList<>();

    /**
     * The actual prompt content/text.
     */
    private String content;

    /**
     * Vector embedding of the prompt content for similarity search.
     * Uses pgvector extension with 1536 dimensions (OpenAI ada-002).
     */
    private PGvector embedding;

    /**
     * Flexible metadata storage for prompt-specific information.
     * Can include tags, categories, success criteria, etc.
     */
    @Builder.Default
    private JsonNode metadata = null;

    /**
     * Author/creator of the prompt.
     */
    private String author;

    /**
     * Purpose or use case description for the prompt.
     */
    private String purpose;

    /**
     * Success criteria for evaluating prompt effectiveness.
     */
    private String successCriteria;

    /**
     * Tags for categorization and discovery.
     */
    private String[] tags;

    /**
     * Team or organization that owns this prompt.
     */
    private String teamOwner;

    /**
     * Target LLM model for this prompt (e.g., "gpt-4", "claude-3").
     */
    private String modelTarget;

    /**
     * Version number for tracking prompt evolution.
     */
    @Builder.Default
    private Integer version = 1;

    /**
     * Whether this prompt is currently active/available.
     */
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Timestamp when the prompt was created.
     */
    private Instant createdAt;

    /**
     * Timestamp when the prompt was last updated.
     */
    private Instant updatedAt;

    /**
     * Cross-references to related prompts (not in the same lineage).
     */
    @JsonIgnore
    @Builder.Default
    private List<PromptCrossref> outgoingCrossrefs = new ArrayList<>();

    /**
     * Cross-references from other prompts to this one.
     */
    @JsonIgnore
    @Builder.Default
    private List<PromptCrossref> incomingCrossrefs = new ArrayList<>();

    /**
     * Usage records for this prompt.
     */
    @JsonIgnore
    @Builder.Default
    private List<PromptUsage> usages = new ArrayList<>();

    /**
     * Evaluation records for this prompt.
     */
    @JsonIgnore
    @Builder.Default
    private List<PromptEvaluation> evaluations = new ArrayList<>();

    /**
     * Check if this prompt is a root prompt (has no parent).
     */
    public boolean checkIsRoot() {
        return parent == null;
    }

    /**
     * Check if this prompt has children (is a parent to other prompts).
     */
    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }

    /**
     * Find the root prompt in the lineage hierarchy.
     */
    public Prompt findRoot() {
        Prompt current = this;
        while (current.getParent() != null) {
            current = current.getParent();
        }
        return current;
    }

    /**
     * Get the depth of this prompt in the lineage hierarchy (0 for root).
     */
    public int getDepth() {
        int depth = 0;
        Prompt current = this;
        while (current.getParent() != null) {
            depth++;
            current = current.getParent();
        }
        return depth;
    }

    /**
     * Create a child prompt (fork) from this prompt.
     */
    public Prompt createChild(String newContent, String author) {
        return Prompt.builder()
                .parent(this)
                .content(newContent)
                .author(author)
                .purpose(this.purpose)
                .teamOwner(this.teamOwner)
                .modelTarget(this.modelTarget)
                .version(this.version + 1)
                .isActive(true)
                .build();
    }

    /**
     * Add a cross-reference to another prompt.
     */
    public void addCrossref(Prompt targetPrompt, String relationshipType, Float similarityScore, String notes) {
        PromptCrossref crossref = PromptCrossref.builder()
                .sourcePrompt(this)
                .targetPrompt(targetPrompt)
                .relationshipType(relationshipType)
                .similarityScore(similarityScore)
                .notes(notes)
                .build();
        
        this.outgoingCrossrefs.add(crossref);
        targetPrompt.getIncomingCrossrefs().add(crossref);
    }
}
