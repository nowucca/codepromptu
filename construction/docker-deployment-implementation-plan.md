# 🐳 CodePromptu Docker Deployment Implementation Plan

## Overview

This document outlines the implementation plan for containerizing CodePromptu as a set of Docker images that can be deployed in production environments. The system will be decomposed into microservices, each with its own container, following cloud-native best practices.

## System Architecture for Docker Deployment

Based on the existing CodePromptu design, the system will be split into the following containerized services using **Java Spring Boot** with **Spring AI** framework:

### Core Services

1. **API Gateway/Proxy Service** (`codepromptu-gateway`)
   - Spring Boot application with Spring Cloud Gateway
   - Handles LLM API proxying and request/response interception
   - Manages client API key pass-through using Spring Security
   - Captures prompts for analysis using Spring AOP
   - Routes requests to appropriate LLM providers via Spring AI

2. **Prompt Processing Service** (`codepromptu-processor`)
   - Spring Boot application with Spring AI integration
   - Handles prompt embedding using Spring AI's EmbeddingClient
   - Manages template induction and variable extraction
   - Performs clustering analysis (background jobs with Spring Batch)
   - Interfaces with PostgreSQL pgvector for similarity lookups

3. **Web API Service** (`codepromptu-api`)
   - Spring Boot REST API with Spring Data JPA
   - Handles forking, versioning, and metadata management
   - Provides search and discovery endpoints
   - Manages user authentication with Spring Security
   - Uses Spring AI for prompt-related operations

4. **Web UI Service** (`codepromptu-ui`)
   - React frontend for prompt management
   - Dashboard for evaluation metrics and analytics
   - Prompt evolution timeline and diff views
   - Administrative interface for template management

5. **Evaluation Service** (`codepromptu-evaluator`)
   - Spring Boot application with Spring AI
   - Processes usage metrics and performance data
   - Generates evaluation reports using Spring AI's ChatClient
   - Handles qualitative feedback collection
   - Manages success criteria tracking

### Data Services

6. **Database Service** (`codepromptu-database`)
   - PostgreSQL 15+ with pgvector extension
   - Handles both structured data and vector embeddings
   - Stores prompts, templates, usage data, metadata, and embeddings
   - Supports semantic search and similarity queries
   - Handles versioning and lineage tracking

7. **Cache Service** (`codepromptu-cache`)
   - Redis container for caching frequently accessed data
   - Session management and temporary data storage
   - Rate limiting and request throttling
   - Spring Cache integration

### Supporting Services

8. **Background Worker Service** (`codepromptu-worker`)
   - Spring Boot application with Spring Batch
   - Handles asynchronous tasks (clustering, template generation)
   - Processes evaluation pipelines
   - Manages data cleanup and maintenance tasks
   - Uses Spring AI for background processing

9. **Configuration Service** (`codepromptu-config`)
   - Spring Cloud Config Server
   - Centralized configuration management
   - Environment-specific properties
   - Feature flags and runtime configuration

10. **Monitoring Service** (`codepromptu-monitoring`)
    - Prometheus/Grafana stack for metrics collection
    - Spring Boot Actuator for health checks and metrics
    - Alerting and performance monitoring
    - Distributed tracing with Spring Cloud Sleuth

## Docker Compose Structure

