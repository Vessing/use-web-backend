package de.useweb.backend.api.dto.ocl;

import java.util.List;

import de.useweb.backend.api.dto.uml.UmlParameterDto;

public record OclDefinitionElementDto(
        String id,
        String kind,
        String ownerKind,
        String ownerId,
        String ownerName,
        String name,
        String qualifiedName,
        String resultType,
        List<UmlParameterDto> parameters,
        String expression,
        SourceRangeDto sourceRange) {
}
