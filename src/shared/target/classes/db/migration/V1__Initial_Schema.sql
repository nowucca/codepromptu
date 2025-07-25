-- CodePromptu Initial Database Schema
-- This migration creates the core tables for prompt storage, templates, usage tracking, and evaluation

-- Enable pgvector extension for vector similarity search
CREATE EXTENSION IF NOT EXISTS vector;

-- Create prompts table with vector embeddings
CREATE TABLE prompts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id UUID REFERENCES prompts(id),
    content TEXT NOT NULL,
    embedding VECTOR(1536), -- OpenAI ada-002 embedding size
    metadata JSONB DEFAULT '{}',
    author VARCHAR(255),
    purpose TEXT,
    success_criteria TEXT,
    tags TEXT[],
    team_owner VARCHAR(255),
    model_target VARCHAR(255),
    version INTEGER DEFAULT 1,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

-- Create template definitions for prompt clustering
CREATE TABLE prompt_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shell TEXT NOT NULL,
    fragments TEXT[],
    embedding VECTOR(1536),
    variable_count INTEGER DEFAULT 0,
    usage_count INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

-- Create usage tracking table for every API call
CREATE TABLE prompt_usages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prompt_id UUID REFERENCES prompts(id),
    template_id UUID REFERENCES prompt_templates(id),
    conversation_id UUID,
    raw_content TEXT NOT NULL,
    variables JSONB DEFAULT '{}',
    request_timestamp TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    response_timestamp TIMESTAMPTZ,
    tokens_input INTEGER,
    tokens_output INTEGER,
    model_used VARCHAR(255),
    provider VARCHAR(100),
    status VARCHAR(50),
    response_content TEXT,
    latency_ms INTEGER,
    client_ip INET,
    user_agent TEXT,
    api_key_hash VARCHAR(255),
    request_metadata JSONB DEFAULT '{}',
    error_message TEXT,
    http_status INTEGER
);

-- Create cross-references table for related prompts
CREATE TABLE prompt_crossrefs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_prompt_id UUID REFERENCES prompts(id) NOT NULL,
    target_prompt_id UUID REFERENCES prompts(id) NOT NULL,
    relationship_type VARCHAR(100),
    similarity_score FLOAT,
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    
    -- Ensure source and target are different
    CONSTRAINT chk_different_prompts CHECK (source_prompt_id != target_prompt_id)
);

-- Create evaluation metrics and feedback table
CREATE TABLE prompt_evaluations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prompt_id UUID REFERENCES prompts(id) NOT NULL,
    usage_id UUID REFERENCES prompt_usages(id),
    evaluation_type VARCHAR(100),
    score FLOAT,
    max_score FLOAT DEFAULT 1.0,
    feedback TEXT,
    evaluator VARCHAR(255),
    criteria JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

-- Create users table for authentication and ownership
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(255) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    full_name VARCHAR(255),
    team VARCHAR(255),
    role VARCHAR(100) DEFAULT 'user',
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    last_login TIMESTAMPTZ
);

-- Create teams table for organization management
CREATE TABLE teams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) UNIQUE NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

-- Create conversations table for session tracking
CREATE TABLE conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id VARCHAR(255),
    user_id UUID REFERENCES users(id),
    started_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    ended_at TIMESTAMPTZ,
    context JSONB DEFAULT '{}'
);

-- Create indexes for performance

-- Vector similarity search indexes (using IVFFlat)
CREATE INDEX idx_prompts_embedding ON prompts USING ivfflat (embedding vector_cosine_ops);
CREATE INDEX idx_templates_embedding ON prompt_templates USING ivfflat (embedding vector_cosine_ops);