```yaml
version: '3.8'
services:
  # API Gateway - Spring Cloud Gateway
  gateway:
    image: codepromptu/gateway:latest
    build:
      context: ./services/gateway
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - SPRING_DATASOURCE_URL=jdbc:postgresql://database:5432/codepromptu
      - SPRING_DATASOURCE_USERNAME=codepromptu_user
      - SPRING_DATASOURCE_PASSWORD=codepromptu_pass
      - SPRING_REDIS_HOST=cache
      - SPRING_REDIS_PORT=6379
      - CONFIG_SERVER_URI=http://config:8888
    depends_on:
      - database
      - cache
      - config
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  # Prompt Processing Service - Spring AI
  processor:
    image: codepromptu/processor:latest
    build:
      context: ./services/processor
      dockerfile: Dockerfile
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - SPRING_DATASOURCE_URL=jdbc:postgresql://database:5432/codepromptu
      - SPRING_DATASOURCE_USERNAME=codepromptu_user
      - SPRING_DATASOURCE_PASSWORD=codepromptu_pass
      - SPRING_REDIS_HOST=cache
      - CONFIG_SERVER_URI=http://config:8888
    depends_on:
      - database
      - cache
      - config
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8082/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  # Web API Service - Spring Boot REST
  api:
    image: codepromptu/api:latest
    build:
      context: ./services/api
      dockerfile: Dockerfile
    ports:
      - "8081:8081"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - SPRING_DATASOURCE_URL=jdbc:postgresql://database:5432/codepromptu
      - SPRING_DATASOURCE_USERNAME=codepromptu_user
      - SPRING_DATASOURCE_PASSWORD=codepromptu_pass
      - SPRING_REDIS_HOST=cache
      - CONFIG_SERVER_URI=http://config:8888
    depends_on:
      - database
      - cache
      - config
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  # React Frontend
  ui:
    image: codepromptu/ui:latest
    build:
      context: ./services/ui
      dockerfile: Dockerfile
    ports:
      - "3000:3000"
    environment:
      - REACT_APP_API_URL=http://localhost:8081
      - REACT_APP_GATEWAY_URL=http://localhost:8080
    depends_on:
      - api
      - gateway

  # Evaluation Service - Spring AI
  evaluator:
    image: codepromptu/evaluator:latest
    build:
      context: ./services/evaluator
      dockerfile: Dockerfile
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - SPRING_DATASOURCE_URL=jdbc:postgresql://database:5432/codepromptu
      - SPRING_DATASOURCE_USERNAME=codepromptu_user
      - SPRING_DATASOURCE_PASSWORD=codepromptu_pass
      - CONFIG_SERVER_URI=http://config:8888
    depends_on:
      - database
      - config
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8083/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  # Background Worker - Spring Batch
  worker:
    image: codepromptu/worker:latest
    build:
      context: ./services/worker
      dockerfile: Dockerfile
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - SPRING_DATASOURCE_URL=jdbc:postgresql://database:5432/codepromptu
      - SPRING_DATASOURCE_USERNAME=codepromptu_user
      - SPRING_DATASOURCE_PASSWORD=codepromptu_pass
      - SPRING_REDIS_HOST=cache
      - CONFIG_SERVER_URI=http://config:8888
    depends_on:
      - database
      - cache
      - config
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8084/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  # Configuration Server - Spring Cloud Config
  config:
    image: codepromptu/config:latest
    build:
      context: ./services/config
      dockerfile: Dockerfile
    ports:
      - "8888:8888"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
    volumes:
      - ./config-repo:/config-repo
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8888/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  # PostgreSQL with pgvector extension
  database:
    image: pgvector/pgvector:pg15
    ports:
      - "5432:5432"
    environment:
      - POSTGRES_DB=codepromptu
      - POSTGRES_USER=codepromptu_user
      - POSTGRES_PASSWORD=codepromptu_pass
      - POSTGRES_INITDB_ARGS=--encoding=UTF-8 --lc-collate=C --lc-ctype=C
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./database/init:/docker-entrypoint-initdb.d
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U codepromptu_user -d codepromptu"]
      interval: 10s
      timeout: 5s
      retries: 5

  # Redis Cache
  cache:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    command: redis-server --appendonly yes
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 3

  # Monitoring Stack
  monitoring:
    image: codepromptu/monitoring:latest
    build:
      context: ./services/monitoring
      dockerfile: Dockerfile
    ports:
      - "3001:3000"  # Grafana
      - "9090:9090"  # Prometheus
    volumes:
      - prometheus_data:/prometheus
      - grafana_data:/var/lib/grafana
    depends_on:
      - gateway
      - api
      - processor
      - evaluator
      - worker

volumes:
  postgres_data:
  redis_data:
  prometheus_data:
  grafana_data:

networks:
  default:
    name: codepromptu-network
```

## Implementation Tasks

### Phase 1: Core Infrastructure & Database Setup
1. **Database Schema Design**
   - Create PostgreSQL schema with pgvector extension
   - Design tables for prompts, templates, usages, and embeddings
   - Implement Flyway migrations for schema versioning
   - Set up vector similarity indexes and constraints

2. **Spring Boot Service Foundations**
   - Create Maven multi-module project structure
   - Set up Spring Boot parent POM with Spring AI dependencies
   - Configure Spring Data JPA with PostgreSQL and pgvector
   - Implement base entities and repositories

3. **Docker Infrastructure**
   - Create multi-stage Dockerfiles for each Spring Boot service
   - Set up Docker Compose with health checks and dependencies
   - Configure Spring Cloud Config Server
   - Implement database initialization scripts

4. **API Gateway Setup**
   - Implement Spring Cloud Gateway with routing rules
   - Configure LLM provider proxy endpoints
   - Set up request/response interception with Spring AOP
   - Implement client API key pass-through mechanism

5. **Basic Prompt Storage**
   - Create JPA entities for prompts and metadata
   - Implement REST endpoints for CRUD operations
   - Set up Spring AI EmbeddingClient integration
   - Configure pgvector for similarity searches

