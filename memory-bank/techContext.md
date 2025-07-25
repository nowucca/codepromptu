# CodePromptu Technical Context

## Technology Stack

### Backend Services (Java Spring Boot)
- **Spring Boot 3.2+**: Core framework for microservices
- **Spring AI**: LLM integration and embedding management
- **Spring Cloud Gateway**: API gateway and routing
- **Spring Data JPA**: Database abstraction and ORM
- **Spring Security**: Authentication and authorization
- **Spring Batch**: Background job processing
- **Spring Cloud Config**: Centralized configuration management
- **Spring Boot Actuator**: Health checks and metrics

### Database & Storage
- **PostgreSQL 15+**: Primary database with ACID compliance
- **pgvector Extension**: Vector similarity search and storage
- **Redis 7**: Caching, session management, and rate limiting
- **Flyway**: Database migration and versioning

### Frontend & UI
- **React 18+**: Modern frontend framework with hooks
- **TypeScript**: Type-safe JavaScript development
- **Material-UI or Tailwind CSS**: Component library and styling
- **React Query**: Server state management and caching
- **React Router**: Client-side routing

### Infrastructure & Deployment
- **Docker**: Containerization for all services
- **Docker Compose**: Local development orchestration
- **Kubernetes**: Production container orchestration
- **Helm**: Kubernetes package management
- **Prometheus**: Metrics collection and monitoring
- **Grafana**: Visualization and dashboards

### Development Tools
- **Maven**: Java build tool and dependency management
- **Node.js/npm**: Frontend build tools
- **VS Code Extension API**: IDE integration
- **Slack SDK**: Bot integration
- **OpenAPI/Swagger**: API documentation

## API Integration Strategy

### LLM Provider Support
- **OpenAI**: GPT models and embeddings (ada-002)
- **Anthropic**: Claude models via API
- **Azure OpenAI**: Enterprise OpenAI integration
- **Local Models**: Ollama integration for on-premise deployment
- **Custom Providers**: Extensible provider framework

### Embedding Strategy
- **Primary**: OpenAI text-embedding-ada-002 (1536 dimensions)
- **Fallback**: Local embedding models via Ollama
- **Consistency**: Single embedding model per deployment
- **Migration**: Support for embedding model upgrades

### API Gateway Configuration
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: openai-proxy
          uri: https://api.openai.com
          predicates:
            - Path=/v1/chat/completions
            - Header=Authorization, Bearer sk-.*
          filters:
            - name: PromptCapture
            - name: StripPrefix
              args:
                parts: 0
```

## Database Schema Design

### Core Tables
```sql
-- Prompts with vector embeddings
CREATE TABLE prompts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id UUID REFERENCES prompts(id),
    content TEXT NOT NULL,
    embedding VECTOR(1536),
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Template shells for clustering
CREATE TABLE prompt_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shell TEXT NOT NULL,
    fragments TEXT[],
    embedding VECTOR(1536),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Usage tracking for every API call
CREATE TABLE prompt_usages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prompt_id UUID REFERENCES prompts(id),
    template_id UUID REFERENCES prompt_templates(id),
    conversation_id UUID,
    variables JSONB DEFAULT '{}',
    request_timestamp TIMESTAMPTZ DEFAULT NOW(),
    tokens_input INTEGER,
    tokens_output INTEGER,
    model_used VARCHAR(255),
    status VARCHAR(50)
);
```

### Vector Indexes
```sql
-- pgvector similarity search indexes
CREATE INDEX idx_prompts_embedding 
ON prompts USING ivfflat (embedding vector_cosine_ops);

CREATE INDEX idx_templates_embedding 
ON prompt_templates USING ivfflat (embedding vector_cosine_ops);
```

## Service Architecture

### Gateway Service (Port 8080)
- **Responsibilities**: API proxying, prompt capture, authentication
- **Dependencies**: Database, Redis, Config Server
- **Key Components**:
  - `ProxyController`: Route LLM API calls
  - `PromptCaptureFilter`: Intercept and store prompts
  - `AuthenticationFilter`: Validate client credentials

### Processor Service (Port 8082)
- **Responsibilities**: Embedding generation, similarity detection, clustering
- **Dependencies**: Database, Redis, Spring AI
- **Key Components**:
  - `EmbeddingService`: Generate and store embeddings
  - `SimilarityService`: Detect prompt variants and duplicates
  - `ClusteringService`: Background template generation

### API Service (Port 8081)
- **Responsibilities**: REST API, search, CRUD operations
- **Dependencies**: Database, Redis
- **Key Components**:
  - `PromptController`: Prompt management endpoints
  - `SearchController`: Vector similarity search
  - `EvaluationController`: Metrics and feedback

### Worker Service (Background)
- **Responsibilities**: Batch processing, maintenance tasks
- **Dependencies**: Database, Spring Batch
- **Key Components**:
  - `ClusteringJob`: Periodic template generation
  - `MaintenanceJob`: Data cleanup and optimization
  - `EvaluationJob`: Performance metric calculation

## Integration Points

### VS Code Extension
```typescript
// Extension API integration
export class CodePromptuProvider {
    private apiClient: CodePromptuClient;
    
    async searchPrompts(query: string): Promise<Prompt[]> {
        return this.apiClient.search({
            query,
            limit: 10,
            includeMetadata: true
        });
    }
    
