package com.codepromptu.api.util;

import com.codepromptu.api.dto.PromptResponseDto;
import com.codepromptu.shared.domain.Prompt;
import org.springframework.stereotype.Component;

/**
 * Utility class for mapping between Prompt entity and DTOs.
 */
@Component
public class PromptMapper {

    /**
     * Convert a Prompt entity to a PromptResponseDto.
     * This avoids Jackson serialization issues with circular references.
     */
    public PromptResponseDto toResponseDto(Prompt prompt) {
        if (prompt == null) {
            return null;
        }

        PromptResponseDto dto = new PromptResponseDto();
        dto.setId(prompt.getId());
        dto.setContent(prompt.getContent());
        dto.setMetadata(prompt.getMetadata());
        dto.setAuthor(prompt.getAuthor());
        dto.setPurpose(prompt.getPurpose());
        dto.setSuccessCriteria(prompt.getSuccessCriteria());
        dto.setTags(prompt.getTags());
        dto.setTeamOwner(prompt.getTeamOwner());
        dto.setModelTarget(prompt.getModelTarget());
        dto.setVersion(prompt.getVersion());
        dto.setIsActive(prompt.getIsActive());
        dto.setCreatedAt(prompt.getCreatedAt());
        dto.setUpdatedAt(prompt.getUpdatedAt());
        dto.setParentId(prompt.getParent() != null ? prompt.getParent().getId() : null);
        
        // Calculate these values safely without triggering Jackson circular reference detection
        dto.setRoot(prompt.checkIsRoot());
        dto.setHasChildren(prompt.getChildren() != null && !prompt.getChildren().isEmpty());
        
        // Calculate depth manually by traversing parent chain
        int depth = 0;
        Prompt current = prompt;
        while (current.getParent() != null) {
            depth++;
            current = current.getParent();
            // Safety check to prevent infinite loops
            if (depth > 100) {
                break;
            }
        }
        dto.setDepth(depth);
        
        return dto;
    }
}