### Phase 2: Processing Pipeline & Spring AI Integration
6. **Prompt Embedding Service**
   - Integrate Spring AI EmbeddingClient (OpenAI, Ollama, etc.)
   - Implement prompt preprocessing and variable masking
   - Create similarity detection algorithms using pgvector
   - Set up embedding storage and retrieval

7. **Template Induction Engine**
   - Implement clustering algorithms using Spring AI
   - Create Longest Common Subsequence (LCS) analysis
   - Build template shell generation and variable extraction
   - Set up background processing with Spring Batch

8. **Background Job Processing**
   - Configure Spring Batch for clustering jobs
   - Implement template generation workflows
   - Set up scheduled tasks for maintenance operations
   - Create job monitoring and failure handling

9. **Conversation Tracking**
   - Implement conversation grouping logic
   - Create session management with Redis
   - Set up conversation ID generation and tracking
   - Build conversation history and context management

10. **Advanced Similarity Detection**
    - Implement threshold-based classification (SAME, FORK, NEW)
    - Create prompt lineage tracking and parent-child relationships
    - Build fork detection and management
    - Set up similarity scoring and ranking

### Phase 3: User Interface & API Development
11. **REST API Development**
    - Build comprehensive REST API with Spring Boot
    - Implement prompt search and discovery endpoints
    - Create forking and versioning API endpoints
    - Set up authentication with Spring Security

12. **React Frontend Development**
    - Create React application with TypeScript
    - Implement prompt browsing and search interface
    - Build prompt editor with syntax highlighting
    - Create responsive design with modern UI framework

13. **Prompt Management UI**
    - Implement prompt creation and editing forms
    - Build forking and versioning interface
    - Create prompt comparison and diff views
    - Set up bulk operations and management tools

14. **Analytics Dashboard**
    - Create evaluation metrics visualization
    - Implement usage analytics and reporting
    - Build performance monitoring dashboards
    - Set up real-time metrics with Spring Boot Actuator

15. **Administrative Interface**
    - Build user management and permissions
    - Create system configuration interface
    - Implement template management tools
    - Set up system health and monitoring views

### Phase 4: Integration & Tooling
16. **VS Code Extension**
    - Create TypeScript-based VS Code extension
    - Implement prompt discovery and insertion
    - Build integration with CodePromptu API
    - Set up authentication and user preferences

17. **Slack Bot Integration**
    - Develop Spring Boot Slack bot application
    - Implement slash commands for prompt operations
    - Create interactive prompt discovery interface
    - Set up notifications and alerts

18. **Webhook System**
    - Implement webhook framework with Spring Boot
    - Create event-driven architecture for integrations
    - Build webhook management and configuration
    - Set up external system notifications

19. **Monitoring & Observability**
    - Configure Prometheus metrics with Micrometer
    - Set up Grafana dashboards for visualization
    - Implement distributed tracing with Spring Cloud Sleuth
    - Create alerting rules and notifications

20. **CI/CD Pipeline**
    - Set up GitHub Actions or Jenkins pipeline
    - Implement automated testing and quality gates
    - Create Docker image building and publishing
    - Set up automated deployment workflows

### Phase 5: Production Readiness & Security
21. **Comprehensive Logging**
    - Implement structured logging with Logback
    - Set up centralized log aggregation
    - Create correlation IDs for request tracing
    - Build log analysis and monitoring

22. **Security Implementation**
    - Configure Spring Security with JWT authentication
    - Implement role-based access control (RBAC)
    - Set up API rate limiting and throttling
    - Create audit logging and compliance features

23. **Performance Optimization**
    - Implement Redis caching with Spring Cache
    - Optimize database queries and indexes
    - Set up connection pooling and resource management
    - Create performance monitoring and profiling

24. **Load Testing & Scalability**
    - Implement load testing with JMeter or Gatling
    - Create horizontal scaling strategies
    - Set up database connection optimization
    - Build performance benchmarking and monitoring

25. **Documentation & Operations**
    - Create comprehensive API documentation with OpenAPI
    - Build deployment and operations runbooks
    - Implement health checks and readiness probes
    - Set up disaster recovery and backup procedures

## Deployment Options

### Local Development
- Docker Compose for local development
- Hot reloading for development containers
- Local volume mounts for code changes

### Production Deployment
- Kubernetes manifests for orchestration
- Helm charts for configuration management
- CI/CD pipeline integration
- Multi-environment support (dev, staging, prod)

## Configuration Management

- Environment-specific configuration files
- Secret management for API keys and credentials
- Feature flags for gradual rollouts
- Health check endpoints for all services

## Monitoring and Observability

- Structured logging with correlation IDs
- Metrics collection for all services
- Distributed tracing for request flows
- Alerting for critical system failures

## Database Schema Design (PostgreSQL + pgvector)

### Core Tables

