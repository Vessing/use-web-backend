package de.useweb.backend.domain.uml;

public record UmlEnumerationLiteral(UmlEnumerationLiteralId id, String name) {
    public UmlEnumerationLiteral {
        if (id == null) throw new IllegalArgumentException("UmlEnumerationLiteral id must not be null");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("UmlEnumerationLiteral name must not be blank");
        }
    }
}
