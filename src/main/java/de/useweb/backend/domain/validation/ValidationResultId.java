package de.useweb.backend.domain.validation;

public record ValidationResultId(String value) {

    public ValidationResultId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ValidationResultId must not be blank");
        }
    }
}
