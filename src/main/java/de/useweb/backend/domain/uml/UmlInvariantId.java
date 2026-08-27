package de.useweb.backend.domain.uml;

public record UmlInvariantId(String value) {

    public UmlInvariantId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("UmlInvariantId must not be blank");
        }
    }
}
