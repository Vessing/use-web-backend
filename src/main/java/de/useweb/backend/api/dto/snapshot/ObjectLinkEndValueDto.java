package de.useweb.backend.api.dto.snapshot;

import java.util.List;

public record ObjectLinkEndValueDto(
        String associationEndId,
        String objectId,
        List<QualifierValueDto> qualifierValues
) {
    public ObjectLinkEndValueDto(String associationEndId, String objectId) {
        this(associationEndId, objectId, List.of());
    }
}
