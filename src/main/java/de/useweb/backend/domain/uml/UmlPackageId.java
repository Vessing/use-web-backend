package de.useweb.backend.domain.uml;

public record UmlPackageId(String value) {
    public UmlPackageId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("UmlPackageId must not be blank");
        }
    }
}
