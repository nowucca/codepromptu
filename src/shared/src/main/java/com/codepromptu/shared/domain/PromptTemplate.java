package com.codepromptu.shared.domain;

import com.pgvector.PGvector;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Template entity representing a prompt shell derived from clustering similar prompts.
 * Contains the common structure with variable placeholders for prompt families.
 */
@Entity
@Table(name = "prompt_templates", indexes = {
    @Index(name = "idx_templates_embedding", columnList = "embedding"),
    @Index(name = "idx_templates_usage_count", columnList = "usage_count"),
    @Index(name = "idx_templates_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PromptTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    /**
     * The template shell with variable placeholders (e.g., __VAR1__, __VAR2__).
     * Represents the common structure across clustered prompts.
     */
    @Column(name = "shell", nullable = false, columnDefinition = "TEXT")
    @NotBlank(message = "Template shell cannot be blank")
    private String shell;

    /**
     * Array of text fragments that make up the template.
     * Used for efficient variable extraction during runtime matching.
     */
    @Column(name = "fragments")
    private String[] fragments;

    /**
     * Vector embedding of the template shell for similarity matching.
     * Uses pgvector extension with 1536 dimensions (OpenAI ada-002).
     */
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private PGvector embedding;

    /**
     * Number of variable placeholders in this template.
     */
    @Column(name = "variable_count")
    @Builder.Default
    private Integer variableCount = 0;

    /**
     * Number of times this template has been used/matched.
     * Updated when prompts are matched to this template.
     */
    @Column(name = "usage_count")
    @Builder.Default
    private Integer usageCount = 0;

    /**
     * Timestamp when the template was created.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp when the template was last updated.
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Usage records that reference this template.
     */
    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PromptUsage> usages = new ArrayList<>();

    /**
     * Increment the usage count when a prompt is matched to this template.
     */
    public void incrementUsageCount() {
        this.usageCount = (this.usageCount == null ? 0 : this.usageCount) + 1;
    }

    /**
     * Check if this template has any variable placeholders.
     */
    public boolean hasVariables() {
        return variableCount != null && variableCount > 0;
    }

    /**
     * Get the template complexity score based on fragment count and variables.
     */
    public double getComplexityScore() {
        int fragmentCount = fragments != null ? fragments.length : 0;
        int varCount = variableCount != null ? variableCount : 0;
        
        // Simple complexity metric: more fragments and variables = higher complexity
        return fragmentCount + (varCount * 2.0);
    }

    /**
     * Check if this template is frequently used (above threshold).
     */
    public boolean isPopular(int threshold) {
        return usageCount != null && usageCount >= threshold;
    }

    /**
     * Extract variables from a raw prompt using this template.
     * Returns null if the prompt doesn't match this template.
     */
    public List<String> extractVariables(String rawPrompt) {
        if (fragments == null || fragments.length == 0) {
            return null;
        }

        List<String> variables = new ArrayList<>();
        String remaining = rawPrompt;
        
        for (int i = 0; i < fragments.length; i++) {
            String fragment = fragments[i];
            
            if (fragment.isEmpty()) {
                continue;
            }
            
            int fragmentIndex = remaining.indexOf(fragment);
            if (fragmentIndex == -1) {
                // Fragment not found, template doesn't match
                return null;
            }
            
            // Extract variable before this fragment (if not the first fragment)
            if (i > 0) {
                String variable = remaining.substring(0, fragmentIndex);
                variables.add(variable);
            }
            
            // Move past this fragment
            remaining = remaining.substring(fragmentIndex + fragment.length());
        }
        
        // Add final variable if there's remaining text
        if (!remaining.isEmpty() && fragments.length > 0) {
            variables.add(remaining);
        }
        
        return variables.size() == variableCount ? variables : null;
    }

    /**
     * Calculate similarity score with another template based on shell content.
     */
    public double calculateSimilarity(PromptTemplate other) {
        if (other == null || other.getShell() == null || this.shell == null) {
            return 0.0;
        }
        
        // Simple Jaccard similarity based on words
        String[] thisWords = this.shell.toLowerCase().split("\\s+");
        String[] otherWords = other.getShell().toLowerCase().split("\\s+");
        
        java.util.Set<String> thisSet = java.util.Set.of(thisWords);
        java.util.Set<String> otherSet = java.util.Set.of(otherWords);
        
        java.util.Set<String> intersection = new java.util.HashSet<>(thisSet);
        intersection.retainAll(otherSet);
        
        java.util.Set<String> union = new java.util.HashSet<>(thisSet);
        union.addAll(otherSet);
        
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    /**
     * Generate a human-readable description of this template.
     */
    public String getDescription() {
        StringBuilder desc = new StringBuilder();
        desc.append("Template with ").append(variableCount).append(" variable(s)");
        
        if (usageCount != null && usageCount > 0) {
            desc.append(", used ").append(usageCount).append(" time(s)");
        }
        
        if (fragments != null && fragments.length > 0) {
            desc.append(", ").append(fragments.length).append(" fragment(s)");
        }
        
        return desc.toString();
    }

    /**
     * Create a new template from a shell and fragments.
     */
    public static PromptTemplate create(String shell, String[] fragments, PGvector embedding) {
        // Count variables in shell
        int varCount = 0;
        String temp = shell;
        while (temp.contains("__VAR")) {
            int start = temp.indexOf("__VAR");
            int end = temp.indexOf("__", start + 2);
            if (end != -1) {
                varCount++;
                temp = temp.substring(end + 2);
            } else {
                break;
            }
        }
        
        return PromptTemplate.builder()
                .shell(shell)
                .fragments(fragments)
                .embedding(embedding)
                .variableCount(varCount)
                .usageCount(0)
                .build();
    }
}
