package de.useweb.backend.domain.uml;

import java.util.Arrays;
import java.util.Optional;

public record UmlType(String name) {

    public static final UmlType STRING = new UmlType(PrimitiveType.STRING.displayName());
    public static final UmlType INTEGER = new UmlType(PrimitiveType.INTEGER.displayName());
    public static final UmlType REAL = new UmlType(PrimitiveType.REAL.displayName());
    public static final UmlType BOOLEAN = new UmlType(PrimitiveType.BOOLEAN.displayName());
    public static final UmlType UNLIMITED_NATURAL = new UmlType("UnlimitedNatural");
    public static final UmlType VOID = new UmlType("Void");

    public UmlType {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("UmlType name must not be blank");
        }
    }

    public static UmlType classType(String className) {
        return new UmlType(className);
    }

    public static UmlType enumerationType(String enumerationName) {
        return new UmlType(enumerationName);
    }

    public static UmlType dataType(String dataTypeName) {
        return new UmlType(dataTypeName);
    }

    public Optional<PrimitiveType> primitiveType() {
        return Arrays.stream(PrimitiveType.values())
                .filter(type -> type.displayName().equals(name))
                .findFirst();
    }

    public boolean isPrimitive() {
        return primitiveType().isPresent();
    }
}