-- Standard indexes for prompts
CREATE INDEX idx_prompts_parent_id ON prompts(parent_id);
CREATE INDEX idx_prompts_team_owner ON prompts(team_owner);
CREATE INDEX idx_prompts_created_at ON prompts(created_at);
CREATE INDEX idx_prompts_is_active ON prompts(is_active);
CREATE INDEX idx_prompts_tags ON prompts USING GIN(tags);
CREATE INDEX idx_prompts_metadata ON prompts USING GIN(metadata);
CREATE INDEX idx_prompts_model_target ON prompts(model_target);
CREATE INDEX idx_prompts_author ON prompts(author);

-- Indexes for prompt templates
CREATE INDEX idx_templates_usage_count ON prompt_templates(usage_count);
CREATE INDEX idx_templates_variable_count ON prompt_templates(variable_count);
CREATE INDEX idx_templates_created_at ON prompt_templates(created_at);

-- Indexes for prompt usages
CREATE INDEX idx_usages_prompt_id ON prompt_usages(prompt_id);
CREATE INDEX idx_usages_template_id ON prompt_usages(template_id);
CREATE INDEX idx_usages_conversation_id ON prompt_usages(conversation_id);
CREATE INDEX idx_usages_request_timestamp ON prompt_usages(request_timestamp);
CREATE INDEX idx_usages_model_used ON prompt_usages(model_used);
CREATE INDEX idx_usages_provider ON prompt_usages(provider);
CREATE INDEX idx_usages_status ON prompt_usages(status);
CREATE INDEX idx_usages_api_key_hash ON prompt_usages(api_key_hash);

-- Indexes for cross-references
CREATE INDEX idx_crossrefs_source_prompt ON prompt_crossrefs(source_prompt_id);
CREATE INDEX idx_crossrefs_target_prompt ON prompt_crossrefs(target_prompt_id);
CREATE INDEX idx_crossrefs_similarity ON prompt_crossrefs(similarity_score);
CREATE INDEX idx_crossrefs_relationship ON prompt_crossrefs(relationship_type);

-- Indexes for evaluations
CREATE INDEX idx_evaluations_prompt_id ON prompt_evaluations(prompt_id);
CREATE INDEX idx_evaluations_usage_id ON prompt_evaluations(usage_id);
CREATE INDEX idx_evaluations_score ON prompt_evaluations(score);
CREATE INDEX idx_evaluations_type ON prompt_evaluations(evaluation_type);
CREATE INDEX idx_evaluations_created_at ON prompt_evaluations(created_at);
CREATE INDEX idx_evaluations_evaluator ON prompt_evaluations(evaluator);

-- Indexes for users and teams
CREATE INDEX idx_users_team ON users(team);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_is_active ON users(is_active);
CREATE INDEX idx_users_created_at ON users(created_at);

-- Indexes for conversations
CREATE INDEX idx_conversations_user_id ON conversations(user_id);
CREATE INDEX idx_conversations_session_id ON conversations(session_id);
CREATE INDEX idx_conversations_started_at ON conversations(started_at);

-- Create functions for automatic timestamp updates
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers for automatic timestamp updates
CREATE TRIGGER update_prompts_updated_at 
    BEFORE UPDATE ON prompts 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_templates_updated_at 
    BEFORE UPDATE ON prompt_templates 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Create function for vector similarity search
