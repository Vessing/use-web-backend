package de.useweb.backend.domain.uml;

public record UmlQualifierId(String value) {
    public UmlQualifierId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("UmlQualifierId must not be blank");
    }
}
