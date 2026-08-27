package de.useweb.backend.domain.uml;

public record UmlModelImportId(String value) {
    public UmlModelImportId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("UmlModelImportId must not be blank");
    }
}
