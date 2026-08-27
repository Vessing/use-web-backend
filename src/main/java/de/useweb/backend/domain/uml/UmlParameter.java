package de.useweb.backend.domain.uml;

public record UmlParameter(UmlParameterId id, String name, UmlType type, ParameterDirection direction, int position) {

    public UmlParameter(UmlParameterId id, String name, UmlType type) {
        this(id, name, type, ParameterDirection.IN, 0);
    }

    public UmlParameter {
        if (id == null) {
            throw new IllegalArgumentException("UmlParameter id must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("UmlParameter name must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("UmlParameter type must not be null");
        }
        direction = ParameterDirection.defaulted(direction);
        if (position < 0) throw new IllegalArgumentException("UmlParameter position must not be negative");
    }
}
