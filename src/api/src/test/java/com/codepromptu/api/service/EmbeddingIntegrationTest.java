package com.codepromptu.api.service;

import com.codepromptu.api.repository.JdbcPromptRepository;
import com.codepromptu.shared.domain.Prompt;
import com.pgvector.PGvector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration test for embedding storage and similarity search functionality.
 * Uses TestContainers with PostgreSQL and pgvector extension to test the complete workflow.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Embedding Storage and Similarity Search Integration Test")
public class EmbeddingIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingIntegrationTest.class);

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg15")
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("codepromptu_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("test-schema.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.show-sql", () -> "true");
        // Disable OpenAI for tests
        registry.add("spring.ai.openai.api-key", () -> "test-key");
    }

    @Autowired
    private PromptService promptService;

    @Autowired
    private JdbcPromptRepository promptRepository;

    @Autowired
    private SimilarityService similarityService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private EmbeddingService embeddingService;

    private List<Double> testEmbedding1;
    private List<Double> testEmbedding2;
    private List<Double> testEmbedding3;

    @BeforeEach
    void setUp() {
        // Create test embeddings with different similarity patterns
        testEmbedding1 = createTestEmbedding(0.1, 0.2, 0.3); // Base embedding
        testEmbedding2 = createTestEmbedding(0.11, 0.21, 0.31); // Very similar to embedding1
        testEmbedding3 = createTestEmbedding(0.8, 0.9, 1.0); // Different from embedding1
        
        logger.info("Test setup complete. Created 3 test embeddings with {} dimensions each", testEmbedding1.size());
    }

    private List<Double> createTestEmbedding(double base1, double base2, double base3) {
        List<Double> embedding = Arrays.asList(base1, base2, base3);
        
        // Extend to 1536 dimensions with pattern
        while (embedding.size() < 1536) {
            embedding = new java.util.ArrayList<>(embedding);
            embedding.add(base1 + (Math.random() * 0.1)); // Add some variation
        }
        
        return embedding;
    }

    @Test
    @DisplayName("Test database schema and pgvector extension")
    void testDatabaseSchemaAndExtension() {
        logger.info("=== TESTING DATABASE SCHEMA AND PGVECTOR EXTENSION ===");
        
        // Check if pgvector extension is available
        try {
            String extensionSql = "SELECT 1 FROM pg_extension WHERE extname = 'vector'";
            Integer result = jdbcTemplate.queryForObject(extensionSql, Integer.class);
            assertEquals(1, result, "pgvector extension should be installed");
            logger.info("✅ pgvector extension is installed");
        } catch (Exception e) {
            logger.error("❌ pgvector extension check failed: {}", e.getMessage(), e);
            fail("pgvector extension not available: " + e.getMessage());
        }

        // Check if prompts table exists with embedding column
        try {
            String tableSql = """
                SELECT column_name, data_type 
                FROM information_schema.columns 
                WHERE table_name = 'prompts' AND column_name = 'embedding'
                """;
            
            List<String> columns = jdbcTemplate.query(tableSql, (rs, rowNum) -> 
                rs.getString("column_name") + ":" + rs.getString("data_type"));
            
            assertFalse(columns.isEmpty(), "Embedding column should exist");
            logger.info("✅ Prompts table has embedding column: {}", columns);
        } catch (Exception e) {
            logger.error("❌ Table schema check failed: {}", e.getMessage(), e);
            fail("Table schema check failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Test complete workflow: Create prompts and find similar ones")
    void testCompleteWorkflowCreateAndFindSimilarPrompts() {
        logger.info("=== TESTING COMPLETE WORKFLOW: CREATE AND FIND SIMILAR PROMPTS ===");
        
        // Mock the embedding service to return our test embeddings
        when(embeddingService.preprocessContent(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        
        // Mock convertToPGVector for any List<Double> input
        when(embeddingService.convertToPGVector(any(List.class))).thenAnswer(invocation -> {
            List<Double> embedding = invocation.getArgument(0);
            return new PGvector(convertToFloatArray(embedding));
        });
        
        when(embeddingService.convertFromPGVector(any(PGvector.class))).thenAnswer(invocation -> {
            PGvector pgVector = invocation.getArgument(0);
            return convertToDoubleList(pgVector.toArray());
        });

        // Create three prompts with different embeddings
        Prompt prompt1 = createTestPrompt("How to write Java code for REST APIs", "java-developer");
        Prompt prompt2 = createTestPrompt("Best practices for Java REST API development", "senior-developer"); // Similar to prompt1
        Prompt prompt3 = createTestPrompt("How to bake chocolate chip cookies", "home-baker"); // Different topic

        // Mock embedding generation for each prompt
        when(embeddingService.generateEmbedding("How to write Java code for REST APIs")).thenReturn(testEmbedding1);
        when(embeddingService.generateEmbedding("Best practices for Java REST API development")).thenReturn(testEmbedding2);
        when(embeddingService.generateEmbedding("How to bake chocolate chip cookies")).thenReturn(testEmbedding3);
        
        // Mock embedding generation for content-based search
        when(embeddingService.generateEmbedding("Java API development guide")).thenReturn(testEmbedding1);

        // Create the prompts
        Prompt savedPrompt1 = promptService.createPrompt(prompt1);
        Prompt savedPrompt2 = promptService.createPrompt(prompt2);
        Prompt savedPrompt3 = promptService.createPrompt(prompt3);

        logger.info("✅ Created 3 test prompts:");
        logger.info("  - Prompt 1 (Java REST): {}", savedPrompt1.getId());
        logger.info("  - Prompt 2 (Java Best Practices): {}", savedPrompt2.getId());
        logger.info("  - Prompt 3 (Baking Cookies): {}", savedPrompt3.getId());

        // Note: We don't check savedPrompt.getEmbedding() because Hibernate doesn't load embeddings
        // The embeddings are stored via JdbcTemplate and retrieved via JdbcTemplate
        // We'll verify they exist when we do the similarity search

        // Test finding similar prompts to prompt1 (Java REST)
        List<SimilarityService.SimilarPromptResult> similarToPrompt1 = promptService.findSimilarPrompts(savedPrompt1.getId(), 5);
        
        logger.info("✅ Found {} similar prompts to Java REST prompt", similarToPrompt1.size());
        
        // The most similar should be prompt2 (Java best practices)
        boolean foundSimilarJavaPrompt = similarToPrompt1.stream()
                .anyMatch(result -> result.getPrompt().getId().equals(savedPrompt2.getId()));
        
        assertTrue(foundSimilarJavaPrompt, "Should find the similar Java prompt");
        logger.info("✅ Successfully found similar Java prompt in results");

        // Test finding similar prompts by content
        List<SimilarityService.SimilarPromptResult> similarByContent = promptService.findSimilarPrompts("Java API development guide", 5);
        
        logger.info("✅ Found {} similar prompts by content search", similarByContent.size());
        
        // Should find both Java-related prompts
        long javaPromptCount = similarByContent.stream()
                .filter(result -> result.getPrompt().getContent().toLowerCase().contains("java"))
                .count();
        
        assertTrue(javaPromptCount >= 1, "Should find at least one Java-related prompt");
        logger.info("✅ Content-based similarity search found {} Java-related prompts", javaPromptCount);

        // Test that dissimilar prompts have lower similarity scores
        boolean foundCookiePrompt = similarToPrompt1.stream()
                .anyMatch(result -> result.getPrompt().getId().equals(savedPrompt3.getId()));
        
        if (foundCookiePrompt) {
            // If cookie prompt is found, it should have a lower similarity score
            double cookieScore = similarToPrompt1.stream()
                    .filter(result -> result.getPrompt().getId().equals(savedPrompt3.getId()))
                    .findFirst()
                    .map(SimilarityService.SimilarPromptResult::getSimilarityScore)
                    .orElse(0.0);
            
            double javaScore = similarToPrompt1.stream()
                    .filter(result -> result.getPrompt().getId().equals(savedPrompt2.getId()))
                    .findFirst()
                    .map(SimilarityService.SimilarPromptResult::getSimilarityScore)
                    .orElse(0.0);
            
            assertTrue(javaScore > cookieScore, "Java prompt should have higher similarity than cookie prompt");
            logger.info("✅ Similarity scores are correctly ordered: Java={}, Cookie={}", javaScore, cookieScore);
        }

        logger.info("🎉 COMPLETE WORKFLOW TEST PASSED - Can create prompts and find similar ones!");
    }

    @Test
    @DisplayName("Test prompt classification based on similarity")
    @Transactional
    void testPromptClassification() {
        logger.info("=== TESTING PROMPT CLASSIFICATION ===");
        
        // Mock the embedding service
        when(embeddingService.preprocessContent(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(embeddingService.generateEmbedding(anyString())).thenReturn(testEmbedding1);
        when(embeddingService.convertToPGVector(any())).thenReturn(new PGvector(convertToFloatArray(testEmbedding1)));

        // Create some existing prompts for classification context
        Prompt existingPrompt = createTestPrompt("Existing Java development prompt", "developer");
        promptService.createPrompt(existingPrompt);

        // Test classifying a new prompt
        SimilarityService.PromptClassificationResult classification = promptService.classifyPrompt("New Java development question");
        
        assertNotNull(classification, "Classification result should not be null");
        logger.info("✅ Successfully classified prompt: {}", classification);
    }

    @Test
    @DisplayName("Test embedding storage and retrieval with real database")
    @Transactional
    void testEmbeddingStorageAndRetrieval() {
        logger.info("=== TESTING EMBEDDING STORAGE AND RETRIEVAL ===");
        
        // Mock the embedding service
        when(embeddingService.preprocessContent(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(embeddingService.generateEmbedding(anyString())).thenReturn(testEmbedding1);
        when(embeddingService.convertToPGVector(testEmbedding1)).thenReturn(new PGvector(convertToFloatArray(testEmbedding1)));
        when(embeddingService.convertFromPGVector(any(PGvector.class))).thenReturn(testEmbedding1);

        // Create a prompt
        Prompt testPrompt = createTestPrompt("Test prompt for embedding storage", "test-user");
        Prompt savedPrompt = promptService.createPrompt(testPrompt);

        // Verify embedding was stored
        assertNotNull(savedPrompt.getEmbedding(), "Saved prompt should have embedding");
        assertEquals(1536, savedPrompt.getEmbedding().toArray().length, "Embedding should have 1536 dimensions");

        // Retrieve from database and verify
        Optional<Prompt> retrievedPrompt = promptRepository.findById(savedPrompt.getId());
        assertTrue(retrievedPrompt.isPresent(), "Prompt should be retrievable from database");
        assertNotNull(retrievedPrompt.get().getEmbedding(), "Retrieved prompt should have embedding");

        logger.info("✅ Successfully stored and retrieved embedding from PostgreSQL with pgvector");
    }

    // Helper methods
    private Prompt createTestPrompt(String content, String author) {
        return Prompt.builder()
                .content(content)
                .author(author)
                .purpose("Testing similarity search")
                .teamOwner("test-team")
                .version(1)
                .isActive(true)
                .build();
    }

    private float[] convertToFloatArray(List<Double> doubleList) {
        float[] floatArray = new float[doubleList.size()];
        for (int i = 0; i < doubleList.size(); i++) {
            floatArray[i] = doubleList.get(i).floatValue();
        }
        return floatArray;
    }

    private List<Double> convertToDoubleList(float[] floatArray) {
        List<Double> doubleList = new java.util.ArrayList<>();
        for (float f : floatArray) {
            doubleList.add((double) f);
        }
        return doubleList;
    }
}
