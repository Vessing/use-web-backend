package de.useweb.backend.domain.uml;

public record UmlDataTypeProperty(String id, String name, UmlType type) {
    public UmlDataTypeProperty {
        if (id == null || id.isBlank() || name == null || name.isBlank() || type == null) {
            throw new IllegalArgumentException("DataType property metadata must not be blank");
        }
    }
}
