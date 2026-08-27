package de.useweb.backend.domain.uml;

public record UmlModelId(String value) {

    public UmlModelId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("UmlModelId must not be blank");
        }
    }
}
