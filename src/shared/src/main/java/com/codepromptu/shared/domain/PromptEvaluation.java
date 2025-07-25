package com.codepromptu.shared.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing evaluation metrics and feedback for prompts and their usage.
 * Supports both quantitative metrics and qualitative feedback.
 */
@Entity
@Table(name = "prompt_evaluations", indexes = {
    @Index(name = "idx_evaluations_prompt_id", columnList = "prompt_id"),
    @Index(name = "idx_evaluations_usage_id", columnList = "usage_id"),
    @Index(name = "idx_evaluations_score", columnList = "score"),
    @Index(name = "idx_evaluations_type", columnList = "evaluation_type"),
    @Index(name = "idx_evaluations_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PromptEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    /**
     * Reference to the prompt being evaluated.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prompt_id", nullable = false)
    @NotNull(message = "Prompt cannot be null")
    private Prompt prompt;

    /**
     * Reference to the specific usage instance (optional).
     * If null, this evaluation applies to the prompt in general.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usage_id")
    private PromptUsage usage;

    /**
     * Type of evaluation: "quantitative", "qualitative", "automated", "manual"
     */
    @Column(name = "evaluation_type", length = 100)
    private String evaluationType;

    /**
     * Numeric score for the evaluation.
     */
    @Column(name = "score")
    private Float score;

    /**
     * Maximum possible score (for normalization).
     */
    @Column(name = "max_score")
    @Builder.Default
    private Float maxScore = 1.0f;

    /**
     * Textual feedback or comments about the prompt/usage.
     */
    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    /**
     * Person or system that provided the evaluation.
     */
    @Column(name = "evaluator")
    private String evaluator;

    /**
     * Structured criteria used for evaluation.
     * Can include metrics like accuracy, relevance, clarity, etc.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "criteria", columnDefinition = "jsonb")
    @Builder.Default
    private JsonNode criteria = null;

    /**
     * Timestamp when the evaluation was created.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Calculate the normalized score (0.0 to 1.0).
     */
    public Float getNormalizedScore() {
        if (score == null || maxScore == null || maxScore == 0) {
            return null;
        }
        return Math.min(1.0f, Math.max(0.0f, score / maxScore));
    }

    /**
     * Check if this is a positive evaluation (above threshold).
     */
    public boolean isPositive(float threshold) {
        Float normalized = getNormalizedScore();
        return normalized != null && normalized >= threshold;
    }

    /**
     * Check if this is a negative evaluation (below threshold).
     */
    public boolean isNegative(float threshold) {
        Float normalized = getNormalizedScore();
        return normalized != null && normalized < threshold;
    }

    /**
     * Get the evaluation grade based on score.
     */
    public String getGrade() {
        Float normalized = getNormalizedScore();
        if (normalized == null) {
            return "N/A";
        }
        
        if (normalized >= 0.9f) {
            return "A";
        } else if (normalized >= 0.8f) {
            return "B";
        } else if (normalized >= 0.7f) {
            return "C";
        } else if (normalized >= 0.6f) {
            return "D";
        } else {
            return "F";
        }
    }

    /**
     * Check if this evaluation has detailed criteria.
     */
    public boolean hasCriteria() {
        return criteria != null && !criteria.isNull() && criteria.size() > 0;
    }

    /**
     * Check if this evaluation has textual feedback.
     */
    public boolean hasFeedback() {
        return feedback != null && !feedback.trim().isEmpty();
    }

    /**
     * Get a summary description of this evaluation.
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        
        if (evaluationType != null) {
            summary.append(evaluationType.toUpperCase());
        } else {
            summary.append("EVALUATION");
        }
        
        if (score != null) {
            summary.append(" - ").append(getGrade());
            summary.append(" (").append(String.format("%.2f", score));
            if (maxScore != null && maxScore != 1.0f) {
                summary.append("/").append(String.format("%.1f", maxScore));
            }
            summary.append(")");
        }
        
        if (evaluator != null) {
            summary.append(" by ").append(evaluator);
        }
        
        return summary.toString();
    }

    /**
     * Create a quantitative evaluation.
     */
    public static PromptEvaluation createQuantitative(Prompt prompt, PromptUsage usage, 
                                                    float score, float maxScore, String evaluator) {
        return PromptEvaluation.builder()
                .prompt(prompt)
                .usage(usage)
                .evaluationType("quantitative")
                .score(score)
                .maxScore(maxScore)
                .evaluator(evaluator)
                .build();
    }

    /**
     * Create a qualitative evaluation.
     */
    public static PromptEvaluation createQualitative(Prompt prompt, PromptUsage usage, 
                                                   String feedback, String evaluator) {
        return PromptEvaluation.builder()
                .prompt(prompt)
                .usage(usage)
                .evaluationType("qualitative")
                .feedback(feedback)
                .evaluator(evaluator)
                .build();
    }

    /**
     * Create an automated evaluation.
     */
    public static PromptEvaluation createAutomated(Prompt prompt, PromptUsage usage, 
                                                 float score, JsonNode criteria, String system) {
        return PromptEvaluation.builder()
                .prompt(prompt)
                .usage(usage)
                .evaluationType("automated")
                .score(score)
                .maxScore(1.0f)
                .criteria(criteria)
                .evaluator(system)
                .build();
    }

    /**
     * Create a manual evaluation with both score and feedback.
     */
    public static PromptEvaluation createManual(Prompt prompt, PromptUsage usage, 
                                              float score, float maxScore, String feedback, 
                                              String evaluator) {
        return PromptEvaluation.builder()
                .prompt(prompt)
                .usage(usage)
                .evaluationType("manual")
                .score(score)
                .maxScore(maxScore)
                .feedback(feedback)
                .evaluator(evaluator)
                .build();
    }

    /**
     * Create a thumbs up/down evaluation.
     */
    public static PromptEvaluation createThumbsUpDown(Prompt prompt, PromptUsage usage, 
                                                    boolean thumbsUp, String evaluator) {
        return PromptEvaluation.builder()
                .prompt(prompt)
                .usage(usage)
                .evaluationType("binary")
                .score(thumbsUp ? 1.0f : 0.0f)
                .maxScore(1.0f)
                .feedback(thumbsUp ? "Thumbs up" : "Thumbs down")
                .evaluator(evaluator)
                .build();
    }

    /**
     * Create a star rating evaluation (1-5 stars).
     */
    public static PromptEvaluation createStarRating(Prompt prompt, PromptUsage usage, 
                                                  int stars, String evaluator) {
        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("Star rating must be between 1 and 5");
        }
        
        return PromptEvaluation.builder()
                .prompt(prompt)
                .usage(usage)
                .evaluationType("star_rating")
                .score((float) stars)
                .maxScore(5.0f)
                .feedback(stars + " star" + (stars == 1 ? "" : "s"))
                .evaluator(evaluator)
                .build();
    }
}
