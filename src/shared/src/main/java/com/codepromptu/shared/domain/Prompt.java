package com.codepromptu.shared.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.pgvector.PGvector;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Core domain entity representing a prompt with its metadata, versioning, and vector embedding.
 * Supports hierarchical relationships through parent-child links for prompt evolution tracking.
 */
@Entity
@Table(name = "prompts", indexes = {
    @Index(name = "idx_prompts_parent_id", columnList = "parent_id"),
    @Index(name = "idx_prompts_team_owner", columnList = "team_owner"),
    @Index(name = "idx_prompts_created_at", columnList = "created_at"),
    @Index(name = "idx_prompts_embedding", columnList = "embedding")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Prompt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    /**
     * Parent prompt for tracking evolution and forking relationships.
     * Null for root prompts.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @JsonIgnore
    private Prompt parent;

    /**
     * Child prompts that were forked or evolved from this prompt.
     */
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    @Builder.Default
    private List<Prompt> children = new ArrayList<>();

    /**
     * The actual prompt content/text.
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    @NotBlank(message = "Prompt content cannot be blank")
    private String content;

    /**
     * Vector embedding of the prompt content for similarity search.
     * Uses pgvector extension with 1536 dimensions (OpenAI ada-002).
     */
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private PGvector embedding;

    /**
     * Flexible metadata storage for prompt-specific information.
     * Can include tags, categories, success criteria, etc.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    @Builder.Default
    private JsonNode metadata = null;

    /**
     * Author/creator of the prompt.
     */
    @Column(name = "author")
    private String author;

    /**
     * Purpose or use case description for the prompt.
     */
    @Column(name = "purpose", columnDefinition = "TEXT")
    private String purpose;

    /**
     * Success criteria for evaluating prompt effectiveness.
     */
    @Column(name = "success_criteria", columnDefinition = "TEXT")
    private String successCriteria;

    /**
     * Tags for categorization and discovery.
     */
    @Column(name = "tags")
    private String[] tags;

    /**
     * Team or organization that owns this prompt.
     */
    @Column(name = "team_owner")
    private String teamOwner;

    /**
     * Target LLM model for this prompt (e.g., "gpt-4", "claude-3").
     */
    @Column(name = "model_target")
    private String modelTarget;

    /**
     * Version number for tracking prompt evolution.
     */
    @Column(name = "version")
    @Builder.Default
    private Integer version = 1;

    /**
     * Whether this prompt is currently active/available.
     */
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Timestamp when the prompt was created.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp when the prompt was last updated.
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Cross-references to related prompts (not in the same lineage).
     */
    @OneToMany(mappedBy = "sourcePrompt", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    @Builder.Default
    private List<PromptCrossref> outgoingCrossrefs = new ArrayList<>();

    /**
     * Cross-references from other prompts to this one.
     */
    @OneToMany(mappedBy = "targetPrompt", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    @Builder.Default
    private List<PromptCrossref> incomingCrossrefs = new ArrayList<>();

    /**
     * Usage records for this prompt.
     */
    @OneToMany(mappedBy = "prompt", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    @Builder.Default
    private List<PromptUsage> usages = new ArrayList<>();

    /**
     * Evaluation records for this prompt.
     */
    @OneToMany(mappedBy = "prompt", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    @Builder.Default
    private List<PromptEvaluation> evaluations = new ArrayList<>();

    /**
     * Check if this prompt is a root prompt (has no parent).
     */
    public boolean isRoot() {
        return parent == null;
    }

    /**
     * Check if this prompt has children (is a parent to other prompts).
     */
    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }

    /**
     * Get the root prompt in the lineage hierarchy.
     */
    public Prompt getRoot() {
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
