package com.codepromptu.api.service;

import com.codepromptu.api.repository.JdbcPromptRepository;
import com.codepromptu.api.repository.PromptVectorRepository;
import com.codepromptu.shared.domain.Prompt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for handling vector similarity operations and prompt classification.
 * Provides similarity search, classification, and threshold-based operations.
 */
@Service
public class SimilarityService {

    private static final Logger logger = LoggerFactory.getLogger(SimilarityService.class);

    private final JdbcPromptRepository promptRepository;
    private final PromptVectorRepository promptVectorRepository;
    private final EmbeddingService embeddingService;

    @Value("${codepromptu.similarity.threshold.same:0.95}")
    private double sameThreshold;

    @Value("${codepromptu.similarity.threshold.fork:0.70}")
    private double forkThreshold;

    @Autowired
    public SimilarityService(JdbcPromptRepository promptRepository, PromptVectorRepository promptVectorRepository, EmbeddingService embeddingService) {
        this.promptRepository = promptRepository;
        this.promptVectorRepository = promptVectorRepository;
        this.embeddingService = embeddingService;
        logger.info("SimilarityService initialized with thresholds - Same: {}, Fork: {}", sameThreshold, forkThreshold);
    }

    /**
     * Classification result for prompt similarity analysis.
     */
    public enum PromptClassification {
        SAME,    // Similarity >= sameThreshold (0.95)
        FORK,    // forkThreshold <= Similarity < sameThreshold (0.70-0.95)
        NEW      // Similarity < forkThreshold (< 0.70)
    }

    /**
     * Result object for similarity search operations.
     */
    public static class SimilarPromptResult {
        private final Prompt prompt;
        private final double similarityScore;
        private final PromptClassification classification;

        public SimilarPromptResult(Prompt prompt, double similarityScore, PromptClassification classification) {
            this.prompt = prompt;
            this.similarityScore = similarityScore;
            this.classification = classification;
        }

        public Prompt getPrompt() { return prompt; }
        public double getSimilarityScore() { return similarityScore; }
        public PromptClassification getClassification() { return classification; }
    }

    /**
     * Find similar prompts based on embedding vector similarity.
     * 
     * @param embedding The embedding vector to search for
     * @param limit Maximum number of results to return
     * @return List of similar prompts with similarity scores
     */
    public List<SimilarPromptResult> findSimilarPrompts(List<Double> embedding, int limit) {
        if (embedding == null || embedding.isEmpty()) {
            throw new IllegalArgumentException("Embedding cannot be null or empty");
        }

        try {
            logger.info("=== SIMILARITY SEARCH START ===");
            logger.info("Finding similar prompts for embedding with {} dimensions, limit: {}", embedding.size(), limit);
            logger.debug("Input embedding first 5 values: {}", embedding.subList(0, Math.min(5, embedding.size())));
            
            // Convert embedding to PGvector format for database query
            logger.debug("Converting embedding to PGvector. Embedding size: {}, first 3 values: [{}, {}, {}]", 
                        embedding.size(), 
                        embedding.get(0), embedding.get(1), embedding.get(2));
            
            var pgVector = embeddingService.convertToPGVector(embedding);
            
            if (pgVector == null) {
                logger.error("CRITICAL: convertToPGVector returned null! Embedding was: size={}, first 3 values: [{}, {}, {}]", 
                            embedding.size(), embedding.get(0), embedding.get(1), embedding.get(2));
                throw new RuntimeException("Failed to convert embedding to PGvector - got null result");
            }
            
            logger.debug("Converted to PGvector successfully: {} dimensions", pgVector.toArray().length);
            
            // Use PromptVectorRepository to find similar prompts (bypasses Hibernate parameter binding issues)
            List<PromptVectorRepository.SimilarPromptResult> vectorResults = promptVectorRepository.findSimilarPrompts(pgVector, limit);
            logger.info("Vector repository returned {} similar prompts", vectorResults.size());
            
            if (vectorResults.isEmpty()) {
                logger.warn("No similar prompts found!");
                return List.of();
            }
            
            // Convert to SimilarityService results and classify
            List<SimilarPromptResult> results = vectorResults.stream()
                    .map(vectorResult -> {
                        Prompt prompt = vectorResult.getPrompt();
                        double similarity = vectorResult.getSimilarityScore();
                        
                        logger.debug("Processing prompt ID: {}, content preview: {}, similarity: {}", 
                                   prompt.getId(), 
                                   prompt.getContent().substring(0, Math.min(50, prompt.getContent().length())),
                                   similarity);
                        
                        PromptClassification classification = classifyBySimilarity(similarity);
                        logger.debug("Classification for prompt {}: {}", prompt.getId(), classification);
                        
                        return new SimilarPromptResult(prompt, similarity, classification);
                    })
                    .toList();
            
            logger.info("Returning {} similarity results", results.size());
            results.forEach(result -> 
                logger.info("Result: ID={}, similarity={}, classification={}", 
                          result.getPrompt().getId(), 
                          result.getSimilarityScore(), 
                          result.getClassification()));
            logger.info("=== SIMILARITY SEARCH END ===");
            
            return results;
                    
        } catch (Exception e) {
            logger.error("Failed to find similar prompts: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to find similar prompts", e);
        }
    }

