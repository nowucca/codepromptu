package com.codepromptu.api.config;

import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.openai.OpenAiEmbeddingClient;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * Configuration for Spring AI integration, specifically for embedding generation.
 * Uses Spring AI's auto-configuration for OpenAI integration.
 */
@Configuration
public class SpringAIConfig {

    /**
     * Mock embedding client for testing when OpenAI is not available.
     * This will only be created if no other EmbeddingClient bean exists.
     */
    @Bean
    @ConditionalOnMissingBean(EmbeddingClient.class)
    public EmbeddingClient mockEmbeddingClient() {
        return new MockEmbeddingClient();
    }

    /**
     * Mock implementation of EmbeddingClient for testing purposes.
     * Generates deterministic embeddings based on content hash.
     */
    public static class MockEmbeddingClient implements EmbeddingClient {
        
        private static final int EMBEDDING_DIMENSIONS = 1536; // OpenAI ada-002 dimensions

        @Override
        public List<Double> embed(String text) {
            // Generate deterministic mock embedding based on text hash
            int hash = text.hashCode();
            List<Double> embedding = new java.util.ArrayList<>();
            
            // Create a deterministic but varied embedding
            for (int i = 0; i < EMBEDDING_DIMENSIONS; i++) {
                double value = Math.sin(hash + i) * 0.1; // Small values between -0.1 and 0.1
                embedding.add(value);
            }
            
            // Normalize the vector to unit length (common for embeddings)
            double magnitude = Math.sqrt(embedding.stream().mapToDouble(d -> d * d).sum());
            if (magnitude > 0) {
                embedding.replaceAll(d -> d / magnitude);
            }
            
            return embedding;
        }

        @Override
        public List<List<Double>> embed(List<String> texts) {
            return texts.stream().map(this::embed).toList();
        }

        @Override
        public List<Double> embed(org.springframework.ai.document.Document document) {
            // Extract text content from document and embed it
            return embed(document.getContent());
        }

        @Override
        public org.springframework.ai.embedding.EmbeddingResponse call(org.springframework.ai.embedding.EmbeddingRequest request) {
            // Extract texts from the request and generate embeddings
            List<String> texts = request.getInstructions();
            List<List<Double>> embeddings = embed(texts);
            
            // Create embedding results
            List<org.springframework.ai.embedding.Embedding> embeddingResults = new java.util.ArrayList<>();
            for (int i = 0; i < embeddings.size(); i++) {
                embeddingResults.add(new org.springframework.ai.embedding.Embedding(embeddings.get(i), i));
            }
            
            // Return response with results
            return new org.springframework.ai.embedding.EmbeddingResponse(embeddingResults);
        }
    }
}
