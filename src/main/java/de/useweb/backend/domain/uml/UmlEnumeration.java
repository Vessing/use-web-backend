package de.useweb.backend.domain.uml;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public record UmlEnumeration(UmlEnumerationId id, String name,
        List<UmlEnumerationLiteral> literalDefinitions, UmlPackageId packageId, UmlVisibility visibility) {
    public UmlEnumeration(UmlEnumerationId id, String name, Collection<String> literals) {
        this(id, name, legacyLiterals(id, literals), null, UmlVisibility.PUBLIC);
    }

    public UmlEnumeration(UmlEnumerationId id, String name, Collection<String> literals, UmlPackageId packageId) {
        this(id, name, legacyLiterals(id, literals), packageId, UmlVisibility.PUBLIC);
    }

    public UmlEnumeration {
        if (id == null) {
            throw new IllegalArgumentException("UmlEnumeration id must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("UmlEnumeration name must not be blank");
        }
        literalDefinitions = List.copyOf(literalDefinitions == null ? List.of() : literalDefinitions);
        if (new HashSet<>(literalDefinitions.stream().map(UmlEnumerationLiteral::id).toList()).size()
                != literalDefinitions.size()) {
            throw new IllegalArgumentException("UmlEnumeration literal ids must be unique");
        }
        if (new HashSet<>(literalDefinitions.stream().map(UmlEnumerationLiteral::name).toList()).size()
                != literalDefinitions.size()) {
            throw new IllegalArgumentException("UmlEnumeration literals must be unique");
        }
        visibility = UmlVisibility.defaulted(visibility);
    }

    public List<String> literals() {
        return literalDefinitions.stream().map(UmlEnumerationLiteral::name).toList();
    }

    public boolean containsLiteral(String literal) {
        return literals().contains(literal);
    }

    public String qualifiedName(UmlModel model) {
        if (packageId == null) return name;
        return model.findPackage(packageId).map(pkg -> pkg.qualifiedName() + "::" + name).orElse(name);
    }

    private static List<UmlEnumerationLiteral> legacyLiterals(UmlEnumerationId enumerationId,
            Collection<String> literals) {
        if (literals == null) return List.of();
        int[] index = {0};
        return literals.stream().map(name -> new UmlEnumerationLiteral(
                new UmlEnumerationLiteralId(enumerationId.value() + ":literal:" + index[0]++), name)).toList();
    }
}
