package de.useweb.backend.api.dto.uml;

import java.util.List;

public record UmlAssociationDto(
        String id,
        String name,
        List<UmlAssociationEndDto> ends,
        String associationClassId
) {
    public UmlAssociationDto(String id, String name, List<UmlAssociationEndDto> ends) {
        this(id, name, ends, null);
    }
}
