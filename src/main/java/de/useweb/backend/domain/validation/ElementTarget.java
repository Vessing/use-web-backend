package de.useweb.backend.domain.validation;

public record ElementTarget(ElementType elementType, String elementId, String path) {

    public ElementTarget {
        if (elementType == null) {
            throw new IllegalArgumentException("ElementTarget elementType must not be null");
        }
        if (elementId == null || elementId.isBlank()) {
            throw new IllegalArgumentException("ElementTarget elementId must not be blank");
        }
    }

    public static ElementTarget object(String objectId) {
        return new ElementTarget(ElementType.OBJECT, objectId, null);
    }

    public static ElementTarget invariant(String invariantId) {
        return new ElementTarget(ElementType.INVARIANT, invariantId, null);
    }
}
