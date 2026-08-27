package de.useweb.backend.domain.layout;

public record NodeLayout(String elementId, double x, double y, Double width, Double height) {

    public NodeLayout {
        if (elementId == null || elementId.isBlank()) {
            throw new IllegalArgumentException("NodeLayout elementId must not be blank");
        }
    }
}
