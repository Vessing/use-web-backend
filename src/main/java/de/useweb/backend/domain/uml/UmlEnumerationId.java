package de.useweb.backend.domain.uml;

public record UmlEnumerationId(String value) {
    public UmlEnumerationId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("UmlEnumerationId value must not be blank");
        }
    }
}
