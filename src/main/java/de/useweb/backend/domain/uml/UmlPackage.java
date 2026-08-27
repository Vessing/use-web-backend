package de.useweb.backend.domain.uml;

public record UmlPackage(UmlPackageId id, String qualifiedName) {
    public UmlPackage {
        if (id == null) throw new IllegalArgumentException("UmlPackage id must not be null");
        qualifiedName = normalizeQualifiedName(qualifiedName);
    }

    static String normalizeQualifiedName(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Qualified name must not be blank");
        String normalized = value.trim();
        for (String segment : normalized.split("::", -1)) {
            if (!segment.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IllegalArgumentException("Invalid qualified-name segment: " + segment);
            }
        }
        return normalized;
    }
}
