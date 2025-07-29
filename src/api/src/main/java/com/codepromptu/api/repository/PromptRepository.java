package com.codepromptu.api.repository;

import com.codepromptu.shared.domain.Prompt;
import com.pgvector.PGvector;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PromptRepository extends JpaRepository<Prompt, UUID> {
    
    /**
     * Find similar prompts using pgvector cosine similarity search.
     * Returns prompts ordered by similarity (most similar first).
     * 
     * @param embedding The embedding vector to search for
     * @param limit Maximum number of results to return
     * @return List of similar prompts
     */
    @Query(value = """
        SELECT p.* FROM prompts p 
        WHERE p.is_active = true 
        AND p.embedding IS NOT NULL
        ORDER BY p.embedding <=> :embedding
        LIMIT :limit
        """, nativeQuery = true)
    List<Prompt> findSimilarPrompts(@Param("embedding") PGvector embedding, @Param("limit") int limit);
    
    /**
     * Find prompts above a specific similarity threshold.
     * Uses cosine distance where smaller distance = higher similarity.
     * 
     * @param embedding The embedding vector to search for
     * @param threshold Minimum similarity threshold (0.0 to 1.0)
     * @return List of prompts above the threshold
     */
    @Query(value = """
        SELECT p.* FROM prompts p 
        WHERE p.is_active = true 
        AND p.embedding IS NOT NULL
        AND (1 - (p.embedding <=> :embedding)) >= :threshold
        ORDER BY p.embedding <=> :embedding
        """, nativeQuery = true)
    List<Prompt> findPromptsByThreshold(@Param("embedding") PGvector embedding, @Param("threshold") double threshold);
    
    /**
     * Find prompts by team owner with active status.
     * 
     * @param teamOwner The team owner to filter by
     * @param pageable Pagination information
     * @return Page of prompts for the team
     */
    Page<Prompt> findByTeamOwnerAndIsActiveTrueOrderByCreatedAtDesc(String teamOwner, Pageable pageable);
    
    /**
     * Find active prompts ordered by creation date.
     * 
     * @param pageable Pagination information
     * @return Page of active prompts
     */
    Page<Prompt> findByIsActiveTrueOrderByCreatedAtDesc(Pageable pageable);
    
    /**
     * Find child prompts of a parent prompt.
     * 
     * @param parentId The parent prompt ID
     * @return List of child prompts
     */
    List<Prompt> findByParentIdAndIsActiveTrueOrderByCreatedAtDesc(UUID parentId);
    
    /**
     * Find prompts by author.
     * 
     * @param author The author to filter by
     * @param pageable Pagination information
     * @return Page of prompts by the author
     */
    Page<Prompt> findByAuthorAndIsActiveTrueOrderByCreatedAtDesc(String author, Pageable pageable);
    
    /**
     * Find prompts by model target.
     * 
     * @param modelTarget The target model to filter by
     * @param pageable Pagination information
     * @return Page of prompts for the model
     */
    Page<Prompt> findByModelTargetAndIsActiveTrueOrderByCreatedAtDesc(String modelTarget, Pageable pageable);
    
    /**
     * Find prompts containing specific tags.
     * Uses PostgreSQL array contains operator.
     * 
     * @param tag The tag to search for
     * @param pageable Pagination information
     * @return Page of prompts containing the tag
     */
    @Query(value = """
        SELECT p.* FROM prompts p 
        WHERE p.is_active = true 
        AND :tag = ANY(p.tags)
        ORDER BY p.created_at DESC
        """, nativeQuery = true)
    Page<Prompt> findByTagAndIsActiveTrue(@Param("tag") String tag, Pageable pageable);
    
    /**
     * Search prompts by content using full-text search.
     * 
     * @param searchTerm The search term
     * @param pageable Pagination information
     * @return Page of prompts matching the search term
     */
    @Query(value = """
        SELECT p.* FROM prompts p 
        WHERE p.is_active = true 
        AND (
            p.content ILIKE %:searchTerm% 
            OR p.purpose ILIKE %:searchTerm%
            OR p.author ILIKE %:searchTerm%
        )
        ORDER BY p.created_at DESC
        """, nativeQuery = true)
    Page<Prompt> searchByContent(@Param("searchTerm") String searchTerm, Pageable pageable);
    
    /**
     * Count prompts by team owner.
     * 
     * @param teamOwner The team owner
     * @return Count of prompts for the team
     */
    long countByTeamOwnerAndIsActiveTrue(String teamOwner);
    
    /**
     * Count prompts by author.
     * 
     * @param author The author
     * @return Count of prompts by the author
     */
    long countByAuthorAndIsActiveTrue(String author);
    
    /**
     * Find root prompts (prompts without parents).
     * 
     * @param pageable Pagination information
     * @return Page of root prompts
     */
    Page<Prompt> findByParentIsNullAndIsActiveTrueOrderByCreatedAtDesc(Pageable pageable);
}
