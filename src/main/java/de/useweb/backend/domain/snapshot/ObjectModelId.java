package de.useweb.backend.domain.snapshot;

public record ObjectModelId(String value) {

    public ObjectModelId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ObjectModelId must not be blank");
        }
    }
}
