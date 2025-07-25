package com.codepromptu.shared.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing cross-references between related prompts that are not in the same lineage.
 * Used to surface common patterns across teams and domains.
 */
@Entity
@Table(name = "prompt_crossrefs", indexes = {
    @Index(name = "idx_crossrefs_source_prompt", columnList = "source_prompt_id"),
    @Index(name = "idx_crossrefs_target_prompt", columnList = "target_prompt_id"),
    @Index(name = "idx_crossrefs_similarity", columnList = "similarity_score"),
    @Index(name = "idx_crossrefs_relationship", columnList = "relationship_type")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PromptCrossref {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    /**
     * Source prompt in the cross-reference relationship.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_prompt_id", nullable = false)
    @NotNull(message = "Source prompt cannot be null")
    private Prompt sourcePrompt;

    /**
     * Target prompt in the cross-reference relationship.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_prompt_id", nullable = false)
    @NotNull(message = "Target prompt cannot be null")
    private Prompt targetPrompt;

    /**
     * Type of relationship between the prompts.
     * Examples: "similar", "variant", "improvement", "related", "opposite"
     */
    @Column(name = "relationship_type", length = 100)
    private String relationshipType;

    /**
     * Similarity score between the prompts (0.0 to 1.0).
     * Calculated using vector similarity or other metrics.
     */
    @Column(name = "similarity_score")
    private Float similarityScore;

    /**
     * Optional notes explaining the relationship.
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * Timestamp when the cross-reference was created.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Check if this is a bidirectional relationship.
     * Some relationships like "similar" are naturally bidirectional.
     */
    public boolean isBidirectional() {
        return relationshipType != null && (
            relationshipType.equalsIgnoreCase("similar") ||
            relationshipType.equalsIgnoreCase("related") ||
            relationshipType.equalsIgnoreCase("equivalent")
        );
    }

    /**
     * Check if this is a high-similarity relationship.
     */
    public boolean isHighSimilarity(float threshold) {
        return similarityScore != null && similarityScore >= threshold;
    }

    /**
     * Get the relationship strength based on similarity score and type.
     */
    public String getRelationshipStrength() {
        if (similarityScore == null) {
            return "unknown";
        }
        
        if (similarityScore >= 0.9f) {
            return "very strong";
        } else if (similarityScore >= 0.7f) {
            return "strong";
        } else if (similarityScore >= 0.5f) {
            return "moderate";
        } else if (similarityScore >= 0.3f) {
            return "weak";
        } else {
            return "very weak";
        }
    }

    /**
     * Get a human-readable description of this cross-reference.
     */
    public String getDescription() {
        StringBuilder desc = new StringBuilder();
        
        if (relationshipType != null) {
            desc.append(relationshipType);
        } else {
            desc.append("related");
        }
        
        if (similarityScore != null) {
            desc.append(" (").append(String.format("%.2f", similarityScore)).append(" similarity)");
        }
        
        if (notes != null && !notes.trim().isEmpty()) {
            desc.append(" - ").append(notes);
        }
        
        return desc.toString();
    }

    /**
     * Create a similarity-based cross-reference.
     */
    public static PromptCrossref createSimilarity(Prompt source, Prompt target, float similarityScore) {
        return PromptCrossref.builder()
                .sourcePrompt(source)
                .targetPrompt(target)
                .relationshipType("similar")
                .similarityScore(similarityScore)
                .build();
    }

    /**
     * Create a variant cross-reference (for prompts that are variations of each other).
     */
    public static PromptCrossref createVariant(Prompt source, Prompt target, float similarityScore, String notes) {
        return PromptCrossref.builder()
                .sourcePrompt(source)
                .targetPrompt(target)
                .relationshipType("variant")
                .similarityScore(similarityScore)
                .notes(notes)
                .build();
    }

    /**
     * Create an improvement cross-reference (when one prompt improves upon another).
     */
    public static PromptCrossref createImprovement(Prompt source, Prompt improved, String notes) {
        return PromptCrossref.builder()
                .sourcePrompt(source)
                .targetPrompt(improved)
                .relationshipType("improvement")
                .notes(notes)
                .build();
    }

    /**
     * Create a general relationship cross-reference.
     */
    public static PromptCrossref createRelated(Prompt source, Prompt target, String relationshipType, String notes) {
        return PromptCrossref.builder()
                .sourcePrompt(source)
                .targetPrompt(target)
                .relationshipType(relationshipType)
                .notes(notes)
                .build();
    }

    /**
     * Validate that source and target are different prompts.
     */
    @PrePersist
    @PreUpdate
    private void validateCrossref() {
        if (sourcePrompt != null && targetPrompt != null && 
            sourcePrompt.getId() != null && targetPrompt.getId() != null &&
            sourcePrompt.getId().equals(targetPrompt.getId())) {
            throw new IllegalArgumentException("Source and target prompts cannot be the same");
        }
    }
}
