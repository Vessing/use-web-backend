package de.useweb.backend.api.dto.ocl;

import java.util.List;
import java.util.Map;

import de.useweb.backend.api.dto.validation.ElementTargetDto;

public record OclDiagnosticDto(
        String id,
        String kind,
        String phase,
        String code,
        String severity,
        String message,
        String userMessage,
        String technicalMessage,
        SourceRangeDto sourceRange,
        SourceReferenceDto source,
        List<String> expected,
        String actual,
        List<ElementTargetDto> targets,
        Map<String, Object> details,
        String suggestedFix
) {
    public OclDiagnosticDto(
            String id,
            String kind,
            String code,
            String severity,
            String message,
            String userMessage,
            String technicalMessage,
            SourceRangeDto sourceRange,
            List<String> expected,
            String actual,
            List<ElementTargetDto> targets,
            Map<String, Object> details,
            String suggestedFix) {
        this(id, kind, null, code, severity, message, userMessage, technicalMessage, sourceRange, null,
                expected, actual, targets, details, suggestedFix);
    }

    public OclDiagnosticDto(
            String code,
            String severity,
            String message,
            SourceRangeDto sourceRange,
            List<String> expected,
            String actual) {
        this(null, "VALIDATION_ERROR", null, code, severity, message, message, message, sourceRange, null, expected, actual, List.of(), Map.of(), null);
    }
}