```sql
-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Prompts table with vector embeddings
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
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    version INTEGER DEFAULT 1,
    is_active BOOLEAN DEFAULT true
);

-- Template definitions for prompt clustering
CREATE TABLE prompt_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shell TEXT NOT NULL,
    fragments TEXT[],
    embedding VECTOR(1536),
    variable_count INTEGER DEFAULT 0,
    usage_count INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Every captured LLM API call
CREATE TABLE prompt_usages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prompt_id UUID REFERENCES prompts(id),
    template_id UUID REFERENCES prompt_templates(id),
    conversation_id UUID,
    raw_content TEXT NOT NULL,
    variables JSONB DEFAULT '{}',
    request_timestamp TIMESTAMPTZ DEFAULT NOW(),
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
    api_key_hash VARCHAR(255)
);

-- Cross-references between related prompts
CREATE TABLE prompt_crossrefs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_prompt_id UUID REFERENCES prompts(id),
    target_prompt_id UUID REFERENCES prompts(id),
    relationship_type VARCHAR(100), -- 'similar', 'variant', 'improvement', 'related'
    similarity_score FLOAT,
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Evaluation metrics and feedback
CREATE TABLE prompt_evaluations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prompt_id UUID REFERENCES prompts(id),
    usage_id UUID REFERENCES prompt_usages(id),
    evaluation_type VARCHAR(100), -- 'quantitative', 'qualitative', 'automated'
    score FLOAT,
    max_score FLOAT DEFAULT 1.0,
    feedback TEXT,
    evaluator VARCHAR(255),
    criteria JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- User management and authentication
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(255) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    full_name VARCHAR(255),
    team VARCHAR(255),
    role VARCHAR(100) DEFAULT 'user',
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    last_login TIMESTAMPTZ
);

-- Team and ownership management
CREATE TABLE teams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) UNIQUE NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Conversation grouping for session tracking
CREATE TABLE conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id VARCHAR(255),
    user_id UUID REFERENCES users(id),
    started_at TIMESTAMPTZ DEFAULT NOW(),
    ended_at TIMESTAMPTZ,
    context JSONB DEFAULT '{}'
);
```

### Indexes for Performance

```sql
-- Vector similarity search indexes
CREATE INDEX idx_prompts_embedding ON prompts USING ivfflat (embedding vector_cosine_ops);
CREATE INDEX idx_templates_embedding ON prompt_templates USING ivfflat (embedding vector_cosine_ops);

-- Standard indexes for queries
CREATE INDEX idx_prompts_parent_id ON prompts(parent_id);
CREATE INDEX idx_prompts_team_owner ON prompts(team_owner);
CREATE INDEX idx_prompts_created_at ON prompts(created_at);
CREATE INDEX idx_prompts_tags ON prompts USING GIN(tags);
CREATE INDEX idx_prompts_metadata ON prompts USING GIN(metadata);

CREATE INDEX idx_usages_prompt_id ON prompt_usages(prompt_id);
CREATE INDEX idx_usages_template_id ON prompt_usages(template_id);
CREATE INDEX idx_usages_conversation_id ON prompt_usages(conversation_id);
CREATE INDEX idx_usages_request_timestamp ON prompt_usages(request_timestamp);
CREATE INDEX idx_usages_model_used ON prompt_usages(model_used);

CREATE INDEX idx_evaluations_prompt_id ON prompt_evaluations(prompt_id);
CREATE INDEX idx_evaluations_score ON prompt_evaluations(score);
CREATE INDEX idx_evaluations_created_at ON prompt_evaluations(created_at);

CREATE INDEX idx_crossrefs_source_prompt ON prompt_crossrefs(source_prompt_id);
CREATE INDEX idx_crossrefs_target_prompt ON prompt_crossrefs(target_prompt_id);
CREATE INDEX idx_crossrefs_similarity ON prompt_crossrefs(similarity_score);
```

### Spring Boot Configuration

```yaml
# application-docker.yml
spring:
  datasource:
    url: jdbc:postgresql://database:5432/codepromptu
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
    driver-class-name: org.postgresql.Driver
  
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        jdbc:
          lob:
            non_contextual_creation: true
    show-sql: false
  
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
  
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      embedding:
        options:
          model: text-embedding-ada-002
    
  redis:
    host: ${SPRING_REDIS_HOST}
    port: ${SPRING_REDIS_PORT}
    timeout: 2000ms
    
  cache:
    type: redis
    redis:
      time-to-live: 600000 # 10 minutes
```

## Security Considerations

- Container image scanning
- Network policies and service mesh
- API authentication and rate limiting
- Data encryption at rest and in transit
- Secure API key storage and rotation
- RBAC implementation with Spring Security
- Audit logging for compliance
- Input validation and sanitization
