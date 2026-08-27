package de.useweb.backend.domain.layout;

public record Viewport(double x, double y, double zoom) {

    public Viewport {
        if (zoom <= 0) {
            throw new IllegalArgumentException("Viewport zoom must be positive");
        }
    }
}
