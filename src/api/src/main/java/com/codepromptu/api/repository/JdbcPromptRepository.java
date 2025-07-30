package com.codepromptu.api.repository;

import com.codepromptu.shared.domain.Prompt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC Template-based repository for Prompt operations.
 * Replaces the JPA-based PromptRepository to avoid Hibernate issues.
 */
@Repository
public class JdbcPromptRepository {

    private static final Logger logger = LoggerFactory.getLogger(JdbcPromptRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public JdbcPromptRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        logger.info("JdbcPromptRepository initialized");
    }

    /**
     * Save a new prompt to the database.
     */
    public Prompt save(Prompt prompt) {
        try {
            if (prompt.getId() == null) {
                prompt.setId(UUID.randomUUID());
            }
            
            if (prompt.getCreatedAt() == null) {
                prompt.setCreatedAt(Instant.now());
            }
            prompt.setUpdatedAt(Instant.now());

            String sql = """
                INSERT INTO prompts (id, content, author, purpose, success_criteria, metadata, tags, 
                                   team_owner, model_target, parent_id, version, is_active, embedding, 
                                   created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, CAST(? AS vector), ?, ?)
                """;

            String metadataJson = prompt.getMetadata() != null ? 
                objectMapper.writeValueAsString(prompt.getMetadata()) : null;
            
            String embeddingString = prompt.getEmbedding() != null ? 
                prompt.getEmbedding().toString() : null;

            jdbcTemplate.update(sql,
                prompt.getId(),
                prompt.getContent(),
                prompt.getAuthor(),
                prompt.getPurpose(),
                prompt.getSuccessCriteria(),
                metadataJson,
                prompt.getTags(),
                prompt.getTeamOwner(),
                prompt.getModelTarget(),
                prompt.getParent() != null ? prompt.getParent().getId() : null,
                prompt.getVersion(),
                prompt.getIsActive(),
                embeddingString,
                Timestamp.from(prompt.getCreatedAt()),
                Timestamp.from(prompt.getUpdatedAt())
            );

            logger.info("Successfully saved prompt with ID: {}", prompt.getId());
            
            // Retrieve the complete prompt including embedding from database
            return findById(prompt.getId()).orElse(prompt);

        } catch (Exception e) {
            logger.error("Failed to save prompt: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save prompt", e);
        }
    }

    /**
     * Update an existing prompt.
     */
    public Prompt update(Prompt prompt) {
        try {
            prompt.setUpdatedAt(Instant.now());

            String sql = """
                UPDATE prompts SET content = ?, author = ?, purpose = ?, success_criteria = ?, 
                                 metadata = ?::jsonb, tags = ?, team_owner = ?, model_target = ?, 
                                 parent_id = ?, version = ?, is_active = ?, embedding = CAST(? AS vector), 
                                 updated_at = ?
                WHERE id = ?
                """;

            String metadataJson = prompt.getMetadata() != null ? 
                objectMapper.writeValueAsString(prompt.getMetadata()) : null;
            
            String embeddingString = prompt.getEmbedding() != null ? 
                prompt.getEmbedding().toString() : null;

            int rowsUpdated = jdbcTemplate.update(sql,
                prompt.getContent(),
                prompt.getAuthor(),
                prompt.getPurpose(),
                prompt.getSuccessCriteria(),
                metadataJson,
                prompt.getTags(),
                prompt.getTeamOwner(),
                prompt.getModelTarget(),
                prompt.getParent() != null ? prompt.getParent().getId() : null,
                prompt.getVersion(),
                prompt.getIsActive(),
                embeddingString,
                Timestamp.from(prompt.getUpdatedAt()),
                prompt.getId()
            );

            if (rowsUpdated == 0) {
                throw new RuntimeException("Prompt not found for update: " + prompt.getId());
            }

            logger.info("Successfully updated prompt with ID: {}", prompt.getId());
            return prompt;

        } catch (Exception e) {
            logger.error("Failed to update prompt: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update prompt", e);
        }
    }

