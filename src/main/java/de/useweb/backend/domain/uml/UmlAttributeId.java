package de.useweb.backend.domain.uml;

public record UmlAttributeId(String value) {

    public UmlAttributeId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("UmlAttributeId must not be blank");
        }
    }
}
