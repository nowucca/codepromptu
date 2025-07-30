package com.codepromptu.api.service;

import com.codepromptu.api.repository.JdbcPromptRepository;
import com.codepromptu.api.repository.PromptVectorRepository;
import com.codepromptu.shared.domain.Prompt;
import com.pgvector.PGvector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SimilarityService using mocks.
 * Tests the core similarity detection and prompt lineage functionality.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SimilarityService Unit Tests - Mocked Dependencies")
class SimilarityServiceTest {

    @Mock
    private JdbcPromptRepository promptRepository;

    @Mock
    private PromptVectorRepository promptVectorRepository;

    @Mock
    private EmbeddingService embeddingService;

    @InjectMocks
    private SimilarityService similarityService;

    private List<Double> testEmbedding;
    private PGvector testPGVector;
    private Prompt testPrompt;
    private PromptVectorRepository.SimilarPromptResult mockVectorResult;

    @BeforeEach
    void setUp() {
        // Set up test thresholds
        ReflectionTestUtils.setField(similarityService, "sameThreshold", 0.95);
        ReflectionTestUtils.setField(similarityService, "forkThreshold", 0.70);

        // Create test data
        testEmbedding = Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5);
        testPGVector = new PGvector(new float[]{0.1f, 0.2f, 0.3f, 0.4f, 0.5f});
        
        testPrompt = Prompt.builder()
                .id(UUID.randomUUID())
                .content("Test prompt content")
                .author("test-author")
                .version(1)
                .isActive(true)
                .build();

