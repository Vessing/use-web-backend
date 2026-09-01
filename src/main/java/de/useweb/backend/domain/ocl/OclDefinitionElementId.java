package de.useweb.backend.domain.ocl;

public record OclDefinitionElementId(String value) {
    public OclDefinitionElementId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Definition id must not be blank");
    }
}
