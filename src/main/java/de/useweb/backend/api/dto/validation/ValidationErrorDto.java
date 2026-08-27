package de.useweb.backend.api.dto.validation;

import java.util.List;
import java.util.Map;

import de.useweb.backend.api.dto.ocl.SourceRangeDto;

public record ValidationErrorDto(
        String id,
        String kind,
        String code,
        String severity,
        String message,
        String userMessage,
        String technicalMessage,
        String elementType,
        String elementId,
        List<String> relatedElementIds,
        String contextClassId,
        String contextObjectId,
        String invariantId,
        String expression,
        SourceRangeDto location,
        List<ElementTargetDto> targets,
        Map<String, Object> details,
        String suggestedFix
) {
    public ValidationErrorDto(
            String id,
            String code,
            String severity,
            String message,
            List<ElementTargetDto> targets,
            Map<String, Object> details) {
        this(id, "VALIDATION_ERROR", code, severity, message, message, message, null, null, List.of(), null, null, null, null, null, targets, details, null);
    }
}
