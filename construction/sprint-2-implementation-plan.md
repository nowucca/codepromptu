# Sprint 2: Core API Implementation Plan

## Sprint Overview

**Duration**: 2 weeks (July 28 - August 11, 2025)
**Goal**: Implement basic prompt CRUD and vector search functionality
**Team**: Backend Developer, DevOps Engineer, Product Owner

## Sprint Objectives

### Primary Deliverables
1. **Complete API Service REST Controllers** - Full CRUD operations for prompt management
2. **Spring AI EmbeddingClient Integration** - Automatic vector generation on prompt storage
3. **Vector Similarity Search** - pgvector-based similarity algorithms with configurable thresholds
4. **Integration Test Suite** - TestContainers-based testing with real database
5. **OpenAPI Documentation** - Comprehensive API documentation with Swagger UI

### Success Criteria
- ✅ Can store and retrieve prompts via REST API
- ✅ Vector embeddings generated automatically on prompt storage
- ✅ Similarity search returns relevant results with proper scoring
- ✅ All integration tests pass with real database
- ✅ API documentation available via Swagger UI

## Technical Implementation Plan

### 1. API Service Enhancement

#### 1.1 REST Controller Implementation
**File**: `src/api/src/main/java/com/codepromptu/api/controller/PromptController.java`

**Endpoints to Implement**:
```java
@RestController
@RequestMapping("/api/v1/prompts")
@Validated
public class PromptController {
    
    // CRUD Operations
    @GetMapping
    public ResponseEntity<Page<PromptDto>> getAllPrompts(Pageable pageable);
    
    @GetMapping("/{id}")
    public ResponseEntity<PromptDto> getPrompt(@PathVariable UUID id);
    
    @PostMapping
    public ResponseEntity<PromptDto> createPrompt(@Valid @RequestBody CreatePromptRequest request);
    
    @PutMapping("/{id}")
    public ResponseEntity<PromptDto> updatePrompt(@PathVariable UUID id, @Valid @RequestBody UpdatePromptRequest request);
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePrompt(@PathVariable UUID id);
    
    // Forking and Versioning
    @PostMapping("/{id}/fork")
    public ResponseEntity<PromptDto> forkPrompt(@PathVariable UUID id, @RequestBody ForkPromptRequest request);
    
    // Similarity Search
    @GetMapping("/{id}/similar")
    public ResponseEntity<List<SimilarPromptDto>> findSimilarPrompts(@PathVariable UUID id, @RequestParam(defaultValue = "10") int limit);
    
    @PostMapping("/search/similar")
    public ResponseEntity<List<SimilarPromptDto>> searchSimilarPrompts(@RequestBody SimilaritySearchRequest request);
}
```

#### 1.2 Service Layer Implementation
**File**: `src/api/src/main/java/com/codepromptu/api/service/PromptService.java`

**Key Features**:
- Spring AI EmbeddingClient integration for automatic vector generation
- Prompt classification logic (SAME/FORK/NEW) based on similarity thresholds
- Vector similarity search using pgvector
- Comprehensive error handling and validation

```java
@Service
@Transactional
public class PromptService {
    
    private final PromptRepository promptRepository;
    private final EmbeddingClient embeddingClient;
    private final SimilarityService similarityService;
    
    // Core CRUD operations with automatic embedding generation
    public PromptDto createPrompt(CreatePromptRequest request);
    public PromptDto updatePrompt(UUID id, UpdatePromptRequest request);
    public Optional<PromptDto> getPrompt(UUID id);
    public Page<PromptDto> getAllPrompts(Pageable pageable);
    public void deletePrompt(UUID id);
    
    // Advanced operations
    public PromptDto forkPrompt(UUID parentId, ForkPromptRequest request);
    public List<SimilarPromptDto> findSimilarPrompts(UUID promptId, int limit);
    public List<SimilarPromptDto> searchSimilarPrompts(String content, int limit);
    public PromptClassification classifyPrompt(String content);
}
```

#### 1.3 Vector Similarity Implementation
**File**: `src/api/src/main/java/com/codepromptu/api/service/SimilarityService.java`

**Features**:
- Configurable similarity thresholds (Same ≥0.95, Fork 0.70-0.95, New <0.70)
- pgvector cosine similarity search
- Prompt classification based on similarity scores
- Performance optimization with proper indexing

```java
@Service
public class SimilarityService {
    
    private final PromptRepository promptRepository;
    
    @Value("${codepromptu.similarity.threshold.same:0.95}")
    private double sameThreshold;
    
    @Value("${codepromptu.similarity.threshold.fork:0.70}")
    private double forkThreshold;
    
    public List<SimilarPromptDto> findSimilarPrompts(List<Double> embedding, int limit);
    public PromptClassification classifyPrompt(List<Double> embedding);
    public double calculateSimilarity(List<Double> embedding1, List<Double> embedding2);
}
```

