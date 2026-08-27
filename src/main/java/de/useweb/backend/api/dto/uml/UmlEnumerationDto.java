package de.useweb.backend.api.dto.uml;

import java.util.List;

public record UmlEnumerationDto(String id, String name, List<String> literals, String packageId,
        String qualifiedName) {
    public UmlEnumerationDto(String id, String name, List<String> literals) {
        this(id, name, literals, null, name);
    }
}
