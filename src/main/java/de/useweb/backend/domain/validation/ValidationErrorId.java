package de.useweb.backend.domain.validation;

public record ValidationErrorId(String value) {

    public ValidationErrorId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ValidationErrorId must not be blank");
        }
    }
}
