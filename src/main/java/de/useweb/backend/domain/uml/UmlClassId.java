package de.useweb.backend.domain.uml;

public record UmlClassId(String value) {

    public UmlClassId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("UmlClassId must not be blank");
        }
    }
}
