package de.useweb.backend.domain.layout;

import java.util.List;

public record EdgeLayout(String elementId, List<Point> bendPoints, Point labelPosition) {

    public EdgeLayout {
        if (elementId == null || elementId.isBlank()) {
            throw new IllegalArgumentException("EdgeLayout elementId must not be blank");
        }
        bendPoints = List.copyOf(bendPoints == null ? List.of() : bendPoints);
    }
}
