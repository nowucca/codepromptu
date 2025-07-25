# CodePromptu System Patterns

## Architecture Overview

CodePromptu follows a microservices architecture with the following key patterns:

### API Gateway Pattern
- **Gateway Service**: Acts as single entry point for all client requests
- **Proxy Functionality**: Transparently forwards LLM API calls to providers
- **Zero-Touch Capture**: Intercepts and stores prompts without client modifications
- **API Key Pass-through**: Maintains client's own provider API keys

### Event-Driven Architecture
- **Prompt Capture Events**: Triggered on every API call interception
- **Processing Pipeline**: Asynchronous processing of embeddings and clustering
- **Evaluation Events**: Triggered by usage patterns and feedback
- **Notification Events**: For system alerts and user notifications

### CQRS (Command Query Responsibility Segregation)
- **Write Side**: Prompt storage, forking, evaluation submission
- **Read Side**: Search, discovery, analytics, reporting
- **Separate Models**: Optimized data structures for reads vs writes
- **Event Sourcing**: Track all changes to prompts and metadata

## Data Patterns

### Vector Similarity Search
- **Embedding Strategy**: Use OpenAI ada-002 for consistent embeddings
- **Similarity Thresholds**:
  - `≥ 0.95`: Same prompt (record usage)
  - `0.70-0.95`: Variant (create fork)
  - `< 0.70`: New prompt
- **Index Strategy**: pgvector with IVFFlat for performance

### Template Induction
- **Clustering Algorithm**: Group prompts by similarity ≥ 0.8
- **Shell Generation**: Longest Common Subsequence (LCS) across clusters
- **Variable Extraction**: Pattern matching between shell and raw prompts
- **Background Processing**: Periodic clustering jobs via Spring Batch

### Versioning and Lineage
- **Parent-Child Relationships**: Track prompt evolution through forks
- **Immutable History**: Never delete, only mark as inactive
- **Cross-References**: Link related prompts across different lineages
- **Metadata Inheritance**: Propagate relevant metadata to forks

## Integration Patterns

### Plugin Architecture
- **VS Code Extension**: TypeScript-based with REST API integration
- **Slack Bot**: Spring Boot application with Slack SDK
- **CLI Tool**: Command-line interface for batch operations
- **Webhook System**: Event-driven integrations with external systems

### Security Patterns
- **JWT Authentication**: Stateless authentication with Spring Security
- **RBAC (Role-Based Access Control)**: Team-based permissions
- **API Key Management**: Secure storage and rotation
- **Audit Logging**: Comprehensive tracking of all operations

## Performance Patterns

### Caching Strategy
- **Redis Integration**: Cache frequently accessed prompts and search results
- **Cache Invalidation**: Event-driven cache updates
- **Session Management**: Store conversation context in Redis
- **Rate Limiting**: Prevent abuse and ensure fair usage

### Database Optimization
- **Connection Pooling**: HikariCP for efficient database connections
- **Read Replicas**: Separate read/write workloads
- **Indexing Strategy**: Optimized indexes for vector and text search
- **Partitioning**: Time-based partitioning for usage data

### Asynchronous Processing
- **Message Queues**: Spring Boot with embedded messaging
- **Background Jobs**: Spring Batch for heavy processing
- **Circuit Breakers**: Resilience patterns for external API calls
- **Retry Logic**: Exponential backoff for transient failures

## Monitoring and Observability

### Metrics Collection
- **Spring Boot Actuator**: Health checks and application metrics
- **Micrometer**: Metrics collection and export to Prometheus
- **Custom Metrics**: Prompt usage, similarity scores, performance
- **Business Metrics**: Adoption rates, quality improvements

### Distributed Tracing
- **Spring Cloud Sleuth**: Request correlation across services
- **Correlation IDs**: Track requests through the entire pipeline
- **Performance Monitoring**: Identify bottlenecks and optimization opportunities

### Logging Strategy
- **Structured Logging**: JSON format with consistent fields
- **Log Aggregation**: Centralized logging with correlation IDs
- **Security Logging**: Audit trail for compliance
- **Error Tracking**: Comprehensive error reporting and alerting

## Deployment Patterns

### Containerization
- **Docker Multi-stage Builds**: Optimized container images
- **Health Checks**: Container-level health monitoring
- **Resource Limits**: CPU and memory constraints
- **Security Scanning**: Automated vulnerability detection

### Orchestration
- **Docker Compose**: Local development and testing
- **Kubernetes**: Production orchestration and scaling
- **Helm Charts**: Configuration management and deployment
- **Service Mesh**: Advanced networking and security (future)

### Configuration Management
- **Spring Cloud Config**: Centralized configuration server
- **Environment-specific Configs**: Dev, staging, production
- **Secret Management**: Secure handling of sensitive data
- **Feature Flags**: Gradual rollout and A/B testing

## Known Trade-offs

### Performance vs Accuracy
- **Embedding Quality**: Higher quality embeddings increase processing time
- **Similarity Thresholds**: Stricter thresholds reduce false positives but may miss variants
- **Real-time vs Batch**: Real-time processing adds latency but improves user experience

### Storage vs Compute
- **Vector Storage**: Large storage requirements for embeddings
- **Clustering Frequency**: More frequent clustering improves accuracy but increases compute cost
- **Cache Size**: Larger caches improve performance but increase memory usage

### Consistency vs Availability
- **Eventual Consistency**: Background processing may delay template updates
- **Read Replicas**: May serve slightly stale data for better performance
- **Distributed Systems**: CAP theorem considerations for multi-region deployment
