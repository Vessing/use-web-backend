package de.useweb.backend.domain.uml;

import java.util.List;

public record UmlAttribute(UmlAttributeId id, String name, UmlType type,
        boolean derived, String deriveExpression, String initExpression, UmlVisibility visibility,
        List<UmlAttributeId> redefinedAttributeIds, boolean staticAttribute,
        UmlClassifierValue classifierValue) {

    public UmlAttribute(UmlAttributeId id, String name, UmlType type, boolean derived,
            String deriveExpression, String initExpression, UmlVisibility visibility,
            List<UmlAttributeId> redefinedAttributeIds) {
        this(id, name, type, derived, deriveExpression, initExpression, visibility,
                redefinedAttributeIds, false, null);
    }

    public UmlAttribute(UmlAttributeId id, String name, UmlType type, boolean derived,
            String deriveExpression, String initExpression, UmlVisibility visibility) {
        this(id, name, type, derived, deriveExpression, initExpression, visibility, List.of());
    }

    public UmlAttribute(UmlAttributeId id, String name, UmlType type,
            boolean derived, String deriveExpression, String initExpression) {
        this(id, name, type, derived, deriveExpression, initExpression, UmlVisibility.PUBLIC, List.of());
    }

    public UmlAttribute(UmlAttributeId id, String name, UmlType type) {
        this(id, name, type, false, null, null, UmlVisibility.PUBLIC, List.of());
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
        redefinedAttributeIds = List.copyOf(redefinedAttributeIds == null ? List.of() : redefinedAttributeIds);
        if (redefinedAttributeIds.contains(id) || redefinedAttributeIds.stream().distinct().count() != redefinedAttributeIds.size()) {
            throw new IllegalArgumentException("Attribute redefinition targets must be unique and must not reference self");
        }
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
        if (!staticAttribute && classifierValue != null) {
            throw new IllegalArgumentException("Classifier value requires a static attribute");
        }
        if (derived && classifierValue != null) {
            throw new IllegalArgumentException("Derived static attributes must not store a classifier value");
        }
        if (classifierValue != null && !classifierValue.valueType().equals(type)) {
            throw new IllegalArgumentException("Classifier value type must match attribute type");
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
