package de.useweb.backend.api.dto.uml;

import java.util.List;

public record UmlDataTypeDto(String id, String name, List<UmlDataTypePropertyDto> properties,
        String packageId, String qualifiedName, List<UmlOperationDto> operations) {
    public UmlDataTypeDto(String id, String name, List<UmlDataTypePropertyDto> properties,
            String packageId, String qualifiedName) {
        this(id, name, properties, packageId, qualifiedName, List.of());
    }
}
