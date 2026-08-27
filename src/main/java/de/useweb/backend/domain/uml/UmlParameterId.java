package de.useweb.backend.domain.uml;

public record UmlParameterId(String value) {

    public UmlParameterId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("UmlParameterId must not be blank");
        }
    }
}
