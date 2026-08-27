package de.useweb.backend.api.dto.uml;

public record MultiplicityDto(
        int lower,
        Integer upper,
        boolean unbounded,
        String raw
) {
}