### 2. Spring AI Integration

#### 2.1 EmbeddingClient Configuration
**File**: `src/api/src/main/java/com/codepromptu/api/config/SpringAIConfig.java`

```java
@Configuration
@EnableConfigurationProperties(SpringAIProperties.class)
public class SpringAIConfig {
    
    @Bean
    @ConditionalOnProperty(name = "spring.ai.openai.api-key")
    public EmbeddingClient openAIEmbeddingClient(OpenAiApi openAiApi) {
        return new OpenAiEmbeddingClient(openAiApi);
    }
    
    @Bean
    @ConditionalOnMissingBean(EmbeddingClient.class)
    public EmbeddingClient mockEmbeddingClient() {
        return new MockEmbeddingClient(); // For testing
    }
}
```

#### 2.2 Embedding Generation Service
**File**: `src/api/src/main/java/com/codepromptu/api/service/EmbeddingService.java`

```java
@Service
public class EmbeddingService {
    
    private final EmbeddingClient embeddingClient;
    
    public List<Double> generateEmbedding(String content) {
        EmbeddingRequest request = new EmbeddingRequest(List.of(content), EmbeddingOptions.EMPTY);
        EmbeddingResponse response = embeddingClient.call(request);
        return response.getResults().get(0).getOutput();
    }
    
    public CompletableFuture<List<Double>> generateEmbeddingAsync(String content) {
        return CompletableFuture.supplyAsync(() -> generateEmbedding(content));
    }
}
```

### 3. Database Integration

#### 3.1 Repository Enhancement
**File**: `src/api/src/main/java/com/codepromptu/api/repository/PromptRepository.java`

```java
@Repository
public interface PromptRepository extends JpaRepository<Prompt, UUID> {
    
    // Vector similarity search using pgvector
    @Query(value = """
        SELECT p.*, (1 - (p.embedding <=> CAST(:embedding AS vector))) as similarity_score
        FROM prompts p 
        WHERE p.is_active = true
        ORDER BY p.embedding <=> CAST(:embedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<PromptSimilarityProjection> findSimilarPrompts(@Param("embedding") String embedding, @Param("limit") int limit);
    
    // Find prompts by similarity threshold
    @Query(value = """
        SELECT p.*, (1 - (p.embedding <=> CAST(:embedding AS vector))) as similarity_score
        FROM prompts p 
        WHERE p.is_active = true 
        AND (1 - (p.embedding <=> CAST(:embedding AS vector))) >= :threshold
        ORDER BY p.embedding <=> CAST(:embedding AS vector)
        """, nativeQuery = true)
    List<PromptSimilarityProjection> findPromptsByThreshold(@Param("embedding") String embedding, @Param("threshold") double threshold);
    
    // Standard queries
    Page<Prompt> findByIsActiveTrueOrderByCreatedAtDesc(Pageable pageable);
    List<Prompt> findByParentIdAndIsActiveTrue(UUID parentId);
    List<Prompt> findByTeamOwnerAndIsActiveTrue(String teamOwner);
}
```

#### 3.2 Entity Enhancement
**File**: `src/shared/src/main/java/com/codepromptu/shared/domain/Prompt.java`

```java
@Entity
@Table(name = "prompts")
public class Prompt {
    
    // Existing fields...
    
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private String embedding; // Stored as string representation of vector
    
    // Helper methods for embedding conversion
    public void setEmbeddingFromList(List<Double> embeddingList) {
        this.embedding = embeddingList.stream()
            .map(String::valueOf)
            .collect(Collectors.joining(",", "[", "]"));
    }
    
    public List<Double> getEmbeddingAsList() {
        if (embedding == null) return null;
        String cleanEmbedding = embedding.substring(1, embedding.length() - 1);
        return Arrays.stream(cleanEmbedding.split(","))
            .map(String::trim)
            .map(Double::parseDouble)
            .collect(Collectors.toList());
    }
}
```

### 4. Integration Testing

