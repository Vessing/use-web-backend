package de.useweb.backend.api.dto.uml;

import java.util.List;

public record UmlClassDto(
        String id,
        String name,
        List<UmlAttributeDto> attributes,
        List<UmlOperationDto> operations,
        boolean abstractClass,
        List<String> superClassIds,
        String visibility,
        String packageId,
        String qualifiedName
) {
    public UmlClassDto(String id, String name, List<UmlAttributeDto> attributes, List<UmlOperationDto> operations,
            boolean abstractClass, List<String> superClassIds) {
        this(id, name, attributes, operations, abstractClass, superClassIds, "PUBLIC", null, name);
    }
    public UmlClassDto(String id, String name, List<UmlAttributeDto> attributes, List<UmlOperationDto> operations) {
        this(id, name, attributes, operations, false, List.of(), "PUBLIC", null, name);
    }
}
