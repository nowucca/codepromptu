-- Test schema for integration testing with pgvector
-- This file is used by TestContainers to initialize the test database

-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Create prompts table with vector column
CREATE TABLE IF NOT EXISTS prompts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT NOT NULL,
    author VARCHAR(255),
    purpose TEXT,
    success_criteria TEXT,
    metadata JSONB,
    tags TEXT[],
    team_owner VARCHAR(255),
    model_target VARCHAR(255),
    parent_id UUID REFERENCES prompts(id),
    version INTEGER DEFAULT 1,
    is_active BOOLEAN DEFAULT true,
    embedding vector(1536),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_prompts_author ON prompts(author);
CREATE INDEX IF NOT EXISTS idx_prompts_team_owner ON prompts(team_owner);
CREATE INDEX IF NOT EXISTS idx_prompts_is_active ON prompts(is_active);
CREATE INDEX IF NOT EXISTS idx_prompts_parent_id ON prompts(parent_id);
CREATE INDEX IF NOT EXISTS idx_prompts_created_at ON prompts(created_at);

-- Note: Vector index will be created automatically by the embedding index service
-- when there are enough prompts in the database
