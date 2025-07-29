package com.codepromptu.api.service;

import com.codepromptu.api.repository.JdbcPromptRepository;
import com.codepromptu.api.repository.PromptVectorRepository;
import com.codepromptu.shared.domain.Prompt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing prompts with automatic embedding generation and similarity analysis.
 * Provides CRUD operations, forking, and similarity-based operations.
 */
@Service
public class PromptService {

    private static final Logger logger = LoggerFactory.getLogger(PromptService.class);

    private final JdbcPromptRepository promptRepository;
    private final EmbeddingService embeddingService;
    private final SimilarityService similarityService;
    private final PromptVectorRepository promptVectorRepository;

    @Autowired
    public PromptService(JdbcPromptRepository promptRepository, 
                        EmbeddingService embeddingService,
                        SimilarityService similarityService,
                        PromptVectorRepository promptVectorRepository) {
        this.promptRepository = promptRepository;
        this.embeddingService = embeddingService;
        this.similarityService = similarityService;
        this.promptVectorRepository = promptVectorRepository;
        logger.info("PromptService initialized with JDBC repository, embedding, similarity, and vector services");
    }

    /**
     * Get all active prompts.
     * 
     * @return List of active prompts
     */
    public List<Prompt> getAllPrompts() {
        return promptRepository.findAllActive();
    }

    /**
     * Get a prompt by ID.
     * 
     * @param id The prompt ID
     * @return Optional containing the prompt if found
     */
    public Optional<Prompt> getPromptById(UUID id) {
        return promptRepository.findById(id);
    }

    /**
     * Create a new prompt with automatic embedding generation.
     * 
     * @param prompt The prompt to create
     * @return The created prompt with embedding
     */
    public Prompt createPrompt(Prompt prompt) {
        if (prompt == null) {
            throw new IllegalArgumentException("Prompt cannot be null");
        }

        if (prompt.getContent() == null || prompt.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt content cannot be null or empty");
        }

        try {
            logger.debug("Creating prompt with content length: {}", prompt.getContent().length());

            // Set default values if not provided
            if (prompt.getIsActive() == null) {
                prompt.setIsActive(true);
            }
            if (prompt.getVersion() == null) {
                prompt.setVersion(1);
            }

            // Save the prompt first without embedding
            Prompt savedPrompt = promptRepository.save(prompt);
            
            // Generate embedding for the prompt content
            String processedContent = embeddingService.preprocessContent(prompt.getContent());
            List<Double> embedding = embeddingService.generateEmbedding(processedContent);
            
            // Convert embedding to PGvector and store using JdbcTemplate
            com.pgvector.PGvector pgVector = embeddingService.convertToPGVector(embedding);
            promptVectorRepository.updateEmbedding(savedPrompt.getId(), pgVector);
            
            // Retrieve the complete prompt with embedding from database
            Prompt finalPrompt = promptRepository.findById(savedPrompt.getId()).orElse(savedPrompt);
            
            logger.info("Created prompt with ID: {} and embedding with {} dimensions", 
                       finalPrompt.getId(), embedding.size());

            return finalPrompt;

        } catch (Exception e) {
            logger.error("Failed to create prompt: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create prompt", e);
        }
    }

    /**
     * Update an existing prompt with automatic embedding regeneration if content changed.
     * 
     * @param id The prompt ID
     * @param updatedPrompt The updated prompt data
     * @return Optional containing the updated prompt if found
     */
    public Optional<Prompt> updatePrompt(UUID id, Prompt updatedPrompt) {
        return promptRepository.findById(id).map(existing -> {
            try {
                logger.debug("Updating prompt with ID: {}", id);

                boolean contentChanged = !existing.getContent().equals(updatedPrompt.getContent());

                // Update fields
                existing.setContent(updatedPrompt.getContent());
                existing.setMetadata(updatedPrompt.getMetadata());
                existing.setAuthor(updatedPrompt.getAuthor());
                existing.setPurpose(updatedPrompt.getPurpose());
                existing.setSuccessCriteria(updatedPrompt.getSuccessCriteria());
                existing.setTags(updatedPrompt.getTags());
                existing.setTeamOwner(updatedPrompt.getTeamOwner());
                existing.setModelTarget(updatedPrompt.getModelTarget());
                
                if (updatedPrompt.getIsActive() != null) {
                    existing.setIsActive(updatedPrompt.getIsActive());
                }

                // Save the prompt first
                Prompt savedPrompt = promptRepository.save(existing);
                
                // Regenerate embedding if content changed
                if (contentChanged) {
                    logger.debug("Content changed, regenerating embedding for prompt: {}", id);
                    String processedContent = embeddingService.preprocessContent(existing.getContent());
                    List<Double> embedding = embeddingService.generateEmbedding(processedContent);
                    com.pgvector.PGvector pgVector = embeddingService.convertToPGVector(embedding);
                    promptVectorRepository.updateEmbedding(id, pgVector);
                    
                    // Increment version on content change
                    existing.setVersion(existing.getVersion() + 1);
                    savedPrompt = promptRepository.save(existing);
                }

                logger.info("Updated prompt with ID: {}, content changed: {}", id, contentChanged);

                return savedPrompt;

            } catch (Exception e) {
                logger.error("Failed to update prompt {}: {}", id, e.getMessage(), e);
                throw new RuntimeException("Failed to update prompt", e);
            }
        });
    }

    /**
     * Soft delete a prompt by setting isActive to false.
     * 
     * @param id The prompt ID
     * @return true if the prompt was deleted, false if not found
     */
    public boolean deletePrompt(UUID id) {
        return promptRepository.findById(id).map(prompt -> {
            prompt.setIsActive(false);
            promptRepository.save(prompt);
            logger.info("Soft deleted prompt with ID: {}", id);
            return true;
        }).orElse(false);
    }

