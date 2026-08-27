package de.useweb.backend.api.dto.snapshot;

import java.util.List;

public record ObjectModelDto(
        String id,
        String name,
        List<ObjectInstanceDto> objects,
        List<ObjectLinkDto> links
) {
}
