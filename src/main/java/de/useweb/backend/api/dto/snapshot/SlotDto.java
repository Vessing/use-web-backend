package de.useweb.backend.api.dto.snapshot;

public record SlotDto(
        String id,
        String attributeId,
        SlotValueDto value,
        Boolean isUnset
) {
}
