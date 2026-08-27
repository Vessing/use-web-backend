package de.useweb.backend.api.dto.snapshot;

import java.util.List;

public record ObjectInstanceDto(
        String id,
        String name,
        String classId,
        List<SlotDto> slots
) {
}