    async insertPrompt(promptId: string): Promise<void> {
        const prompt = await this.apiClient.getPrompt(promptId);
        const editor = vscode.window.activeTextEditor;
        editor?.edit(editBuilder => {
            editBuilder.insert(editor.selection.active, prompt.content);
        });
    }
}
```

### Slack Bot Integration
```java
@Component
public class CodePromptuSlackBot {
    
    @SlashCommandMapping("/prompt-search")
    public SlackResponse searchPrompts(@RequestParam String text) {
        List<Prompt> results = promptService.search(text);
        return SlackResponse.builder()
            .responseType("ephemeral")
            .blocks(formatSearchResults(results))
            .build();
    }
}
```

## Configuration Management

### Application Properties
```yaml
# application-docker.yml
spring:
  datasource:
    url: jdbc:postgresql://database:5432/codepromptu
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      embedding:
        options:
          model: text-embedding-ada-002
  
  redis:
    host: cache
    port: 6379
    timeout: 2000ms

codepromptu:
  similarity:
    same-threshold: 0.95
    fork-threshold: 0.70
    cluster-threshold: 0.80
  
  processing:
    batch-size: 100
    clustering-schedule: "0 0 2 * * ?" # Daily at 2 AM
```

### Environment Variables
- `OPENAI_API_KEY`: OpenAI API key for embeddings
- `DB_USERNAME/DB_PASSWORD`: Database credentials
- `REDIS_PASSWORD`: Redis authentication
- `JWT_SECRET`: JWT signing key
- `SLACK_BOT_TOKEN`: Slack integration token

## Security Configuration

### Spring Security Setup
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/v1/prompts/**").authenticated()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt())
            .build();
    }
}
```

### API Key Management
```java
@Service
public class ApiKeyService {
    
    public String hashApiKey(String apiKey) {
        return BCrypt.hashpw(apiKey, BCrypt.gensalt());
    }
    
    public boolean validateApiKey(String apiKey, String hash) {
        return BCrypt.checkpw(apiKey, hash);
    }
}
```

## Performance Considerations

### Caching Strategy
- **Prompt Cache**: Frequently accessed prompts (TTL: 1 hour)
- **Search Cache**: Search results (TTL: 15 minutes)
- **Embedding Cache**: Generated embeddings (TTL: 24 hours)
- **Session Cache**: User sessions and conversation context

### Database Optimization
- **Connection Pooling**: HikariCP with 20 max connections
- **Query Optimization**: Proper indexing for vector and text search
- **Partitioning**: Time-based partitioning for usage data
- **Read Replicas**: Separate read workloads for analytics

### Monitoring and Metrics
```java
@Component
public class PromptMetrics {
    
    private final Counter promptCaptureCounter;
    private final Timer embeddingGenerationTimer;
    private final Gauge similarityScoreGauge;
    
    @EventListener
    public void onPromptCaptured(PromptCapturedEvent event) {
        promptCaptureCounter.increment(
            Tags.of("model", event.getModel(), "provider", event.getProvider())
        );
    }
}
```

## Development Workflow

### Local Development Setup
1. **Prerequisites**: Java 17+, Node.js 18+, Docker, Docker Compose
2. **Database**: `docker-compose up database cache`
3. **Backend**: `mvn spring-boot:run` for each service
4. **Frontend**: `npm start` in UI directory
5. **Testing**: `mvn test` and `npm test`

### CI/CD Pipeline
1. **Build**: Maven compile and test
2. **Quality**: SonarQube analysis and security scanning
3. **Package**: Docker image building
4. **Deploy**: Kubernetes deployment via Helm
5. **Monitor**: Health checks and smoke tests

## Implementation Status

### ✅ Completed Components
- **Maven Multi-Module Structure**: Parent POM with all service modules defined
- **Domain Model**: Complete JPA entities with business logic and relationships
- **Database Schema**: PostgreSQL with pgvector extension and optimized indexes
- **Development Infrastructure**: Docker Compose with all required services
- **Testing Framework**: Unit tests with comprehensive domain model coverage
- **Documentation**: Complete README and setup guides

### 🔄 In Progress Components
- **Service Applications**: Spring Boot applications for each microservice
- **Spring AI Integration**: EmbeddingClient and vector processing
- **REST API**: Controllers and OpenAPI documentation
- **Frontend**: React TypeScript application
- **Integration Tests**: TestContainers-based testing

### ⏳ Planned Components
- **Production Deployment**: Kubernetes manifests and Helm charts
- **Monitoring Stack**: Prometheus/Grafana integration
- **Security Hardening**: Enterprise authentication and RBAC
- **Performance Optimization**: Caching and query optimization

## Known Technical Debt

### Current Limitations
- **Single Embedding Model**: No support for model migration (planned for Phase 2)
- **Synchronous Processing**: Some operations may block request threads (async planned)
- **Limited Scaling**: Vector search performance needs validation with large datasets
- **Manual Configuration**: Similarity thresholds require manual tuning (auto-tuning planned)

### Implementation Priorities
1. **Service Layer**: Complete Spring Boot applications with Spring AI integration
2. **API Layer**: REST endpoints with proper error handling and validation
3. **Integration Testing**: TestContainers setup for database and vector operations
4. **Performance Testing**: Load testing with realistic prompt datasets

### Future Improvements
- **Async Processing**: Move heavy operations to background queues with Spring Batch
- **Embedding Versioning**: Support for multiple embedding models and migration
- **Auto-tuning**: Machine learning for optimal similarity thresholds
- **Distributed Vector Search**: Sharding for large-scale deployments
- **Advanced Caching**: Multi-level caching with Redis and application-level cache
