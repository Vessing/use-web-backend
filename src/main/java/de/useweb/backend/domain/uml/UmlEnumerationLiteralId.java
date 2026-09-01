package de.useweb.backend.domain.uml;

public record UmlEnumerationLiteralId(String value) {
    public UmlEnumerationLiteralId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("UmlEnumerationLiteralId value must not be blank");
        }
    }
}