    /**
     * Hard delete a prompt (permanently remove from database).
     * 
     * @param id The prompt ID
     * @return true if the prompt was deleted, false if not found
     */
    public boolean hardDeletePrompt(UUID id) {
        try {
            promptRepository.deleteById(id);
            logger.info("Hard deleted prompt with ID: {}", id);
            return true;
        } catch (Exception e) {
            logger.warn("Failed to hard delete prompt {}: {}", id, e.getMessage());
            return false;
        }
    }

    /**
     * Fork a prompt to create a child prompt with new content.
     * 
     * @param parentId The parent prompt ID
     * @param newContent The new content for the fork
     * @param author The author of the fork
     * @return Optional containing the forked prompt if parent found
     */
    public Optional<Prompt> forkPrompt(UUID parentId, String newContent, String author) {
        return promptRepository.findById(parentId).map(parent -> {
            try {
                logger.debug("Forking prompt {} with new content length: {}", parentId, newContent.length());

                Prompt fork = parent.createChild(newContent, author);
                
                // Save the fork first without embedding
                Prompt savedFork = promptRepository.save(fork);
                
                // Generate embedding for the new content
                String processedContent = embeddingService.preprocessContent(newContent);
                List<Double> embedding = embeddingService.generateEmbedding(processedContent);
                com.pgvector.PGvector pgVector = embeddingService.convertToPGVector(embedding);
                promptVectorRepository.updateEmbedding(savedFork.getId(), pgVector);

                logger.info("Created fork with ID: {} from parent: {}", savedFork.getId(), parentId);

                return savedFork;

            } catch (Exception e) {
                logger.error("Failed to fork prompt {}: {}", parentId, e.getMessage(), e);
                throw new RuntimeException("Failed to fork prompt", e);
            }
        });
    }

    /**
     * Find similar prompts to a given prompt.
     * 
     * @param promptId The prompt ID to find similar prompts for
     * @param limit Maximum number of results
     * @return List of similar prompts with similarity scores
     */
    public List<SimilarityService.SimilarPromptResult> findSimilarPrompts(UUID promptId, int limit) {
        return promptRepository.findById(promptId)
                .map(prompt -> {
                    // Use JdbcTemplate to retrieve embedding (bypasses Hibernate type mapping issues)
                    com.pgvector.PGvector pgVector = promptVectorRepository.getEmbedding(promptId);
                    
                    if (pgVector == null) {
                        logger.warn("Prompt {} has no embedding, cannot find similar prompts", promptId);
                        return List.<SimilarityService.SimilarPromptResult>of();
                    }
                    
                    logger.debug("Retrieved embedding for prompt {}: {} dimensions", promptId, pgVector.toArray().length);
                    List<Double> embedding = embeddingService.convertFromPGVector(pgVector);
                    return similarityService.findSimilarPrompts(embedding, limit);
                })
                .orElse(List.of());
    }

    /**
     * Find similar prompts to given content.
     * 
     * @param content The content to find similar prompts for
     * @param limit Maximum number of results
     * @return List of similar prompts with similarity scores
     */
    public List<SimilarityService.SimilarPromptResult> findSimilarPrompts(String content, int limit) {
        return similarityService.findSimilarPrompts(content, limit);
    }

    /**
     * Classify a prompt based on similarity to existing prompts.
     * 
     * @param content The prompt content to classify
     * @return Classification result
     */
    public SimilarityService.PromptClassificationResult classifyPrompt(String content) {
        return similarityService.classifyPrompt(content);
    }

    /**
     * Get prompts by team owner.
     * 
     * @param teamOwner The team owner
     * @return List of prompts for the team
     */
    public List<Prompt> getPromptsByTeam(String teamOwner) {
        return promptRepository.findByTeamOwner(teamOwner);
    }

    /**
     * Get prompts by author.
     * 
     * @param author The author
     * @return List of prompts by the author
     */
    public List<Prompt> getPromptsByAuthor(String author) {
        return promptRepository.findByAuthor(author);
    }

    /**
     * Get child prompts of a parent.
     * 
     * @param parentId The parent prompt ID
     * @return List of child prompts
     */
    public List<Prompt> getChildPrompts(UUID parentId) {
        return promptRepository.findByParentId(parentId);
    }

    /**
     * Search prompts by content.
     * 
     * @param searchTerm The search term
     * @return List of prompts matching the search term
     */
    public List<Prompt> searchPrompts(String searchTerm) {
        return promptRepository.searchByContent(searchTerm);
    }

    /**
     * Get prompt statistics for a team.
     * 
     * @param teamOwner The team owner
     * @return Statistics object
     */
    public PromptStatistics getTeamStatistics(String teamOwner) {
        long totalPrompts = promptRepository.countByTeamOwner(teamOwner);
        // Additional statistics can be added here
        return new PromptStatistics(totalPrompts, 0, 0); // Placeholder for now
    }

    /**
     * Statistics object for prompt metrics.
     */
    public static class PromptStatistics {
        private final long totalPrompts;
        private final long totalUsages;
        private final long totalForks;

        public PromptStatistics(long totalPrompts, long totalUsages, long totalForks) {
            this.totalPrompts = totalPrompts;
            this.totalUsages = totalUsages;
            this.totalForks = totalForks;
        }

        public long getTotalPrompts() { return totalPrompts; }
        public long getTotalUsages() { return totalUsages; }
        public long getTotalForks() { return totalForks; }
    }
}
