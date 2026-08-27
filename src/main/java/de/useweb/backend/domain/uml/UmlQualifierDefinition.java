package de.useweb.backend.domain.uml;

public record UmlQualifierDefinition(UmlQualifierId id, String name, UmlType type, int order) {
    public UmlQualifierDefinition {
        if (id == null) throw new IllegalArgumentException("Qualifier id must not be null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Qualifier name must not be blank");
        if (type == null) throw new IllegalArgumentException("Qualifier type must not be null");
        if (order < 0) throw new IllegalArgumentException("Qualifier order must not be negative");
    }
}
