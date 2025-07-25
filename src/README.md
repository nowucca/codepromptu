# CodePromptu

A system for storing, evaluating, evolving, and reusing prompts written for LLMs. CodePromptu treats prompts not as static snippets but as knowledge artifacts — shaped by context, improved through feedback, and connected across systems.

## 🏗️ Architecture Overview

CodePromptu follows a microservices architecture built with Spring Boot and Spring AI:

- **Gateway Service** (Port 8080): API gateway with zero-touch prompt capture
- **Processor Service** (Port 8082): Embedding generation and similarity detection
- **API Service** (Port 8081): REST API for prompt management
- **Worker Service** (Background): Batch processing and clustering
- **Config Service** (Port 8888): Centralized configuration management
- **UI Service** (Port 3000): React frontend for prompt management

## 🚀 Quick Start

### Prerequisites

- Java 17+
- Maven 3.8+
- Docker and Docker Compose
- Node.js 18+ (for UI development)
- OpenAI API Key (for embeddings)

### Local Development Setup

1. **Clone and navigate to the project:**
   ```bash
   cd codepromptu/src
   ```

2. **Set up environment variables:**
   ```bash
   export OPENAI_API_KEY=your_openai_api_key_here
   ```

3. **Start infrastructure services:**
   ```bash
   docker-compose up -d database cache
   ```

4. **Build the project:**
   ```bash
   mvn clean install
   ```

5. **Run services individually:**
   ```bash
   # Terminal 1 - Config Server
   mvn spring-boot:run -pl config

   # Terminal 2 - API Service
   mvn spring-boot:run -pl api

   # Terminal 3 - Gateway Service
   mvn spring-boot:run -pl gateway

   # Terminal 4 - Processor Service
   mvn spring-boot:run -pl processor

   # Terminal 5 - Worker Service
   mvn spring-boot:run -pl worker
   ```

6. **Run the UI (optional):**
   ```bash
   cd ui
   npm install
   npm start
   ```

### Docker Compose (Full Stack)

To run the entire stack with Docker:

```bash
# Set your OpenAI API key
export OPENAI_API_KEY=your_openai_api_key_here

# Start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop all services
docker-compose down
```

## 📊 Service Endpoints

| Service | Port | Health Check | Description |
|---------|------|--------------|-------------|
| Gateway | 8080 | `/actuator/health` | API Gateway & Prompt Capture |
| API | 8081 | `/actuator/health` | REST API for Prompt Management |
| Processor | 8082 | `/actuator/health` | Embedding & Similarity Processing |
| Worker | 8084 | `/actuator/health` | Background Jobs & Clustering |
| Config | 8888 | `/actuator/health` | Configuration Server |
| UI | 3000 | `/` | React Frontend |
| Database | 5432 | - | PostgreSQL with pgvector |
| Cache | 6379 | - | Redis |

## 🗄️ Database Schema

The system uses PostgreSQL with the pgvector extension for vector similarity search:

### Core Tables

- **`prompts`**: Core prompt storage with vector embeddings
- **`prompt_templates`**: Template shells from clustering
- **`prompt_usages`**: Complete log of every LLM API call
- **`prompt_crossrefs`**: Cross-references between related prompts
- **`prompt_evaluations`**: Evaluation metrics and feedback
- **`users`**: User accounts and authentication
- **`teams`**: Team/organization management
- **`conversations`**: Session tracking for API calls

### Key Features

- **Vector Similarity Search**: Uses pgvector with 1536-dimensional embeddings (OpenAI ada-002)
- **Automatic Indexing**: Optimized indexes for both vector and traditional queries
- **Database Functions**: Built-in functions for similarity search and template matching
- **Audit Trail**: Complete tracking of prompt evolution and usage

## 🔧 Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `OPENAI_API_KEY` | OpenAI API key for embeddings | Required |
| `SPRING_PROFILES_ACTIVE` | Spring profile (dev, docker, prod) | `dev` |
| `SPRING_DATASOURCE_URL` | Database connection URL | `jdbc:postgresql://localhost:5432/codepromptu` |
| `SPRING_REDIS_HOST` | Redis host | `localhost` |
| `CONFIG_SERVER_URI` | Config server URL | `http://localhost:8888` |

### Similarity Thresholds

Configure in `application.yml`:

```yaml
codepromptu:
  similarity:
    same-threshold: 0.95      # Same prompt (record usage)
    fork-threshold: 0.70      # Fork/variant (0.70-0.95)
    cluster-threshold: 0.80   # Clustering threshold
```