#### 4.1 TestContainers Setup
**File**: `src/api/src/test/java/com/codepromptu/api/integration/BaseIntegrationTest.java`

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg15")
            .withDatabaseName("codepromptu_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("test-schema.sql");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", redis::getFirstMappedPort);
    }
}
```

#### 4.2 API Integration Tests
**File**: `src/api/src/test/java/com/codepromptu/api/integration/PromptControllerIntegrationTest.java`

```java
@ExtendWith(SpringExtension.class)
public class PromptControllerIntegrationTest extends BaseIntegrationTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private PromptRepository promptRepository;
    
    @Test
    void shouldCreatePromptWithEmbedding() {
        // Test prompt creation with automatic embedding generation
    }
    
    @Test
    void shouldFindSimilarPrompts() {
        // Test vector similarity search
    }
    
    @Test
    void shouldClassifyPromptCorrectly() {
        // Test prompt classification (SAME/FORK/NEW)
    }
    
    @Test
    void shouldHandlePromptForking() {
        // Test prompt forking functionality
    }
}
```

### 5. OpenAPI Documentation

#### 5.1 OpenAPI Configuration
**File**: `src/api/src/main/java/com/codepromptu/api/config/OpenAPIConfig.java`

```java
@Configuration
public class OpenAPIConfig {
    
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("CodePromptu API")
                .version("1.0.0")
                .description("API for prompt management and similarity search")
                .contact(new Contact()
                    .name("CodePromptu Team")
                    .email("support@codepromptu.com")))
            .servers(List.of(
                new Server().url("http://localhost:8081").description("Local development"),
                new Server().url("https://api.codepromptu.com").description("Production")))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")));
    }
}
```

#### 5.2 API Documentation Annotations
```java
@RestController
@RequestMapping("/api/v1/prompts")
@Tag(name = "Prompts", description = "Prompt management operations")
public class PromptController {
    
    @Operation(summary = "Create a new prompt", description = "Creates a new prompt with automatic embedding generation")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Prompt created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping
    public ResponseEntity<PromptDto> createPrompt(
        @Parameter(description = "Prompt creation request") @Valid @RequestBody CreatePromptRequest request) {
        // Implementation
    }
}
```

## Configuration Updates

### 6.1 Application Configuration
**File**: `src/config-repo/api.yml`

```yaml
# Spring AI Configuration
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      embedding:
        options:
          model: text-embedding-ada-002

# Similarity thresholds
codepromptu:
  similarity:
    threshold:
      same: 0.95
      fork: 0.70
  embedding:
    dimensions: 1536
    batch-size: 100

# OpenAPI Configuration
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    enabled: true
```

### 6.2 Maven Dependencies
**File**: `src/api/pom.xml`

```xml
<dependencies>
    <!-- Spring AI -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    </dependency>
    
    <!-- OpenAPI Documentation -->
    <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        <version>2.2.0</version>
    </dependency>
    
    <!-- TestContainers for Integration Testing -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>postgresql</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## Testing Strategy

### Unit Tests
- **Service Layer**: Mock dependencies, test business logic
- **Repository Layer**: @DataJpaTest with H2 database
- **Controller Layer**: @WebMvcTest with mocked services

### Integration Tests
- **API Endpoints**: Full request/response cycle with TestContainers
- **Database Operations**: Real PostgreSQL with pgvector
- **Spring AI Integration**: Mock EmbeddingClient for consistent testing

### Performance Tests
- **Vector Search**: Measure similarity search performance with various dataset sizes
- **Embedding Generation**: Test batch processing performance
- **API Response Times**: Validate response time targets

## Risk Mitigation

### Technical Risks
1. **Spring AI Integration Complexity**: Allocate spike time for research and prototyping
2. **Vector Search Performance**: Implement proper indexing and monitoring
3. **Embedding Generation Latency**: Consider async processing for large batches

### Quality Assurance
1. **Code Coverage**: Target >80% for service layer
2. **API Testing**: Comprehensive integration test suite
3. **Documentation**: Keep OpenAPI docs synchronized with implementation

## Definition of Done

### Code Quality
- [ ] All unit tests passing (>80% coverage)
- [ ] Integration tests passing with TestContainers
- [ ] Code review completed and approved
- [ ] Static analysis passing (SonarQube)
- [ ] No high/critical security vulnerabilities

### Functionality
- [ ] All REST endpoints implemented and tested
- [ ] Vector similarity search working correctly
- [ ] Prompt classification logic validated
- [ ] Error handling comprehensive and tested
- [ ] OpenAPI documentation complete and accurate

### Performance
- [ ] API response times within targets (<200ms for search)
- [ ] Vector search performance validated
- [ ] Memory usage within acceptable limits
- [ ] Database queries optimized

### Documentation
- [ ] OpenAPI/Swagger documentation complete
- [ ] README updated with new endpoints
- [ ] Integration test documentation
- [ ] Deployment guide updated

## Sprint Retrospective Planning

### Metrics to Track
- Story points completed vs planned
- Defect rate and resolution time
- Code review cycle time
- Integration test execution time
- API response time benchmarks

### Success Indicators
- All primary deliverables completed
- Integration tests passing consistently
- API documentation accessible and accurate
- Performance targets met
- Team velocity maintained or improved

This sprint sets the foundation for the core API functionality that will enable prompt management, similarity search, and the basis for future features like prompt capture and template induction.
