package com.codepromptu.api.repository;

import com.codepromptu.shared.domain.Prompt;
import com.pgvector.PGvector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for handling vector operations that bypass Hibernate's type mapping issues.
 * Uses native SQL to properly insert PGvector data into PostgreSQL.
 */
@Repository
public class PromptVectorRepository {

    private static final Logger logger = LoggerFactory.getLogger(PromptVectorRepository.class);

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public PromptVectorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        logger.info("PromptVectorRepository initialized with JdbcTemplate");
    }

    /**
     * Update the embedding for a prompt using native SQL to bypass Hibernate type mapping issues.
     * 
     * @param promptId The prompt ID
     * @param embedding The PGvector embedding
     */
    public void updateEmbedding(UUID promptId, PGvector embedding) {
        try {
            logger.info("=== UPDATING EMBEDDING FOR PROMPT {} ===", promptId);
            logger.debug("Embedding vector length: {}", embedding.toArray().length);
            logger.debug("Embedding vector preview: [{}, {}, {}, ...]", 
                        embedding.toArray()[0], 
                        embedding.toArray()[1], 
                        embedding.toArray()[2]);
            
            String sql = "UPDATE prompts SET embedding = CAST(? AS vector) WHERE id = ?";
            String embeddingString = embedding.toString();
            logger.debug("SQL: {}", sql);
            logger.debug("Embedding string length: {}", embeddingString.length());
            logger.debug("Embedding string preview: {}...", embeddingString.substring(0, Math.min(100, embeddingString.length())));
            
            int rowsUpdated = jdbcTemplate.update(sql, embeddingString, promptId);
            logger.info("Embedding update completed. Rows affected: {}", rowsUpdated);
            
            if (rowsUpdated == 0) {
                logger.warn("No rows were updated! Prompt {} may not exist or embedding update failed", promptId);
            } else {
                logger.info("Successfully updated embedding for prompt {}", promptId);
            }
            
        } catch (Exception e) {
            logger.error("Failed to update embedding for prompt {}: {}", promptId, e.getMessage(), e);
            throw new RuntimeException("Failed to update embedding", e);
        }
    }

    /**
     * Insert a prompt with embedding using native SQL.
     * This method handles the vector insertion properly.
     * 
     * @param promptId The prompt ID
     * @param embedding The PGvector embedding
     */
    public void insertEmbedding(UUID promptId, PGvector embedding) {
        // First check if the prompt exists and needs embedding update
        String checkSql = "SELECT COUNT(*) FROM prompts WHERE id = ? AND embedding IS NULL";
        Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, promptId);
        
        if (count != null && count > 0) {
            updateEmbedding(promptId, embedding);
        }
    }

    /**
     * Retrieve the embedding for a specific prompt using native SQL.
     * This bypasses Hibernate's type mapping issues during retrieval.
     * 
     * @param promptId The prompt ID
     * @return PGvector embedding or null if not found
     */
    public PGvector getEmbedding(UUID promptId) {
        try {
            logger.debug("Retrieving embedding for prompt: {}", promptId);
            
            // First, let's check what's actually in the database
            String checkSql = "SELECT id, embedding IS NOT NULL as has_embedding, LENGTH(embedding::text) as embedding_length FROM prompts WHERE id = ?";
            jdbcTemplate.query(checkSql, (rs, rowNum) -> {
                logger.info("Database check for prompt {}: has_embedding={}, embedding_length={}", 
                           rs.getString("id"), rs.getBoolean("has_embedding"), rs.getInt("embedding_length"));
                return null;
            }, promptId);
            
            String sql = "SELECT embedding FROM prompts WHERE id = ? AND embedding IS NOT NULL";
            
            List<PGvector> results = jdbcTemplate.query(sql, (rs, rowNum) -> {
                String embeddingString = rs.getString("embedding");
                logger.info("Raw embedding string for prompt {}: length={}, preview={}", 
                           promptId, embeddingString != null ? embeddingString.length() : 0,
                           embeddingString != null ? embeddingString.substring(0, Math.min(50, embeddingString.length())) : "null");
                
                if (embeddingString != null) {
                    try {
                        // Parse the vector string back to PGvector
                        PGvector pgVector = new PGvector(embeddingString);
                        logger.info("Successfully parsed PGvector: {} dimensions", pgVector.toArray().length);
                        return pgVector;
                    } catch (Exception e) {
                        logger.error("Failed to parse embedding string to PGvector: {}", e.getMessage(), e);
                        return null;
                    }
                }
                return null;
            }, promptId);
            
            if (results.isEmpty()) {
                logger.debug("No embedding found for prompt: {}", promptId);
                return null;
            }
            
            PGvector embedding = results.get(0);
            logger.debug("Retrieved embedding for prompt {}: {} dimensions", promptId, 
                        embedding != null ? embedding.toArray().length : 0);
            return embedding;
            
        } catch (Exception e) {
            logger.error("Failed to retrieve embedding for prompt {}: {}", promptId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Find similar prompts using cosine similarity with pgvector.
     * Uses JdbcTemplate to bypass Hibernate's parameter binding issues.
     * 
     * @param queryEmbedding The embedding to search for similar prompts
     * @param limit Maximum number of results to return
     * @return List of similar prompts with similarity scores
     */
    public List<SimilarPromptResult> findSimilarPrompts(PGvector queryEmbedding, int limit) {
        try {
            logger.info("=== SIMILARITY SEARCH START ===");
            
            if (queryEmbedding == null) {
                logger.error("Query embedding is null! Cannot perform similarity search.");
                return List.of();
            }
            
            logger.info("Finding similar prompts for embedding with {} dimensions, limit: {}", 
                       queryEmbedding.toArray().length, limit);
            logger.debug("Input embedding first 5 values: [{}, {}, {}, {}, {}]", 
                        queryEmbedding.toArray()[0], queryEmbedding.toArray()[1], 
                        queryEmbedding.toArray()[2], queryEmbedding.toArray()[3], 
                        queryEmbedding.toArray()[4]);

            String sql = """
                SELECT p.id, p.content, p.author, p.purpose, p.team_owner, p.model_target, 
                       p.version, p.is_active, p.created_at, p.updated_at,
                       (p.embedding <=> CAST(? AS vector)) as similarity_score
                FROM prompts p
                WHERE p.is_active = true
                AND p.embedding IS NOT NULL
                ORDER BY p.embedding <=> CAST(? AS vector)
                LIMIT ?
                """;

            String embeddingString = queryEmbedding.toString();
            logger.debug("Embedding string length: {}", embeddingString.length());
            logger.debug("SQL query: {}", sql);
            
            List<SimilarPromptResult> results = jdbcTemplate.query(sql, 
                new SimilarPromptResultRowMapper(), 
                embeddingString, embeddingString, limit);
            
            logger.info("Repository returned {} similar prompts", results.size());
            
            if (results.isEmpty()) {
                logger.warn("No similar prompts found! Checking database state...");
                // Debug: Check total prompts
                Integer totalPrompts = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM prompts", Integer.class);
                Integer promptsWithEmbeddings = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM prompts WHERE embedding IS NOT NULL", Integer.class);
                Integer activePrompts = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM prompts WHERE is_active = true", Integer.class);
                
                logger.info("Total prompts in database: {}", totalPrompts);
                logger.info("Prompts with embeddings: {}", promptsWithEmbeddings);
                logger.info("Active prompts: {}", activePrompts);
            }
            
            return results;
            
        } catch (Exception e) {
            logger.error("Failed to find similar prompts: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to find similar prompts", e);
        }
    }

    /**
     * Result class for similarity search results.
     */
    public static class SimilarPromptResult {
        private final Prompt prompt;
        private final double similarityScore;

        public SimilarPromptResult(Prompt prompt, double similarityScore) {
            this.prompt = prompt;
            this.similarityScore = similarityScore;
        }

        public Prompt getPrompt() {
            return prompt;
        }

        public double getSimilarityScore() {
            return similarityScore;
        }
    }

    /**
     * RowMapper for similarity search results.
     */
    private static class SimilarPromptResultRowMapper implements RowMapper<SimilarPromptResult> {
        @Override
        public SimilarPromptResult mapRow(ResultSet rs, int rowNum) throws SQLException {
            Prompt prompt = Prompt.builder()
                    .id(UUID.fromString(rs.getString("id")))
                    .content(rs.getString("content"))
                    .author(rs.getString("author"))
                    .purpose(rs.getString("purpose"))
                    .teamOwner(rs.getString("team_owner"))
                    .modelTarget(rs.getString("model_target"))
                    .version(rs.getInt("version"))
                    .isActive(rs.getBoolean("is_active"))
                    .createdAt(rs.getTimestamp("created_at").toInstant())
                    .updatedAt(rs.getTimestamp("updated_at").toInstant())
                    .build();

            double similarityScore = rs.getDouble("similarity_score");
            
            return new SimilarPromptResult(prompt, similarityScore);
        }
    }
}