        mockVectorResult = new PromptVectorRepository.SimilarPromptResult(testPrompt, 0.85);
    }

    @Nested
    @DisplayName("Similarity Classification Tests")
    class SimilarityClassificationTests {

        @Test
        @DisplayName("Should classify as SAME when similarity >= 0.95")
        void shouldClassifyAsSameWhenHighSimilarity() {
            // When
            SimilarityService.PromptClassification result = similarityService.classifyBySimilarity(0.96);

            // Then
            assertEquals(SimilarityService.PromptClassification.SAME, result);
        }

        @Test
        @DisplayName("Should classify as FORK when 0.70 <= similarity < 0.95")
        void shouldClassifyAsForkWhenModerateSimilarity() {
            // When
            SimilarityService.PromptClassification result1 = similarityService.classifyBySimilarity(0.85);
            SimilarityService.PromptClassification result2 = similarityService.classifyBySimilarity(0.70);

            // Then
            assertEquals(SimilarityService.PromptClassification.FORK, result1);
            assertEquals(SimilarityService.PromptClassification.FORK, result2);
        }

        @Test
        @DisplayName("Should classify as NEW when similarity < 0.70")
        void shouldClassifyAsNewWhenLowSimilarity() {
            // When
            SimilarityService.PromptClassification result = similarityService.classifyBySimilarity(0.65);

            // Then
            assertEquals(SimilarityService.PromptClassification.NEW, result);
        }

        @Test
        @DisplayName("Should handle edge cases correctly")
        void shouldHandleEdgeCasesCorrectly() {
            // Test exact threshold values
            assertEquals(SimilarityService.PromptClassification.SAME, 
                        similarityService.classifyBySimilarity(0.95));
            assertEquals(SimilarityService.PromptClassification.FORK, 
                        similarityService.classifyBySimilarity(0.94));
            assertEquals(SimilarityService.PromptClassification.NEW, 
                        similarityService.classifyBySimilarity(0.69));
        }
    }

    @Nested
    @DisplayName("Find Similar Prompts Tests")
    class FindSimilarPromptsTests {

        @Test
        @DisplayName("Should find similar prompts by embedding vector")
        void shouldFindSimilarPromptsByEmbedding() {
            // Given
            when(embeddingService.convertToPGVector(testEmbedding)).thenReturn(testPGVector);
            when(promptVectorRepository.findSimilarPrompts(testPGVector, 5))
                    .thenReturn(Collections.singletonList(mockVectorResult));

            // When
            List<SimilarityService.SimilarPromptResult> results = 
                    similarityService.findSimilarPrompts(testEmbedding, 5);

            // Then
            assertNotNull(results);
            assertEquals(1, results.size());
            
            SimilarityService.SimilarPromptResult result = results.get(0);
            assertEquals(testPrompt, result.getPrompt());
            assertEquals(0.85, result.getSimilarityScore());
            assertEquals(SimilarityService.PromptClassification.FORK, result.getClassification());

            verify(embeddingService).convertToPGVector(testEmbedding);
            verify(promptVectorRepository).findSimilarPrompts(testPGVector, 5);
        }

        @Test
        @DisplayName("Should find similar prompts by content")
        void shouldFindSimilarPromptsByContent() {
            // Given
            String content = "Test content for similarity search";
            String processedContent = "processed test content";
            
            when(embeddingService.preprocessContent(content)).thenReturn(processedContent);
            when(embeddingService.generateEmbedding(processedContent)).thenReturn(testEmbedding);
            when(embeddingService.convertToPGVector(testEmbedding)).thenReturn(testPGVector);
            when(promptVectorRepository.findSimilarPrompts(testPGVector, 3))
                    .thenReturn(Collections.singletonList(mockVectorResult));

            // When
            List<SimilarityService.SimilarPromptResult> results = 
                    similarityService.findSimilarPrompts(content, 3);

            // Then
            assertNotNull(results);
            assertEquals(1, results.size());
            assertEquals(testPrompt, results.get(0).getPrompt());

            verify(embeddingService).preprocessContent(content);
            verify(embeddingService).generateEmbedding(processedContent);
            verify(embeddingService).convertToPGVector(testEmbedding);
            verify(promptVectorRepository).findSimilarPrompts(testPGVector, 3);
        }

        @Test
        @DisplayName("Should handle empty results gracefully")
        void shouldHandleEmptyResultsGracefully() {
            // Given
            when(embeddingService.convertToPGVector(testEmbedding)).thenReturn(testPGVector);
            when(promptVectorRepository.findSimilarPrompts(testPGVector, 5))
                    .thenReturn(Collections.emptyList());

            // When
            List<SimilarityService.SimilarPromptResult> results = 
                    similarityService.findSimilarPrompts(testEmbedding, 5);

            // Then
            assertNotNull(results);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("Should throw exception for null embedding")
        void shouldThrowExceptionForNullEmbedding() {
            // When & Then
            assertThrows(IllegalArgumentException.class, () -> 
                    similarityService.findSimilarPrompts((List<Double>) null, 5));
        }

        @Test
        @DisplayName("Should throw exception for empty embedding")
        void shouldThrowExceptionForEmptyEmbedding() {
            // When & Then
            assertThrows(IllegalArgumentException.class, () -> 
                    similarityService.findSimilarPrompts(Collections.emptyList(), 5));
        }

        @Test
        @DisplayName("Should throw exception for null content")
        void shouldThrowExceptionForNullContent() {
            // When & Then
            assertThrows(IllegalArgumentException.class, () -> 
                    similarityService.findSimilarPrompts((String) null, 5));
        }

        @Test
        @DisplayName("Should throw exception for empty content")
        void shouldThrowExceptionForEmptyContent() {
            // When & Then
            assertThrows(IllegalArgumentException.class, () -> 
                    similarityService.findSimilarPrompts("", 5));
        }
    }

    @Nested
    @DisplayName("Prompt Classification Tests")
    class PromptClassificationTests {

        @Test
        @DisplayName("Should classify prompt and return most similar")
        void shouldClassifyPromptAndReturnMostSimilar() {
            // Given
            String content = "Test prompt for classification";
            String processedContent = "processed test prompt";
            
            when(embeddingService.preprocessContent(content)).thenReturn(processedContent);
            when(embeddingService.generateEmbedding(processedContent)).thenReturn(testEmbedding);
            when(embeddingService.convertToPGVector(testEmbedding)).thenReturn(testPGVector);
            when(promptVectorRepository.findSimilarPrompts(testPGVector, 1))
                    .thenReturn(Collections.singletonList(mockVectorResult));

            // When
            SimilarityService.PromptClassificationResult result = 
                    similarityService.classifyPrompt(content);

            // Then
            assertNotNull(result);
            assertEquals(testPrompt, result.getMostSimilarPrompt());
            assertEquals(0.85, result.getSimilarityScore());
            assertEquals(SimilarityService.PromptClassification.FORK, result.getClassification());
            assertTrue(result.isFork());
            assertFalse(result.isNew());
            assertFalse(result.isSame());
        }

        @Test
        @DisplayName("Should return NEW classification when no similar prompts found")
        void shouldReturnNewClassificationWhenNoSimilarPromptsFound() {
            // Given
            String content = "Unique content with no matches";
            String processedContent = "processed unique content";
            
            when(embeddingService.preprocessContent(content)).thenReturn(processedContent);
            when(embeddingService.generateEmbedding(processedContent)).thenReturn(testEmbedding);
            when(embeddingService.convertToPGVector(testEmbedding)).thenReturn(testPGVector);
            when(promptVectorRepository.findSimilarPrompts(testPGVector, 1))
                    .thenReturn(Collections.emptyList());

            // When
            SimilarityService.PromptClassificationResult result = 
                    similarityService.classifyPrompt(content);

            // Then
            assertNotNull(result);
            assertNull(result.getMostSimilarPrompt());
            assertEquals(0.0, result.getSimilarityScore());
            assertEquals(SimilarityService.PromptClassification.NEW, result.getClassification());
            assertTrue(result.isNew());
        }

        @Test
        @DisplayName("Should handle null content gracefully")
        void shouldHandleNullContentGracefully() {
            // When
            SimilarityService.PromptClassificationResult result = 
                    similarityService.classifyPrompt(null);

            // Then
            assertNotNull(result);
            assertNull(result.getMostSimilarPrompt());
            assertEquals(0.0, result.getSimilarityScore());
            assertEquals(SimilarityService.PromptClassification.NEW, result.getClassification());
            assertTrue(result.isNew());
        }

        @Test
        @DisplayName("Should handle empty content gracefully")
        void shouldHandleEmptyContentGracefully() {
            // When
            SimilarityService.PromptClassificationResult result = 
                    similarityService.classifyPrompt("   ");

            // Then
            assertNotNull(result);
            assertNull(result.getMostSimilarPrompt());
            assertEquals(0.0, result.getSimilarityScore());
            assertEquals(SimilarityService.PromptClassification.NEW, result.getClassification());
            assertTrue(result.isNew());
        }
    }

    @Nested
    @DisplayName("Calculate Similarity Tests")
    class CalculateSimilarityTests {

        @Test
        @DisplayName("Should calculate similarity between two prompts")
        void shouldCalculateSimilarityBetweenTwoPrompts() {
            // Given
            Prompt prompt1 = Prompt.builder()
                    .id(UUID.randomUUID())
                    .content("First prompt")
                    .embedding(testPGVector)
                    .build();

            Prompt prompt2 = Prompt.builder()
                    .id(UUID.randomUUID())
                    .content("Second prompt")
                    .embedding(testPGVector)
                    .build();

            List<Double> embedding1 = Arrays.asList(0.1, 0.2, 0.3);
            List<Double> embedding2 = Arrays.asList(0.2, 0.3, 0.4);
            double expectedSimilarity = 0.92;

            when(embeddingService.convertFromPGVector(testPGVector))
                    .thenReturn(embedding1)
                    .thenReturn(embedding2);
            when(embeddingService.calculateCosineSimilarity(embedding1, embedding2))
                    .thenReturn(expectedSimilarity);

            // When
            double similarity = similarityService.calculateSimilarity(prompt1, prompt2);

            // Then
            assertEquals(expectedSimilarity, similarity);
            verify(embeddingService, times(2)).convertFromPGVector(testPGVector);
            verify(embeddingService).calculateCosineSimilarity(embedding1, embedding2);
        }

        @Test
        @DisplayName("Should throw exception for null prompts")
        void shouldThrowExceptionForNullPrompts() {
            // Given
            Prompt validPrompt = Prompt.builder()
                    .id(UUID.randomUUID())
                    .embedding(testPGVector)
                    .build();

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> 
                    similarityService.calculateSimilarity(null, validPrompt));
            assertThrows(IllegalArgumentException.class, () -> 
                    similarityService.calculateSimilarity(validPrompt, null));
        }

        @Test
        @DisplayName("Should throw exception for prompts without embeddings")
        void shouldThrowExceptionForPromptsWithoutEmbeddings() {
            // Given
            Prompt promptWithoutEmbedding = Prompt.builder()
                    .id(UUID.randomUUID())
                    .content("No embedding")
                    .build();

            Prompt promptWithEmbedding = Prompt.builder()
                    .id(UUID.randomUUID())
                    .embedding(testPGVector)
                    .build();

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> 
                    similarityService.calculateSimilarity(promptWithoutEmbedding, promptWithEmbedding));
            assertThrows(IllegalArgumentException.class, () -> 
                    similarityService.calculateSimilarity(promptWithEmbedding, promptWithoutEmbedding));
        }
    }

    @Nested
    @DisplayName("Threshold Configuration Tests")
    class ThresholdConfigurationTests {

        @Test
        @DisplayName("Should return configured thresholds")
        void shouldReturnConfiguredThresholds() {
            // When & Then
            assertEquals(0.95, similarityService.getSameThreshold());
            assertEquals(0.70, similarityService.getForkThreshold());
        }

        @Test
        @DisplayName("Should use custom thresholds for classification")
        void shouldUseCustomThresholdsForClassification() {
            // Given - Set custom thresholds
            ReflectionTestUtils.setField(similarityService, "sameThreshold", 0.90);
            ReflectionTestUtils.setField(similarityService, "forkThreshold", 0.60);

            // When & Then
            assertEquals(SimilarityService.PromptClassification.SAME, 
                        similarityService.classifyBySimilarity(0.91));
            assertEquals(SimilarityService.PromptClassification.FORK, 
                        similarityService.classifyBySimilarity(0.75));
            assertEquals(SimilarityService.PromptClassification.NEW, 
                        similarityService.classifyBySimilarity(0.55));
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle embedding service failures gracefully")
        void shouldHandleEmbeddingServiceFailuresGracefully() {
            // Given
            when(embeddingService.convertToPGVector(testEmbedding))
                    .thenThrow(new RuntimeException("Embedding conversion failed"));

            // When & Then
            assertThrows(RuntimeException.class, () -> 
                    similarityService.findSimilarPrompts(testEmbedding, 5));
        }

        @Test
        @DisplayName("Should handle repository failures gracefully")
        void shouldHandleRepositoryFailuresGracefully() {
            // Given
            when(embeddingService.convertToPGVector(testEmbedding)).thenReturn(testPGVector);
            when(promptVectorRepository.findSimilarPrompts(testPGVector, 5))
                    .thenThrow(new RuntimeException("Database connection failed"));

            // When & Then
            assertThrows(RuntimeException.class, () -> 
                    similarityService.findSimilarPrompts(testEmbedding, 5));
        }

        @Test
        @DisplayName("Should handle null PGVector conversion gracefully")
        void shouldHandleNullPGVectorConversionGracefully() {
            // Given
            when(embeddingService.convertToPGVector(testEmbedding)).thenReturn(null);

            // When & Then
            RuntimeException exception = assertThrows(RuntimeException.class, () -> 
                    similarityService.findSimilarPrompts(testEmbedding, 5));
            
            assertEquals("Failed to find similar prompts", exception.getMessage());
        }
    }
}
