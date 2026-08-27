package de.useweb.backend.api.dto.layout;

public record LayoutDto(
        DiagramLayoutDto classDiagram,
        DiagramLayoutDto objectDiagram
) {
}
