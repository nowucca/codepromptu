-- Fix Embedding Index Migration
-- This migration fixes the embedding storage issue by removing the problematic btree index
-- and replacing it with a proper vector index for similarity search

-- Drop the problematic btree index that prevents 1536-dimensional vectors from being stored
-- This index causes: "index row size 6160 exceeds btree version 4 maximum 2704"
DROP INDEX IF EXISTS idx_prompts_embedding;

-- Create a function to conditionally create the vector index based on data volume
-- The ivfflat index works better with more data, so we'll create it conditionally
CREATE OR REPLACE FUNCTION create_embedding_index_if_needed() RETURNS void AS $$
DECLARE
    prompt_count INTEGER;
BEGIN
    -- Count the number of prompts with embeddings
    SELECT COUNT(*) INTO prompt_count 
    FROM prompts 
    WHERE embedding IS NOT NULL;
    
    -- Only create the index if we have sufficient data (at least 100 prompts with embeddings)
    -- This prevents the "ivfflat index created with little data" warning
    IF prompt_count >= 100 THEN
        -- Drop existing index if it exists
        DROP INDEX IF EXISTS idx_prompts_embedding_ivfflat;
        
        -- Create the proper vector index for similarity search
        CREATE INDEX idx_prompts_embedding_ivfflat ON prompts 
        USING ivfflat (embedding vector_cosine_ops) 
        WITH (lists = GREATEST(prompt_count / 1000, 10));
        
        RAISE NOTICE 'Created vector index with % prompts and % lists', prompt_count, GREATEST(prompt_count / 1000, 10);
    ELSE
        RAISE NOTICE 'Skipping vector index creation - only % prompts with embeddings (need at least 100)', prompt_count;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Create a function to rebuild the index when we have enough data
CREATE OR REPLACE FUNCTION rebuild_embedding_index() RETURNS void AS $$
BEGIN
    PERFORM create_embedding_index_if_needed();
END;
$$ LANGUAGE plpgsql;

-- Grant execute permissions on the functions
GRANT EXECUTE ON FUNCTION create_embedding_index_if_needed() TO codepromptu_user;
GRANT EXECUTE ON FUNCTION rebuild_embedding_index() TO codepromptu_user;

-- Log the migration
INSERT INTO audit.change_log (table_name, operation, new_values, changed_by, changed_at)
VALUES (
    'prompts', 
    'INDEX_FIX', 
    '{"action": "removed_btree_index", "reason": "prevents_1536_dimensional_vectors", "solution": "conditional_ivfflat_index"}',
    'migration_02',
    CURRENT_TIMESTAMP
);

-- Add a comment to document this fix
COMMENT ON TABLE prompts IS 'Prompts table with vector embeddings. Note: btree index on embedding column was removed due to size constraints for 1536-dimensional vectors. Use rebuild_embedding_index() function when sufficient data is available.';
