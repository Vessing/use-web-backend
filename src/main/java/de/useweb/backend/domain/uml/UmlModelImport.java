package de.useweb.backend.domain.uml;

public record UmlModelImport(
        UmlModelImportId id,
        UmlPackageId importingPackageId,
        UmlPackageId importedPackageId,
        String alias,
        String source,
        String provenance) {

    public UmlModelImport {
        if (id == null || importingPackageId == null || importedPackageId == null) {
            throw new IllegalArgumentException("Import id and package references must not be null");
        }
        alias = normalize(alias);
        source = normalize(source);
        provenance = normalize(provenance);
        if (alias != null && !alias.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid import alias: " + alias);
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
