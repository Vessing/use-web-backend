package de.useweb.backend.api.dto.uml;

public record UmlModelImportDto(String id, String importingPackageId, String importedPackageId,
        String alias, String source, String provenance) {}
