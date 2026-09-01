package de.useweb.backend.domain.uml;

public record UmlClassifierValue(UmlType valueType, Object value) {
    public UmlClassifierValue {
        if (valueType == null) throw new IllegalArgumentException("Classifier value type must not be null");
    }
}
