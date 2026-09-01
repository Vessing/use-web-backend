package de.useweb.backend.api.dto.uml;

import java.util.List;

import de.useweb.backend.api.dto.snapshot.SlotValueDto;

public record UmlAttributeDto(
        String id,
        String name,
        String type,
        Boolean derived,
        String deriveExpression,
        String initExpression,
        String visibility,
        List<String> redefinedAttributeIds,
        Boolean staticAttribute,
        SlotValueDto classifierValue
) {
    public UmlAttributeDto(String id, String name, String type, Boolean derived, String deriveExpression,
            String initExpression, String visibility, List<String> redefinedAttributeIds) {
        this(id, name, type, derived, deriveExpression, initExpression, visibility,
                redefinedAttributeIds, false, null);
    }
    public UmlAttributeDto(String id, String name, String type, Boolean derived, String deriveExpression,
            String initExpression, String visibility) {
        this(id, name, type, derived, deriveExpression, initExpression, visibility, List.of());
    }
    public UmlAttributeDto(String id, String name, String type, Boolean derived, String deriveExpression,
            String initExpression) {
        this(id, name, type, derived, deriveExpression, initExpression, "PUBLIC", List.of());
    }
    public UmlAttributeDto(String id, String name, String type) {
        this(id, name, type, false, null, null, "PUBLIC", List.of());
    }
}
