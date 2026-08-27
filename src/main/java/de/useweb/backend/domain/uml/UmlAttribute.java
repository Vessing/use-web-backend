package de.useweb.backend.domain.uml;

public record UmlAttribute(UmlAttributeId id, String name, UmlType type,
        boolean derived, String deriveExpression, String initExpression, UmlVisibility visibility) {

    public UmlAttribute(UmlAttributeId id, String name, UmlType type,
            boolean derived, String deriveExpression, String initExpression) {
        this(id, name, type, derived, deriveExpression, initExpression, UmlVisibility.PUBLIC);
    }

    public UmlAttribute(UmlAttributeId id, String name, UmlType type) {
        this(id, name, type, false, null, null, UmlVisibility.PUBLIC);
    }

    public UmlAttribute {
        if (id == null) {
            throw new IllegalArgumentException("UmlAttribute id must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("UmlAttribute name must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("UmlAttribute type must not be null");
        }
        visibility = UmlVisibility.defaulted(visibility);
        deriveExpression = normalize(deriveExpression);
        initExpression = normalize(initExpression);
        if (derived && deriveExpression == null) {
            throw new IllegalArgumentException("Derived attribute requires deriveExpression");
        }
        if (!derived && deriveExpression != null) {
            throw new IllegalArgumentException("deriveExpression requires derived attribute");
        }
        if (derived && initExpression != null) {
            throw new IllegalArgumentException("Derived attribute must not have initExpression");
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
