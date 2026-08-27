package de.useweb.backend.domain.uml;

public record UmlDataTypeId(String value) {
    public UmlDataTypeId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("UmlDataTypeId value must not be blank");
    }
}
