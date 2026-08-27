package de.useweb.backend.domain.ocl;

public record OclExpressionId(String value) {

    public OclExpressionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OclExpressionId must not be blank");
        }
    }
}
