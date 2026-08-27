package de.useweb.backend.api.dto.layout;

import java.util.List;

import de.useweb.backend.api.dto.common.PointDto;

public record EdgeLayoutDto(
        String elementId,
        List<PointDto> bendPoints,
        PointDto labelPosition
) {
}
