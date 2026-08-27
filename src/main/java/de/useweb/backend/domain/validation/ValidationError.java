package de.useweb.backend.domain.validation;

import java.util.List;
import java.util.Map;

public record ValidationError(
        ValidationErrorId id,
        ValidationErrorCode code,
        ValidationSeverity severity,
        String message,
        List<ElementTarget> targets,
        Map<String, Object> details) {

    public ValidationError {
        if (id == null) {
            throw new IllegalArgumentException("ValidationError id must not be null");
        }
        if (code == null) {
            throw new IllegalArgumentException("ValidationError code must not be null");
        }
        if (severity == null) {
            throw new IllegalArgumentException("ValidationError severity must not be null");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("ValidationError message must not be blank");
        }
        targets = List.copyOf(targets == null ? List.of() : targets);
        details = Map.copyOf(details == null ? Map.of() : details);
    }
}
