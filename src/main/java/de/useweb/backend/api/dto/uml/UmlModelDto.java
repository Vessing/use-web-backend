package de.useweb.backend.api.dto.uml;

import java.util.List;

public record UmlModelDto(
        String id,
        String name,
        List<String> primitiveTypes,
        List<UmlClassDto> classes,
        List<UmlAssociationDto> associations,
        List<UmlInvariantDto> invariants,
        List<UmlEnumerationDto> enumerations,
        List<UmlPackageDto> packages,
        List<UmlModelImportDto> imports,
        List<UmlDataTypeDto> dataTypes
) {
    public UmlModelDto(String id, String name, List<String> primitiveTypes, List<UmlClassDto> classes,
            List<UmlAssociationDto> associations, List<UmlInvariantDto> invariants,
            List<UmlEnumerationDto> enumerations, List<UmlPackageDto> packages, List<UmlModelImportDto> imports) {
        this(id, name, primitiveTypes, classes, associations, invariants, enumerations, packages, imports, List.of());
    }
    public UmlModelDto(String id, String name, List<String> primitiveTypes, List<UmlClassDto> classes,
            List<UmlAssociationDto> associations, List<UmlInvariantDto> invariants,
            List<UmlEnumerationDto> enumerations) {
        this(id, name, primitiveTypes, classes, associations, invariants, enumerations, List.of(), List.of(), List.of());
    }
    public UmlModelDto(String id, String name, List<String> primitiveTypes, List<UmlClassDto> classes,
            List<UmlAssociationDto> associations, List<UmlInvariantDto> invariants) {
        this(id, name, primitiveTypes, classes, associations, invariants, List.of(), List.of(), List.of(), List.of());
    }
}
