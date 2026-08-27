package de.useweb.backend.validation.result;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import de.useweb.backend.domain.validation.ElementTarget;
import de.useweb.backend.domain.validation.ValidationError;
import de.useweb.backend.domain.validation.ValidationErrorCode;
import de.useweb.backend.domain.validation.ValidationErrorId;
import de.useweb.backend.domain.validation.ValidationSeverity;

public class ValidationErrorFactory {

    private final AtomicInteger sequence = new AtomicInteger();

    public ValidationError error(
            ValidationErrorCode code,
            String message,
            List<ElementTarget> targets,
            Map<String, Object> details) {
        return new ValidationError(
                new ValidationErrorId("validation-error-" + sequence.incrementAndGet()),
                code,
                ValidationSeverity.ERROR,
                message,
                targets,
                details);
    }
}
