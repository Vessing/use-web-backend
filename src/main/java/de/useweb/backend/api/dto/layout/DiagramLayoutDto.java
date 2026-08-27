package de.useweb.backend.api.dto.layout;

import java.util.List;

public record DiagramLayoutDto(
        List<NodeLayoutDto> nodes,
        List<EdgeLayoutDto> edges,
        ViewportDto viewport
) {
}
