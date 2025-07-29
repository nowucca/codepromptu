package com.codepromptu.api.service;

import com.pgvector.PGvector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for generating embeddings using Spring AI EmbeddingClient.
 * Handles both synchronous and asynchronous embedding generation.
 */
@Service
public class EmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingClient embeddingClient;

    @Autowired
    public EmbeddingService(EmbeddingClient embeddingClient) {
        this.embeddingClient = embeddingClient;
        logger.info("EmbeddingService initialized with client: {}", embeddingClient.getClass().getSimpleName());
    }

    /**
     * Generate embedding for a single text content synchronously.
     * 
     * @param content The text content to embed
     * @return List of doubles representing the embedding vector
     */
    public List<Double> generateEmbedding(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Content cannot be null or empty");
        }

        try {
            logger.debug("Generating embedding for content of length: {}", content.length());
            List<Double> embedding = embeddingClient.embed(content);
            logger.debug("Generated embedding with {} dimensions", embedding.size());
            return embedding;
        } catch (Exception e) {
            logger.error("Failed to generate embedding for content: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate embedding", e);
        }
    }

    /**
     * Generate embedding for a single text content asynchronously.
     * 
     * @param content The text content to embed
     * @return CompletableFuture containing the embedding vector
     */
    public CompletableFuture<List<Double>> generateEmbeddingAsync(String content) {
        return CompletableFuture.supplyAsync(() -> generateEmbedding(content));
    }

    /**
     * Generate embeddings for multiple text contents.
     * 
     * @param contents List of text contents to embed
     * @return List of embedding vectors
     */
    public List<List<Double>> generateEmbeddings(List<String> contents) {
        if (contents == null || contents.isEmpty()) {
            throw new IllegalArgumentException("Contents list cannot be null or empty");
        }

        try {
            logger.debug("Generating embeddings for {} contents", contents.size());
            List<List<Double>> embeddings = embeddingClient.embed(contents);
            logger.debug("Generated {} embeddings", embeddings.size());
            return embeddings;
        } catch (Exception e) {
            logger.error("Failed to generate embeddings for {} contents: {}", contents.size(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate embeddings", e);
        }
    }

    /**
     * Convert a List<Double> embedding to PGvector format for database storage.
     * 
     * @param embedding The embedding as List<Double>
     * @return PGvector object for database storage
     */
    public PGvector convertToPGVector(List<Double> embedding) {
        logger.info("=== CONVERT TO PGVECTOR START ===");
        logger.info("Input embedding: null={}, empty={}", embedding == null, embedding == null ? "N/A" : embedding.isEmpty());
        
        if (embedding == null || embedding.isEmpty()) {
            logger.error("convertToPGVector called with null or empty embedding - returning null");
            return null;
        }

        try {
            logger.info("Converting embedding to PGvector: {} dimensions", embedding.size());
            logger.debug("First 5 embedding values: [{}, {}, {}, {}, {}]", 
                        embedding.get(0), embedding.get(1), embedding.get(2), embedding.get(3), embedding.get(4));
            
            float[] floatArray = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                Double value = embedding.get(i);
                if (value == null) {
                    logger.error("CRITICAL: Null value found at index {} in embedding", i);
                    throw new RuntimeException("Null value found in embedding at index " + i);
                }
                floatArray[i] = value.floatValue();
            }
            
            logger.info("Created float array with {} elements", floatArray.length);
            logger.debug("Float array first 5 values: [{}, {}, {}, {}, {}]", 
                        floatArray[0], floatArray[1], floatArray[2], floatArray[3], floatArray[4]);
            
            PGvector pgVector = new PGvector(floatArray);
            logger.info("Successfully created PGvector: {} dimensions", pgVector.toArray().length);
            logger.info("=== CONVERT TO PGVECTOR SUCCESS ===");
            return pgVector;
        } catch (Exception e) {
            logger.error("CRITICAL: Failed to convert embedding to PGvector: {}", e.getMessage(), e);
            logger.error("Exception details:", e);
            throw new RuntimeException("Failed to convert embedding to PGvector", e);
        }
    }

    /**
     * Convert PGvector from database to List<Double> for processing.
     * 
     * @param pgVector The PGvector from database
     * @return List<Double> representation of the embedding
     */
    public List<Double> convertFromPGVector(PGvector pgVector) {
        if (pgVector == null) {
            return null;
        }

        try {
            float[] floatArray = pgVector.toArray();
            List<Double> result = new java.util.ArrayList<>();
            for (float f : floatArray) {
                result.add((double) f);
            }
            return result;
        } catch (Exception e) {
            logger.error("Failed to convert PGvector to List<Double>: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to convert PGvector to List<Double>", e);
        }
    }

    /**
     * Preprocess content before embedding generation.
     * This can include cleaning, normalization, or truncation.
     * 
     * @param content The raw content
     * @return Preprocessed content ready for embedding
     */
    public String preprocessContent(String content) {
        if (content == null) {
            return null;
        }

        // Basic preprocessing: trim whitespace and normalize line endings
        String processed = content.trim().replaceAll("\\r\\n|\\r|\\n", " ");
        
        // Truncate if too long (OpenAI has token limits)
        // This is a simple character-based truncation; in production, you might want token-based truncation
        final int MAX_CHARS = 8000; // Conservative limit
        if (processed.length() > MAX_CHARS) {
            processed = processed.substring(0, MAX_CHARS);
            logger.debug("Content truncated from {} to {} characters", content.length(), processed.length());
        }

        return processed;
    }

    /**
     * Calculate cosine similarity between two embeddings.
     * 
     * @param embedding1 First embedding vector
     * @param embedding2 Second embedding vector
     * @return Cosine similarity score between -1 and 1
     */
    public double calculateCosineSimilarity(List<Double> embedding1, List<Double> embedding2) {
        if (embedding1 == null || embedding2 == null || 
            embedding1.size() != embedding2.size()) {
            throw new IllegalArgumentException("Embeddings must be non-null and of equal size");
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < embedding1.size(); i++) {
            double val1 = embedding1.get(i);
            double val2 = embedding2.get(i);
            
            dotProduct += val1 * val2;
            norm1 += val1 * val1;
            norm2 += val2 * val2;
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}