CREATE OR REPLACE FUNCTION find_similar_prompts(
    query_embedding VECTOR(1536),
    similarity_threshold FLOAT DEFAULT 0.7,
    max_results INTEGER DEFAULT 10
)
RETURNS TABLE (
    prompt_id UUID,
    content TEXT,
    similarity_score FLOAT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        p.id,
        p.content,
        1 - (p.embedding <=> query_embedding) AS similarity
    FROM prompts p
    WHERE p.embedding IS NOT NULL
        AND p.is_active = true
        AND (1 - (p.embedding <=> query_embedding)) >= similarity_threshold
    ORDER BY p.embedding <=> query_embedding
    LIMIT max_results;
END;
$$ LANGUAGE plpgsql;

-- Create function for template matching
CREATE OR REPLACE FUNCTION find_matching_templates(
    query_embedding VECTOR(1536),
    similarity_threshold FLOAT DEFAULT 0.8,
    max_results INTEGER DEFAULT 5
)
RETURNS TABLE (
    template_id UUID,
    shell TEXT,
    similarity_score FLOAT,
    variable_count INTEGER
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        t.id,
        t.shell,
        1 - (t.embedding <=> query_embedding) AS similarity,
        t.variable_count
    FROM prompt_templates t
    WHERE t.embedding IS NOT NULL
        AND (1 - (t.embedding <=> query_embedding)) >= similarity_threshold
    ORDER BY t.embedding <=> query_embedding
    LIMIT max_results;
END;
$$ LANGUAGE plpgsql;

-- Insert default admin user (password should be changed in production)
INSERT INTO users (username, email, full_name, role, is_active) 
VALUES ('admin', 'admin@codepromptu.com', 'System Administrator', 'admin', true);

-- Insert default team
INSERT INTO teams (name, description) 
VALUES ('Default', 'Default team for new users');

-- Create view for prompt statistics
CREATE VIEW prompt_stats AS
SELECT 
    p.id,
    p.content,
    p.author,
    p.team_owner,
    p.created_at,
    COUNT(pu.id) as usage_count,
    AVG(pe.score) as avg_evaluation_score,
    COUNT(pe.id) as evaluation_count,
    COUNT(pc_out.id) as outgoing_crossrefs,
    COUNT(pc_in.id) as incoming_crossrefs
FROM prompts p
LEFT JOIN prompt_usages pu ON p.id = pu.prompt_id
LEFT JOIN prompt_evaluations pe ON p.id = pe.prompt_id
LEFT JOIN prompt_crossrefs pc_out ON p.id = pc_out.source_prompt_id
LEFT JOIN prompt_crossrefs pc_in ON p.id = pc_in.target_prompt_id
WHERE p.is_active = true
GROUP BY p.id, p.content, p.author, p.team_owner, p.created_at;

-- Create view for usage analytics
CREATE VIEW usage_analytics AS
SELECT 
    DATE_TRUNC('day', request_timestamp) as usage_date,
    model_used,
    provider,
    COUNT(*) as request_count,
    COUNT(CASE WHEN status = 'success' THEN 1 END) as success_count,
    COUNT(CASE WHEN status = 'error' THEN 1 END) as error_count,
    AVG(latency_ms) as avg_latency_ms,
    SUM(tokens_input) as total_input_tokens,
    SUM(tokens_output) as total_output_tokens
FROM prompt_usages
GROUP BY DATE_TRUNC('day', request_timestamp), model_used, provider
ORDER BY usage_date DESC;

-- Add comments for documentation
COMMENT ON TABLE prompts IS 'Core table storing prompts with metadata and vector embeddings';
COMMENT ON TABLE prompt_templates IS 'Template shells derived from clustering similar prompts';
COMMENT ON TABLE prompt_usages IS 'Complete log of every LLM API call with context';
COMMENT ON TABLE prompt_crossrefs IS 'Cross-references between related prompts across lineages';
COMMENT ON TABLE prompt_evaluations IS 'Evaluation metrics and feedback for prompts';
COMMENT ON TABLE users IS 'User accounts for authentication and ownership';
COMMENT ON TABLE teams IS 'Team/organization management';
COMMENT ON TABLE conversations IS 'Session tracking for grouping related API calls';

COMMENT ON COLUMN prompts.embedding IS 'Vector embedding using OpenAI ada-002 (1536 dimensions)';
COMMENT ON COLUMN prompts.metadata IS 'Flexible JSONB storage for prompt-specific metadata';
COMMENT ON COLUMN prompt_templates.shell IS 'Template with variable placeholders (__VAR1__, __VAR2__, etc.)';
COMMENT ON COLUMN prompt_usages.variables IS 'Extracted variables if matched to a template';
COMMENT ON COLUMN prompt_crossrefs.relationship_type IS 'Type of relationship: similar, variant, improvement, etc.';
