package de.useweb.backend.api.dto.layout;

public record NodeLayoutDto(
        String elementId,
        double x,
        double y,
        Double width,
        Double height
) {
}
