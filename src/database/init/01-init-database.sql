-- Initialize CodePromptu Database
-- This script runs when the PostgreSQL container starts for the first time

-- Create the main database (already created by POSTGRES_DB env var)
-- But we can add any additional setup here

-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Create additional schemas if needed
CREATE SCHEMA IF NOT EXISTS analytics;
CREATE SCHEMA IF NOT EXISTS audit;

-- Set up basic permissions
GRANT ALL PRIVILEGES ON DATABASE codepromptu TO codepromptu_user;
GRANT ALL PRIVILEGES ON SCHEMA public TO codepromptu_user;
GRANT ALL PRIVILEGES ON SCHEMA analytics TO codepromptu_user;
GRANT ALL PRIVILEGES ON SCHEMA audit TO codepromptu_user;

-- Create audit table for tracking changes
CREATE TABLE IF NOT EXISTS audit.change_log (
    id SERIAL PRIMARY KEY,
    table_name VARCHAR(255) NOT NULL,
    operation VARCHAR(10) NOT NULL,
    old_values JSONB,
    new_values JSONB,
    changed_by VARCHAR(255),
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Grant permissions on audit schema
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA audit TO codepromptu_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA audit TO codepromptu_user;
