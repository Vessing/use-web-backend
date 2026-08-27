package de.useweb.backend.domain.uml;

import java.util.HashSet;
import java.util.List;

public record UmlEnumeration(UmlEnumerationId id, String name, List<String> literals, UmlPackageId packageId) {
    public UmlEnumeration(UmlEnumerationId id, String name, List<String> literals) {
        this(id, name, literals, null);
    }
    public UmlEnumeration {
        if (id == null) {
            throw new IllegalArgumentException("UmlEnumeration id must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("UmlEnumeration name must not be blank");
        }
        literals = List.copyOf(literals == null ? List.of() : literals);
        if (literals.stream().anyMatch(literal -> literal == null || literal.isBlank())) {
            throw new IllegalArgumentException("UmlEnumeration literals must not be blank");
        }
        if (new HashSet<>(literals).size() != literals.size()) {
            throw new IllegalArgumentException("UmlEnumeration literals must be unique");
        }
    }

    public boolean containsLiteral(String literal) {
        return literals.contains(literal);
    }

    public String qualifiedName(UmlModel model) {
        if (packageId == null) return name;
        return model.findPackage(packageId).map(pkg -> pkg.qualifiedName() + "::" + name).orElse(name);
    }
}