## 🧪 Testing

### Unit Tests

```bash
# Run all tests
mvn test

# Run tests for specific module
mvn test -pl shared
mvn test -pl api
```

### Integration Tests

```bash
# Run integration tests (requires Docker)
mvn verify
```

### Test Coverage

```bash
# Generate coverage report
mvn jacoco:report
```

## 📁 Project Structure

```
codepromptu/src/
├── pom.xml                 # Parent POM with dependencies
├── docker-compose.yml     # Local development stack
├── shared/                 # Shared domain models and utilities
│   ├── src/main/java/com/codepromptu/shared/
│   │   └── domain/         # JPA entities
│   └── src/main/resources/
│       └── db/migration/   # Flyway database migrations
├── gateway/                # API Gateway service
├── processor/              # Embedding and similarity processing
├── api/                    # REST API service
├── worker/                 # Background job processing
├── config/                 # Configuration server
├── ui/                     # React frontend
└── monitoring/             # Prometheus/Grafana (optional)
```

## 🔄 Development Workflow

### Adding New Features

1. **Update Domain Models**: Modify entities in `shared/` module
2. **Create Migration**: Add Flyway migration in `shared/src/main/resources/db/migration/`
3. **Implement Service Logic**: Add business logic to appropriate service
4. **Add Tests**: Create unit and integration tests
5. **Update API**: Add REST endpoints if needed
6. **Update UI**: Modify React components if needed

### Database Migrations

```bash
# Create new migration
# File: shared/src/main/resources/db/migration/V2__Description.sql

# Apply migrations
mvn flyway:migrate -pl shared

# Check migration status
mvn flyway:info -pl shared
```

## 🚀 Deployment

### Production Deployment

1. **Build Docker Images:**
   ```bash
   mvn clean package
   docker-compose build
   ```

2. **Deploy to Kubernetes:**
   ```bash
   # Use Helm charts (to be created)
   helm install codepromptu ./helm/codepromptu
   ```

3. **Environment Configuration:**
   - Set production database credentials
   - Configure Redis cluster
   - Set up monitoring and alerting
   - Configure SSL/TLS certificates

## 📊 Monitoring

### Health Checks

All services expose Spring Boot Actuator endpoints:

- `/actuator/health` - Service health status
- `/actuator/metrics` - Application metrics
- `/actuator/info` - Service information

### Metrics

Key metrics to monitor:

- **Business Metrics**: Prompt reuse rate, user adoption, quality improvements
- **Technical Metrics**: API response times, vector search performance, cache hit rates
- **Operational Metrics**: Service uptime, error rates, resource utilization

### Logging

Structured logging with correlation IDs for request tracing across services.

## 🔐 Security

### Authentication

- JWT-based authentication with Spring Security
- Support for OIDC/SAML integration
- Role-based access control (RBAC)

### Data Protection

- API key hashing (never store raw keys)
- Encryption at rest and in transit
- Input validation and sanitization
- Rate limiting and abuse prevention

## 🤝 Contributing

### Development Setup

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Run the test suite
6. Submit a pull request

### Code Standards

- Follow Java coding conventions
- Use Lombok for boilerplate reduction
- Write comprehensive tests
- Document public APIs
- Use meaningful commit messages

## 📚 Documentation

### API Documentation

- OpenAPI/Swagger UI available at: `http://localhost:8081/swagger-ui.html`
- API documentation auto-generated from code annotations

### Architecture Documentation

- See `../construction/` directory for detailed design documents
- Memory bank in `../memory-bank/` contains project knowledge

## 🐛 Troubleshooting

### Common Issues

1. **Database Connection Issues:**
   ```bash
   # Check if PostgreSQL is running
   docker-compose ps database
   
   # View database logs
   docker-compose logs database
   ```

2. **Vector Search Performance:**
   - Ensure pgvector indexes are created
   - Monitor query performance with `EXPLAIN ANALYZE`
   - Consider adjusting similarity thresholds

3. **Memory Issues:**
   - Increase JVM heap size: `-Xmx2g`
   - Monitor garbage collection
   - Check for memory leaks in long-running processes

### Getting Help

- Check the logs: `docker-compose logs [service-name]`
- Review health endpoints: `curl http://localhost:8081/actuator/health`
- Consult the memory bank documentation in `../memory-bank/`

## 📄 License

This project is part of the Constellize methodology demonstration.

## 🙏 Acknowledgments

Built using the Constellize method with comprehensive planning, memory bank documentation, and iterative development practices.
