package com.codepromptu.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Service for managing embedding indexes automatically based on data volume.
 * This service ensures optimal performance for similarity search operations.
 */
@Service
public class EmbeddingIndexService {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingIndexService.class);
    private static final int MIN_PROMPTS_FOR_INDEX = 100;

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public EmbeddingIndexService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        logger.info("EmbeddingIndexService initialized");
    }

    /**
     * Check and create embedding index when the application starts.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("Application ready - checking embedding index status");
        checkAndCreateEmbeddingIndex();
    }

    /**
     * Periodically check if we need to create or rebuild the embedding index.
     * Runs every hour to ensure optimal performance as data grows.
     */
    @Scheduled(fixedRate = 3600000) // 1 hour = 3600000 ms
    public void scheduledIndexCheck() {
        logger.debug("Scheduled embedding index check");
        checkAndCreateEmbeddingIndex();
    }

    /**
     * Check the current state and create/rebuild the embedding index if needed.
     */
    public void checkAndCreateEmbeddingIndex() {
        try {
            // Get current count of prompts with embeddings
            Integer promptCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM prompts WHERE embedding IS NOT NULL", 
                Integer.class
            );

            if (promptCount == null) {
                promptCount = 0;
            }

            logger.info("Found {} prompts with embeddings", promptCount);

            // Check if index exists
            Integer indexCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'idx_prompts_embedding_ivfflat'",
                Integer.class
            );

            boolean indexExists = indexCount != null && indexCount > 0;

            if (promptCount >= MIN_PROMPTS_FOR_INDEX) {
                if (!indexExists) {
                    logger.info("Creating embedding index - sufficient data available ({} prompts)", promptCount);
                    createEmbeddingIndex();
                } else {
                    // Check if we need to rebuild the index (significant data growth)
                    Integer currentLists = getCurrentIndexLists();
                    Integer optimalLists = Math.max(promptCount / 1000, 10);
                    
                    if (currentLists != null && Math.abs(currentLists - optimalLists) > 5) {
                        logger.info("Rebuilding embedding index - data growth detected (current lists: {}, optimal: {})", 
                                   currentLists, optimalLists);
                        rebuildEmbeddingIndex();
                    } else {
                        logger.debug("Embedding index is optimal (lists: {})", currentLists);
                    }
                }
            } else {
                if (indexExists) {
                    logger.info("Removing embedding index - insufficient data ({} prompts, need {})", 
                               promptCount, MIN_PROMPTS_FOR_INDEX);
                    dropEmbeddingIndex();
                } else {
                    logger.debug("No embedding index needed - insufficient data ({} prompts)", promptCount);
                }
            }

        } catch (Exception e) {
            logger.error("Error checking embedding index: {}", e.getMessage(), e);
        }
    }

    /**
     * Create the embedding index using the database function.
     */
    public void createEmbeddingIndex() {
        try {
            jdbcTemplate.execute("SELECT create_embedding_index_if_needed()");
            logger.info("Successfully created embedding index");
        } catch (Exception e) {
            logger.error("Failed to create embedding index: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create embedding index", e);
        }
    }

    /**
     * Rebuild the embedding index.
     */
    public void rebuildEmbeddingIndex() {
        try {
            jdbcTemplate.execute("SELECT rebuild_embedding_index()");
            logger.info("Successfully rebuilt embedding index");
        } catch (Exception e) {
            logger.error("Failed to rebuild embedding index: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to rebuild embedding index", e);
        }
    }

    /**
     * Drop the embedding index.
     */
    public void dropEmbeddingIndex() {
        try {
            jdbcTemplate.execute("DROP INDEX IF EXISTS idx_prompts_embedding_ivfflat");
            logger.info("Successfully dropped embedding index");
        } catch (Exception e) {
            logger.error("Failed to drop embedding index: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to drop embedding index", e);
        }
    }

    /**
     * Get the current number of lists in the existing index.
     */
    private Integer getCurrentIndexLists() {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT (regexp_match(pg_get_indexdef(indexrelid), 'lists = (\\d+)'))[1]::integer " +
                "FROM pg_index JOIN pg_class ON pg_index.indexrelid = pg_class.oid " +
                "WHERE pg_class.relname = 'idx_prompts_embedding_ivfflat'",
                Integer.class
            );
        } catch (Exception e) {
            logger.debug("Could not get current index lists: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get statistics about the embedding index.
     */
    public EmbeddingIndexStats getIndexStats() {
        try {
            Integer promptCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM prompts WHERE embedding IS NOT NULL", 
                Integer.class
            );

            Integer indexCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'idx_prompts_embedding_ivfflat'",
                Integer.class
            );

            boolean indexExists = indexCount != null && indexCount > 0;
            Integer currentLists = indexExists ? getCurrentIndexLists() : null;

            return new EmbeddingIndexStats(
                promptCount != null ? promptCount : 0,
                indexExists,
                currentLists,
                MIN_PROMPTS_FOR_INDEX
            );

        } catch (Exception e) {
            logger.error("Error getting index stats: {}", e.getMessage(), e);
            return new EmbeddingIndexStats(0, false, null, MIN_PROMPTS_FOR_INDEX);
        }
    }

    /**
     * Statistics about the embedding index.
     */
    public static class EmbeddingIndexStats {
        private final int promptsWithEmbeddings;
        private final boolean indexExists;
        private final Integer indexLists;
        private final int minPromptsForIndex;

        public EmbeddingIndexStats(int promptsWithEmbeddings, boolean indexExists, 
                                 Integer indexLists, int minPromptsForIndex) {
            this.promptsWithEmbeddings = promptsWithEmbeddings;
            this.indexExists = indexExists;
            this.indexLists = indexLists;
            this.minPromptsForIndex = minPromptsForIndex;
        }

        public int getPromptsWithEmbeddings() { return promptsWithEmbeddings; }
        public boolean isIndexExists() { return indexExists; }
        public Integer getIndexLists() { return indexLists; }
        public int getMinPromptsForIndex() { return minPromptsForIndex; }
        public boolean shouldHaveIndex() { return promptsWithEmbeddings >= minPromptsForIndex; }
        public boolean isOptimal() { return shouldHaveIndex() == indexExists; }

        @Override
        public String toString() {
            return String.format("EmbeddingIndexStats{prompts=%d, indexExists=%s, lists=%s, optimal=%s}", 
                               promptsWithEmbeddings, indexExists, indexLists, isOptimal());
        }
    }
}