    /**
     * Find a prompt by ID.
     */
    public Optional<Prompt> findById(UUID id) {
        try {
            String sql = """
                SELECT id, content, author, purpose, success_criteria, metadata, tags, 
                       team_owner, model_target, parent_id, version, is_active, embedding,
                       created_at, updated_at
                FROM prompts 
                WHERE id = ?
                """;

            Prompt prompt = jdbcTemplate.queryForObject(sql, new PromptRowMapper(), id);
            return Optional.of(prompt);

        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Failed to find prompt by ID {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to find prompt", e);
        }
    }

    /**
     * Find all active prompts.
     */
    public List<Prompt> findAllActive() {
        try {
            String sql = """
                SELECT id, content, author, purpose, success_criteria, metadata, tags, 
                       team_owner, model_target, parent_id, version, is_active, 
                       created_at, updated_at
                FROM prompts 
                WHERE is_active = true
                ORDER BY created_at DESC
                """;

            return jdbcTemplate.query(sql, new PromptRowMapper());

        } catch (Exception e) {
            logger.error("Failed to find all active prompts: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to find active prompts", e);
        }
    }

    /**
     * Find prompts by team owner.
     */
    public List<Prompt> findByTeamOwner(String teamOwner) {
        try {
            String sql = """
                SELECT id, content, author, purpose, success_criteria, metadata, tags, 
                       team_owner, model_target, parent_id, version, is_active, 
                       created_at, updated_at
                FROM prompts 
                WHERE team_owner = ? AND is_active = true
                ORDER BY created_at DESC
                """;

            return jdbcTemplate.query(sql, new PromptRowMapper(), teamOwner);

        } catch (Exception e) {
            logger.error("Failed to find prompts by team owner {}: {}", teamOwner, e.getMessage(), e);
            throw new RuntimeException("Failed to find prompts by team owner", e);
        }
    }

    /**
     * Find prompts by author.
     */
    public List<Prompt> findByAuthor(String author) {
        try {
            String sql = """
                SELECT id, content, author, purpose, success_criteria, metadata, tags, 
                       team_owner, model_target, parent_id, version, is_active, 
                       created_at, updated_at
                FROM prompts 
                WHERE author = ? AND is_active = true
                ORDER BY created_at DESC
                """;

            return jdbcTemplate.query(sql, new PromptRowMapper(), author);

        } catch (Exception e) {
            logger.error("Failed to find prompts by author {}: {}", author, e.getMessage(), e);
            throw new RuntimeException("Failed to find prompts by author", e);
        }
    }

    /**
     * Find child prompts by parent ID.
     */
    public List<Prompt> findByParentId(UUID parentId) {
        try {
            String sql = """
                SELECT id, content, author, purpose, success_criteria, metadata, tags, 
                       team_owner, model_target, parent_id, version, is_active, 
                       created_at, updated_at
                FROM prompts 
                WHERE parent_id = ? AND is_active = true
                ORDER BY created_at DESC
                """;

            return jdbcTemplate.query(sql, new PromptRowMapper(), parentId);

        } catch (Exception e) {
            logger.error("Failed to find prompts by parent ID {}: {}", parentId, e.getMessage(), e);
            throw new RuntimeException("Failed to find prompts by parent ID", e);
        }
    }

    /**
     * Search prompts by content.
     */
    public List<Prompt> searchByContent(String searchTerm) {
        try {
            String sql = """
                SELECT id, content, author, purpose, success_criteria, metadata, tags, 
                       team_owner, model_target, parent_id, version, is_active, 
                       created_at, updated_at
                FROM prompts 
                WHERE is_active = true 
                AND (content ILIKE ? OR purpose ILIKE ? OR author ILIKE ?)
                ORDER BY created_at DESC
                """;

            String searchPattern = "%" + searchTerm + "%";
            return jdbcTemplate.query(sql, new PromptRowMapper(), 
                searchPattern, searchPattern, searchPattern);

        } catch (Exception e) {
            logger.error("Failed to search prompts by content '{}': {}", searchTerm, e.getMessage(), e);
            throw new RuntimeException("Failed to search prompts", e);
        }
    }

    /**
     * Delete a prompt by ID (soft delete by setting is_active = false).
     */
    public void deleteById(UUID id) {
        try {
            String sql = "UPDATE prompts SET is_active = false, updated_at = ? WHERE id = ?";
            
            int rowsUpdated = jdbcTemplate.update(sql, Timestamp.from(Instant.now()), id);
            
            if (rowsUpdated == 0) {
                throw new RuntimeException("Prompt not found for deletion: " + id);
            }

            logger.info("Successfully deleted prompt with ID: {}", id);

        } catch (Exception e) {
            logger.error("Failed to delete prompt {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to delete prompt", e);
        }
    }

    /**
     * Count active prompts by team owner.
     */
    public long countByTeamOwner(String teamOwner) {
        try {
            String sql = "SELECT COUNT(*) FROM prompts WHERE team_owner = ? AND is_active = true";
            Long count = jdbcTemplate.queryForObject(sql, Long.class, teamOwner);
            return count != null ? count : 0;

        } catch (Exception e) {
            logger.error("Failed to count prompts by team owner {}: {}", teamOwner, e.getMessage(), e);
            throw new RuntimeException("Failed to count prompts", e);
        }
    }

    /**
     * Count active prompts by author.
     */
    public long countByAuthor(String author) {
        try {
            String sql = "SELECT COUNT(*) FROM prompts WHERE author = ? AND is_active = true";
            Long count = jdbcTemplate.queryForObject(sql, Long.class, author);
            return count != null ? count : 0;

        } catch (Exception e) {
            logger.error("Failed to count prompts by author {}: {}", author, e.getMessage(), e);
            throw new RuntimeException("Failed to count prompts", e);
        }
    }

    /**
     * Find prompts above a specific similarity threshold using pgvector cosine similarity.
     */
    public List<Prompt> findPromptsByThreshold(PGvector embedding, double threshold) {
        try {
            String sql = """
                SELECT id, content, author, purpose, success_criteria, metadata, tags, 
                       team_owner, model_target, parent_id, version, is_active, embedding,
                       created_at, updated_at
                FROM prompts 
                WHERE is_active = true 
                AND embedding IS NOT NULL
                AND (1 - (embedding <=> CAST(? AS vector))) >= ?
                ORDER BY embedding <=> CAST(? AS vector)
                """;

            String embeddingString = embedding.toString();
            return jdbcTemplate.query(sql, new PromptRowMapper(), 
                embeddingString, threshold, embeddingString);

        } catch (Exception e) {
            logger.error("Failed to find prompts by threshold {}: {}", threshold, e.getMessage(), e);
            throw new RuntimeException("Failed to find prompts by threshold", e);
        }
    }

    /**
     * RowMapper for converting ResultSet to Prompt objects.
     */
    private class PromptRowMapper implements RowMapper<Prompt> {
        @Override
        public Prompt mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                // Parse metadata JSON
                JsonNode metadata = null;
                String metadataString = rs.getString("metadata");
                if (metadataString != null) {
                    metadata = objectMapper.readTree(metadataString);
                }

                // Parse tags array
                String[] tags = null;
                Array tagsArray = rs.getArray("tags");
                if (tagsArray != null) {
                    tags = (String[]) tagsArray.getArray();
                }

                // Parse embedding vector
                PGvector embedding = null;
                try {
                    Object embeddingObj = rs.getObject("embedding");
                    if (embeddingObj != null) {
                        if (embeddingObj instanceof PGvector) {
                            embedding = (PGvector) embeddingObj;
                        } else if (embeddingObj instanceof org.postgresql.util.PGobject) {
                            // Convert PGobject to PGvector
                            org.postgresql.util.PGobject pgObj = (org.postgresql.util.PGobject) embeddingObj;
                            String vectorString = pgObj.getValue();
                            if (vectorString != null) {
                                embedding = new PGvector(vectorString);
                            }
                        }
                    }
                } catch (Exception e) {
                    // Embedding column might not be selected in some queries
                    logger.debug("Could not retrieve embedding for prompt {}: {}", rs.getString("id"), e.getMessage());
                }

                return Prompt.builder()
                    .id(UUID.fromString(rs.getString("id")))
                    .content(rs.getString("content"))
                    .author(rs.getString("author"))
                    .purpose(rs.getString("purpose"))
                    .successCriteria(rs.getString("success_criteria"))
                    .metadata(metadata)
                    .tags(tags)
                    .teamOwner(rs.getString("team_owner"))
                    .modelTarget(rs.getString("model_target"))
                    .version(rs.getInt("version"))
                    .isActive(rs.getBoolean("is_active"))
                    .embedding(embedding)
                    .createdAt(rs.getTimestamp("created_at").toInstant())
                    .updatedAt(rs.getTimestamp("updated_at").toInstant())
                    .build();

            } catch (Exception e) {
                logger.error("Failed to map row to Prompt: {}", e.getMessage(), e);
                throw new SQLException("Failed to map row to Prompt", e);
            }
        }
    }
}
