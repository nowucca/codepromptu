package com.codepromptu.shared.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

/**
 * Unit tests for the Prompt domain entity.
 */
class PromptTest {

    private Prompt rootPrompt;
    private Prompt childPrompt;

    @BeforeEach
    void setUp() {
        rootPrompt = Prompt.builder()
                .content("You are a helpful assistant. Please help the user with their question: {question}")
                .author("john.doe")
                .purpose("General assistance prompt")
                .teamOwner("ai-team")
                .modelTarget("gpt-4")
                .version(1)
                .isActive(true)
                .build();

        childPrompt = Prompt.builder()
                .parent(rootPrompt)
                .content("You are a helpful coding assistant. Please help the user with their programming question: {question}")
                .author("jane.smith")
                .purpose("Coding assistance prompt")
                .teamOwner("ai-team")
                .modelTarget("gpt-4")
                .version(2)
                .isActive(true)
                .build();
    }

    @Test
    void testPromptCreation() {
        assertNotNull(rootPrompt);
        assertEquals("You are a helpful assistant. Please help the user with their question: {question}", 
                     rootPrompt.getContent());
        assertEquals("john.doe", rootPrompt.getAuthor());
        assertEquals("General assistance prompt", rootPrompt.getPurpose());
        assertEquals("ai-team", rootPrompt.getTeamOwner());
        assertEquals("gpt-4", rootPrompt.getModelTarget());
        assertEquals(1, rootPrompt.getVersion());
        assertTrue(rootPrompt.getIsActive());
    }

    @Test
    void testRootPromptIdentification() {
        assertTrue(rootPrompt.isRoot());
        assertFalse(childPrompt.isRoot());
    }

    @Test
    void testParentChildRelationship() {
        assertEquals(rootPrompt, childPrompt.getParent());
        assertTrue(rootPrompt.getChildren().contains(childPrompt));
    }

    @Test
    void testHasChildren() {
        assertTrue(rootPrompt.hasChildren());
        assertFalse(childPrompt.hasChildren());
    }

    @Test
    void testGetRoot() {
        assertEquals(rootPrompt, rootPrompt.getRoot());
        assertEquals(rootPrompt, childPrompt.getRoot());
    }

    @Test
    void testGetDepth() {
        assertEquals(0, rootPrompt.getDepth());
        assertEquals(1, childPrompt.getDepth());
    }

    @Test
    void testCreateChild() {
        String newContent = "You are a specialized AI assistant for data science tasks.";
        String author = "data.scientist";
        
        Prompt grandChild = rootPrompt.createChild(newContent, author);
        
        assertNotNull(grandChild);
        assertEquals(newContent, grandChild.getContent());
        assertEquals(author, grandChild.getAuthor());
        assertEquals(rootPrompt, grandChild.getParent());
        assertEquals(rootPrompt.getPurpose(), grandChild.getPurpose());
        assertEquals(rootPrompt.getTeamOwner(), grandChild.getTeamOwner());
        assertEquals(rootPrompt.getModelTarget(), grandChild.getModelTarget());
        assertEquals(rootPrompt.getVersion() + 1, grandChild.getVersion());
        assertTrue(grandChild.getIsActive());
    }

    @Test
    void testAddCrossref() {
        Prompt relatedPrompt = Prompt.builder()
                .content("You are an AI assistant specialized in customer support.")
                .author("support.team")
                .purpose("Customer support assistance")
                .teamOwner("support-team")
                .modelTarget("gpt-3.5-turbo")
                .build();

        rootPrompt.addCrossref(relatedPrompt, "similar", 0.85f, "Both are general assistance prompts");

        assertEquals(1, rootPrompt.getOutgoingCrossrefs().size());
        assertEquals(1, relatedPrompt.getIncomingCrossrefs().size());

        PromptCrossref crossref = rootPrompt.getOutgoingCrossrefs().get(0);
        assertEquals(rootPrompt, crossref.getSourcePrompt());
        assertEquals(relatedPrompt, crossref.getTargetPrompt());
        assertEquals("similar", crossref.getRelationshipType());
        assertEquals(0.85f, crossref.getSimilarityScore());
        assertEquals("Both are general assistance prompts", crossref.getNotes());
    }

    @Test
    void testPromptBuilder() {
        Prompt prompt = Prompt.builder()
                .content("Test prompt content")
                .author("test.user")
                .purpose("Testing")
                .successCriteria("Should respond helpfully")
                .tags(new String[]{"test", "example"})
                .teamOwner("test-team")
                .modelTarget("gpt-4")
                .version(1)
                .isActive(true)
                .build();

        assertNotNull(prompt);
        assertEquals("Test prompt content", prompt.getContent());
        assertEquals("test.user", prompt.getAuthor());
        assertEquals("Testing", prompt.getPurpose());
        assertEquals("Should respond helpfully", prompt.getSuccessCriteria());
        assertArrayEquals(new String[]{"test", "example"}, prompt.getTags());
        assertEquals("test-team", prompt.getTeamOwner());
        assertEquals("gpt-4", prompt.getModelTarget());
        assertEquals(1, prompt.getVersion());
        assertTrue(prompt.getIsActive());
    }

    @Test
    void testPromptEquality() {
        UUID id = UUID.randomUUID();
        
        Prompt prompt1 = Prompt.builder()
                .id(id)
                .content("Test content")
                .build();
                
        Prompt prompt2 = Prompt.builder()
                .id(id)
                .content("Different content")
                .build();

        // Equality is based on ID only (as defined by @EqualsAndHashCode(onlyExplicitlyIncluded = true))
        assertEquals(prompt1, prompt2);
        assertEquals(prompt1.hashCode(), prompt2.hashCode());
    }

    @Test
    void testPromptDefaultValues() {
        Prompt prompt = Prompt.builder()
                .content("Minimal prompt")
                .build();

        assertEquals(1, prompt.getVersion());
        assertTrue(prompt.getIsActive());
        assertNotNull(prompt.getChildren());
        assertTrue(prompt.getChildren().isEmpty());
        assertNotNull(prompt.getOutgoingCrossrefs());
        assertTrue(prompt.getOutgoingCrossrefs().isEmpty());
        assertNotNull(prompt.getIncomingCrossrefs());
        assertTrue(prompt.getIncomingCrossrefs().isEmpty());
        assertNotNull(prompt.getUsages());
        assertTrue(prompt.getUsages().isEmpty());
        assertNotNull(prompt.getEvaluations());
        assertTrue(prompt.getEvaluations().isEmpty());
    }

    @Test
    void testComplexHierarchy() {
        // Create a more complex hierarchy: root -> child -> grandchild
        Prompt grandChild = childPrompt.createChild(
            "You are a Python coding assistant specialized in data analysis.",
            "python.expert"
        );

        // Test hierarchy navigation
        assertEquals(rootPrompt, grandChild.getRoot());
        assertEquals(2, grandChild.getDepth());
        assertEquals(childPrompt, grandChild.getParent());
        assertTrue(childPrompt.hasChildren());
        assertTrue(childPrompt.getChildren().contains(grandChild));

        // Test version progression
        assertEquals(1, rootPrompt.getVersion());
        assertEquals(2, childPrompt.getVersion());
        assertEquals(3, grandChild.getVersion());
    }
}