    /**
     * Find similar prompts based on text content.
     * 
     * @param content The text content to find similar prompts for
     * @param limit Maximum number of results to return
     * @return List of similar prompts with similarity scores
     */
    public List<SimilarPromptResult> findSimilarPrompts(String content, int limit) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Content cannot be null or empty");
        }

        // Generate embedding for the content
        String processedContent = embeddingService.preprocessContent(content);
        List<Double> embedding = embeddingService.generateEmbedding(processedContent);
        
        return findSimilarPrompts(embedding, limit);
    }

    /**
     * Classify a prompt based on its similarity to existing prompts.
     * 
     * @param content The prompt content to classify
     * @return Classification result with the most similar prompt and classification
     */
    public PromptClassificationResult classifyPrompt(String content) {
        if (content == null || content.trim().isEmpty()) {
            return new PromptClassificationResult(null, 0.0, PromptClassification.NEW);
        }

        try {
            // Find the most similar prompt
            List<SimilarPromptResult> similarPrompts = findSimilarPrompts(content, 1);
            
            if (similarPrompts.isEmpty()) {
                return new PromptClassificationResult(null, 0.0, PromptClassification.NEW);
            }

            SimilarPromptResult mostSimilar = similarPrompts.get(0);
            return new PromptClassificationResult(
                mostSimilar.getPrompt(),
                mostSimilar.getSimilarityScore(),
                mostSimilar.getClassification()
            );
            
        } catch (Exception e) {
            logger.error("Failed to classify prompt: {}", e.getMessage(), e);
            // Return NEW classification as fallback
            return new PromptClassificationResult(null, 0.0, PromptClassification.NEW);
        }
    }

    /**
     * Classify based on similarity score using configured thresholds.
     * 
     * @param similarity The similarity score (0.0 to 1.0)
     * @return Classification result
     */
    public PromptClassification classifyBySimilarity(double similarity) {
        if (similarity >= sameThreshold) {
            return PromptClassification.SAME;
        } else if (similarity >= forkThreshold) {
            return PromptClassification.FORK;
        } else {
            return PromptClassification.NEW;
        }
    }

    /**
     * Find prompts above a specific similarity threshold.
     * 
     * @param embedding The embedding vector to search for
     * @param threshold Minimum similarity threshold
     * @return List of prompts above the threshold
     */
    public List<SimilarPromptResult> findPromptsAboveThreshold(List<Double> embedding, double threshold) {
        if (embedding == null || embedding.isEmpty()) {
            throw new IllegalArgumentException("Embedding cannot be null or empty");
        }

        try {
            logger.debug("Finding prompts above threshold {} for embedding", threshold);
            
            var pgVector = embeddingService.convertToPGVector(embedding);
            
            // Use repository method to find prompts above threshold
            List<Prompt> prompts = promptRepository.findPromptsByThreshold(pgVector, threshold);
            
            return prompts.stream()
                    .map(prompt -> {
                        List<Double> promptEmbedding = embeddingService.convertFromPGVector(prompt.getEmbedding());
                        double similarity = embeddingService.calculateCosineSimilarity(embedding, promptEmbedding);
                        PromptClassification classification = classifyBySimilarity(similarity);
                        return new SimilarPromptResult(prompt, similarity, classification);
                    })
                    .sorted((a, b) -> Double.compare(b.getSimilarityScore(), a.getSimilarityScore()))
                    .toList();
                    
        } catch (Exception e) {
            logger.error("Failed to find prompts above threshold: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to find prompts above threshold", e);
        }
    }

    /**
     * Calculate similarity between two prompts.
     * 
     * @param prompt1 First prompt
     * @param prompt2 Second prompt
     * @return Similarity score between 0.0 and 1.0
     */
    public double calculateSimilarity(Prompt prompt1, Prompt prompt2) {
        if (prompt1 == null || prompt2 == null) {
            throw new IllegalArgumentException("Prompts cannot be null");
        }

        if (prompt1.getEmbedding() == null || prompt2.getEmbedding() == null) {
            throw new IllegalArgumentException("Prompts must have embeddings");
        }

        List<Double> embedding1 = embeddingService.convertFromPGVector(prompt1.getEmbedding());
        List<Double> embedding2 = embeddingService.convertFromPGVector(prompt2.getEmbedding());
        
        return embeddingService.calculateCosineSimilarity(embedding1, embedding2);
    }

    /**
     * Result object for prompt classification operations.
     */
    public static class PromptClassificationResult {
        private final Prompt mostSimilarPrompt;
        private final double similarityScore;
        private final PromptClassification classification;

        public PromptClassificationResult(Prompt mostSimilarPrompt, double similarityScore, PromptClassification classification) {
            this.mostSimilarPrompt = mostSimilarPrompt;
            this.similarityScore = similarityScore;
            this.classification = classification;
        }

        public Prompt getMostSimilarPrompt() { return mostSimilarPrompt; }
        public double getSimilarityScore() { return similarityScore; }
        public PromptClassification getClassification() { return classification; }
        
        public boolean isNew() { return classification == PromptClassification.NEW; }
        public boolean isFork() { return classification == PromptClassification.FORK; }
        public boolean isSame() { return classification == PromptClassification.SAME; }
    }

    // Getters for thresholds (useful for testing and monitoring)
    public double getSameThreshold() { return sameThreshold; }
    public double getForkThreshold() { return forkThreshold; }
}
