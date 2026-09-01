package de.useweb.backend.api.dto.uml;

import java.util.List;

public record UmlEnumerationDto(String id, String name, List<String> literals, String packageId,
        String qualifiedName, String visibility, List<UmlEnumerationLiteralDto> literalDefinitions) {
    public UmlEnumerationDto(String id, String name, List<String> literals) {
        this(id, name, literals, null, name, "PUBLIC", List.of());
    }

    public UmlEnumerationDto(String id, String name, List<String> literals, String packageId,
            String qualifiedName) {
        this(id, name, literals, packageId, qualifiedName, "PUBLIC", List.of());
    }
}
