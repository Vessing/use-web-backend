package de.useweb.backend.api.dto.snapshot;

import java.util.List;

public record ObjectLinkDto(
        String id,
        String associationId,
        List<ObjectLinkEndValueDto> endValues,
        String associationClassObjectId
) {
    public ObjectLinkDto(String id, String associationId, List<ObjectLinkEndValueDto> endValues) {
        this(id, associationId, endValues, null);
    }
}
