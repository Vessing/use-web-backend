package de.useweb.backend.ocl.definition;

public record OclDefinitionId(String value) {
    public OclDefinitionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Definition id must not be blank");
        }
    }
}
