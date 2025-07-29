package com.codepromptu.shared.serialization;

import com.codepromptu.shared.domain.Prompt;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Custom Jackson serializer for Prompt entities.
 * Handles circular references and controls exactly what gets serialized to JSON.
 * This avoids the need for DTOs while maintaining full control over the API response structure.
 */
public class PromptSerializer extends JsonSerializer<Prompt> {

    private static final Logger logger = LoggerFactory.getLogger(PromptSerializer.class);

    @Override
    public void serialize(Prompt prompt, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (prompt == null) {
            gen.writeNull();
            return;
        }

        try {
            gen.writeStartObject();

            // Basic entity fields
            gen.writeStringField("id", prompt.getId() != null ? prompt.getId().toString() : null);
            gen.writeStringField("content", prompt.getContent());
            gen.writeStringField("author", prompt.getAuthor());
            gen.writeStringField("purpose", prompt.getPurpose());
            gen.writeStringField("successCriteria", prompt.getSuccessCriteria());
            gen.writeStringField("teamOwner", prompt.getTeamOwner());
            gen.writeStringField("modelTarget", prompt.getModelTarget());
            gen.writeNumberField("version", prompt.getVersion() != null ? prompt.getVersion() : 1);
            gen.writeBooleanField("isActive", prompt.getIsActive() != null ? prompt.getIsActive() : true);

            // Timestamps
            if (prompt.getCreatedAt() != null) {
                gen.writeStringField("createdAt", prompt.getCreatedAt().toString());
            }
            if (prompt.getUpdatedAt() != null) {
                gen.writeStringField("updatedAt", prompt.getUpdatedAt().toString());
            }

            // Tags array
            if (prompt.getTags() != null) {
                gen.writeArrayFieldStart("tags");
                for (String tag : prompt.getTags()) {
                    gen.writeString(tag);
                }
                gen.writeEndArray();
            } else {
                gen.writeNullField("tags");
            }

            // Metadata (JsonNode) - let Jackson handle this naturally
            if (prompt.getMetadata() != null) {
                gen.writeFieldName("metadata");
                gen.writeTree(prompt.getMetadata());
            } else {
                gen.writeNullField("metadata");
            }

            // Hierarchy information (computed fields)
            // These avoid circular references by not serializing the actual parent/children objects
            gen.writeBooleanField("isRoot", prompt.checkIsRoot());
            gen.writeBooleanField("hasChildren", prompt.hasChildren());
            gen.writeNumberField("depth", prompt.getDepth());

            // Parent reference (ID only, not the full object)
            if (prompt.getParent() != null) {
                gen.writeStringField("parentId", prompt.getParent().getId().toString());
            } else {
                gen.writeNullField("parentId");
            }

            // Children count (not the full children list)
            if (prompt.getChildren() != null) {
                gen.writeNumberField("childrenCount", prompt.getChildren().size());
            } else {
                gen.writeNumberField("childrenCount", 0);
            }

            // Cross-references count (not the full objects)
            if (prompt.getOutgoingCrossrefs() != null) {
                gen.writeNumberField("outgoingCrossrefsCount", prompt.getOutgoingCrossrefs().size());
            } else {
                gen.writeNumberField("outgoingCrossrefsCount", 0);
            }

            if (prompt.getIncomingCrossrefs() != null) {
                gen.writeNumberField("incomingCrossrefsCount", prompt.getIncomingCrossrefs().size());
            } else {
                gen.writeNumberField("incomingCrossrefsCount", 0);
            }

            // Usage and evaluation counts (not the full objects)
            if (prompt.getUsages() != null) {
                gen.writeNumberField("usageCount", prompt.getUsages().size());
            } else {
                gen.writeNumberField("usageCount", 0);
            }

            if (prompt.getEvaluations() != null) {
                gen.writeNumberField("evaluationCount", prompt.getEvaluations().size());
            } else {
                gen.writeNumberField("evaluationCount", 0);
            }

            gen.writeEndObject();

        } catch (Exception e) {
            logger.error("Error serializing Prompt with ID: {}", prompt.getId(), e);
            // Write a minimal error response
            gen.writeStartObject();
            gen.writeStringField("id", prompt.getId() != null ? prompt.getId().toString() : null);
            gen.writeStringField("error", "Serialization error");
            gen.writeEndObject();
        }
    }
}
