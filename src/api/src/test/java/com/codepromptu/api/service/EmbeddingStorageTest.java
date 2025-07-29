package com.codepromptu.api.service;

import com.codepromptu.api.repository.JdbcPromptRepository;
import com.codepromptu.shared.domain.Prompt;
import com.pgvector.PGvector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Test for embedding storage functionality using TestContainers with PostgreSQL.
 * Tests the embedding conversion and storage logic with real database.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Embedding Storage Logic Test")
public class EmbeddingStorageTest {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingStorageTest.class);

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
    private JdbcPromptRepository promptRepository;

    @MockBean
    private EmbeddingService embeddingService;

    @MockBean
    private SimilarityService similarityService;

    @Autowired
    private PromptService promptService;

    @Test
    @DisplayName("Test embedding conversion and storage logic")
    void testEmbeddingConversionAndStorageLogic() {
        logger.info("=== TESTING EMBEDDING CONVERSION AND STORAGE LOGIC ===");
        
        // Create test embedding data
        List<Double> testEmbedding = Arrays.asList(
            0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0
        );
        
        // Extend to 1536 dimensions for realistic test
        while (testEmbedding.size() < 1536) {
            testEmbedding = new java.util.ArrayList<>(testEmbedding);
            testEmbedding.add(Math.random());
        }

        // Create test PGvector
        float[] floatArray = new float[testEmbedding.size()];
        for (int i = 0; i < testEmbedding.size(); i++) {
            floatArray[i] = testEmbedding.get(i).floatValue();
        }
        PGvector testPGvector = new PGvector(floatArray);

        // Mock the embedding service
        when(embeddingService.preprocessContent(anyString())).thenReturn("processed content");
        when(embeddingService.generateEmbedding(anyString())).thenReturn(testEmbedding);
        when(embeddingService.convertToPGVector(testEmbedding)).thenReturn(testPGvector);

        // Create test prompt
        Prompt inputPrompt = Prompt.builder()
                .content("Test prompt for embedding storage")
                .author("test.user")
                .purpose("Testing embedding storage")
                .teamOwner("test-team")
                .version(1)
                .isActive(true)
                .build();

        try {
            // Test the prompt creation with real database
            Prompt result = promptService.createPrompt(inputPrompt);
            
            // Verify the result
            assertNotNull(result, "Created prompt should not be null");
            assertNotNull(result.getId(), "Created prompt should have an ID");
            assertNotNull(result.getEmbedding(), "Created prompt should have an embedding");
            assertEquals(testPGvector.toArray().length, result.getEmbedding().toArray().length, 
                        "Embedding dimensions should match");
            
            logger.info("✅ Successfully created prompt with embedding");
            logger.info("Prompt ID: {}", result.getId());
            logger.info("Embedding dimensions: {}", result.getEmbedding().toArray().length);
            
        } catch (Exception e) {
            logger.error("❌ Failed to create prompt with embedding: {}", e.getMessage(), e);
            fail("Prompt creation with embedding failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Test embedding field mapping")
    void testEmbeddingFieldMapping() {
        logger.info("=== TESTING EMBEDDING FIELD MAPPING ===");
        
        // Create test embedding
        float[] testArray = {0.1f, 0.2f, 0.3f, 0.4f, 0.5f};
        PGvector testEmbedding = new PGvector(testArray);
        
        // Create prompt with embedding
        Prompt prompt = Prompt.builder()
                .content("Test content")
                .author("test.author")
                .embedding(testEmbedding)
                .build();
        
        // Verify embedding is properly set
        assertNotNull(prompt.getEmbedding(), "Embedding should not be null");
        assertEquals(5, prompt.getEmbedding().toArray().length, "Embedding should have 5 dimensions");
        assertEquals(0.1f, prompt.getEmbedding().toArray()[0], 0.001f, "First dimension should match");
        assertEquals(0.5f, prompt.getEmbedding().toArray()[4], 0.001f, "Last dimension should match");
        
        logger.info("✅ Embedding field mapping works correctly");
        logger.info("Embedding dimensions: {}", prompt.getEmbedding().toArray().length);
    }

    @Test
    @DisplayName("Test PGvector creation and conversion")
    void testPGvectorCreationAndConversion() {
        logger.info("=== TESTING PGVECTOR CREATION AND CONVERSION ===");
        
        try {
            // Test creating PGvector from float array
            float[] floatArray = {1.0f, 2.0f, 3.0f, 4.0f, 5.0f};
            PGvector pgVector = new PGvector(floatArray);
            
            assertNotNull(pgVector, "PGvector should not be null");
            assertEquals(5, pgVector.toArray().length, "PGvector should have 5 dimensions");
            assertEquals(1.0f, pgVector.toArray()[0], 0.001f, "First element should match");
            assertEquals(5.0f, pgVector.toArray()[4], 0.001f, "Last element should match");
            
            logger.info("✅ PGvector creation works correctly");
            logger.info("PGvector dimensions: {}", pgVector.toArray().length);
            logger.info("PGvector string representation: {}", pgVector.toString());
            
        } catch (Exception e) {
            logger.error("❌ PGvector creation failed: {}", e.getMessage(), e);
            fail("PGvector creation failed: " + e.getMessage());
        }
    }
}
