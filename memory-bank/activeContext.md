# CodePromptu Active Context

## Current Development Focus

### Phase 1: Foundation and Core Infrastructure
**Status**: ✅ COMPLETED - Foundation build successful

**Completed Sprint Goals**:
1. ✅ Create project directory structure (src, tests, memory-bank)
2. ✅ Establish memory bank with project documentation
3. ✅ Build Maven multi-module project structure
4. ✅ Implement core domain models and entities
5. ✅ Set up database schema with pgvector support
6. ✅ Create basic Spring Boot service foundations

**Phase 1 Deliverables Completed**:
1. ✅ Maven parent POM with Spring Boot and Spring AI dependencies
2. ✅ Service module structure (gateway, processor, api, worker, config)
3. ✅ Complete JPA entities for prompts, templates, and usages
4. ✅ Database migration scripts with Flyway
5. ✅ Docker Compose for local development
6. ✅ Comprehensive unit tests for domain models
7. ✅ Complete README and documentation

### Phase 2: Service Implementation
**Status**: 🔄 READY TO START - Next development phase

**Current Sprint Goals**:
1. ⏳ Implement individual microservice applications
2. ⏳ Spring AI integration for embedding generation
3. ⏳ REST API endpoints for prompt management
4. ⏳ Basic React UI for prompt browsing
5. ⏳ Integration testing with TestContainers
6. ⏳ Vector similarity search implementation

**Immediate Next Steps**:
1. Create Spring Boot application classes for each service
2. Implement Spring AI EmbeddingClient integration
3. Build REST controllers with OpenAPI documentation
4. Set up basic React frontend with TypeScript
5. Create integration test suite with real database
6. Implement vector similarity algorithms

## Open Questions & Decisions Needed

### Technical Decisions
1. **Embedding Model Strategy**: 
   - Primary: OpenAI text-embedding-ada-002 (1536 dimensions)
   - Question: Should we support multiple embedding models simultaneously?
   - Decision: Start with single model, plan for migration strategy

2. **Similarity Thresholds**:
   - Same prompt: ≥ 0.95
   - Fork/variant: 0.70-0.95  
   - New prompt: < 0.70
   - Question: Should these be configurable per team/organization?
   - Decision: Make configurable via Spring Cloud Config

3. **Vector Index Strategy**:
   - Using pgvector with IVFFlat indexes
   - Question: Performance vs accuracy trade-offs for large datasets
   - Decision: Start with IVFFlat, monitor performance, consider HNSW for scale

### Architecture Decisions
1. **Service Communication**:
   - Synchronous: REST APIs between services
   - Asynchronous: Spring Events for background processing
   - Question: Should we use message queues (RabbitMQ/Kafka) for better scalability?
   - Decision: Start with Spring Events, migrate to queues if needed

2. **Caching Strategy**:
   - Redis for session management and frequently accessed data
   - Question: Cache invalidation strategy for prompt updates
   - Decision: Event-driven cache invalidation with TTL fallback

3. **Authentication & Authorization**:
   - JWT tokens with Spring Security
   - Question: Integration with existing enterprise SSO systems
   - Decision: Support OIDC/SAML integration for enterprise deployments

## Current Blockers & Risks

### Technical Risks
1. **Vector Search Performance**: 
   - Risk: Similarity search may be slow with large prompt datasets
   - Mitigation: Implement proper indexing and consider approximate search algorithms

2. **Embedding Consistency**:
   - Risk: Different embedding models may produce incompatible vectors
   - Mitigation: Version embeddings and provide migration tools

3. **Real-time Processing**:
   - Risk: Prompt capture and processing may add latency to LLM calls
   - Mitigation: Asynchronous processing with minimal request overhead

### Business Risks
1. **Adoption Barriers**:
   - Risk: Teams may resist changing existing prompt workflows
   - Mitigation: Zero-touch capture reduces friction, provide clear value demonstration

2. **Data Privacy**:
   - Risk: Storing prompts may contain sensitive information
   - Mitigation: Implement data classification and retention policies

## Integration Priorities

### High Priority Integrations
1. **VS Code Extension**: Primary developer workflow integration
2. **OpenAI API Proxy**: Core functionality for prompt capture
3. **PostgreSQL + pgvector**: Essential for vector similarity search

### Medium Priority Integrations
1. **Slack Bot**: Team collaboration and prompt sharing
2. **Anthropic API**: Support for Claude models
3. **Prometheus/Grafana**: Monitoring and observability

### Future Integrations
1. **Kubernetes Deployment**: Production orchestration
2. **Enterprise SSO**: Authentication integration
3. **Webhook System**: External system notifications

## Development Environment Setup

### Prerequisites Installed
- Java 17+ (OpenJDK)
- Maven 3.8+
- Node.js 18+
- Docker and Docker Compose
- PostgreSQL client tools

### Local Development Stack
```bash
# Start infrastructure services
docker-compose up -d database cache

# Run backend services
mvn spring-boot:run -pl gateway
mvn spring-boot:run -pl processor  
mvn spring-boot:run -pl api
mvn spring-boot:run -pl worker

# Run frontend
cd ui && npm start
```

### Testing Strategy
- **Unit Tests**: JUnit 5 with Mockito for service layer
- **Integration Tests**: TestContainers for database integration
- **API Tests**: RestAssured for REST endpoint testing
- **Frontend Tests**: Jest and React Testing Library
- **E2E Tests**: Playwright for full workflow testing

## Performance Targets

### Response Time Goals
- Prompt search: < 200ms (95th percentile)
- Prompt capture: < 50ms overhead
- Similarity detection: < 100ms
- Template generation: < 5 seconds (background)

### Throughput Goals
- API Gateway: 1000 requests/second
- Prompt storage: 500 prompts/second
- Vector search: 100 queries/second
- Concurrent users: 1000+

### Scalability Targets
- Prompts: 1M+ stored prompts
- Users: 10K+ registered users
- Teams: 100+ organizations
- Vector dimensions: 1536 (OpenAI ada-002)

## Monitoring & Observability

### Key Metrics to Track
1. **Business Metrics**:
   - Prompt reuse rate
   - Time saved in development
   - User adoption rate
   - Quality improvement over time

2. **Technical Metrics**:
   - API response times
   - Vector search performance
   - Database query performance
   - Cache hit rates

3. **Operational Metrics**:
   - Service uptime
   - Error rates
   - Resource utilization
   - Background job success rates

### Alerting Thresholds
- API response time > 500ms
- Error rate > 1%
- Database connection pool > 80%
- Vector search timeout > 5 seconds
- Background job failure rate > 5%

## Security Considerations

### Data Protection
- Encrypt sensitive data at rest
- Use TLS for all API communications
- Implement proper input validation
- Regular security scanning of dependencies

### Access Control
- Role-based permissions (Admin, User, Viewer)
- Team-based data isolation
- API rate limiting
- Audit logging for compliance

### Compliance Requirements
- GDPR: Right to deletion and data portability
- SOC2: Security controls and monitoring
- Enterprise: Integration with existing security systems
