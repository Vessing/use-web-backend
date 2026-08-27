package de.useweb.backend.api.dto.uml;

public record UmlAttributeDto(
        String id,
        String name,
        String type,
        Boolean derived,
        String deriveExpression,
        String initExpression,
        String visibility
) {
    public UmlAttributeDto(String id, String name, String type, Boolean derived, String deriveExpression,
            String initExpression) {
        this(id, name, type, derived, deriveExpression, initExpression, "PUBLIC");
    }
    public UmlAttributeDto(String id, String name, String type) {
        this(id, name, type, false, null, null, "PUBLIC");
    }
}
