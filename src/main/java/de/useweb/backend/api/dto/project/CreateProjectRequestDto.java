package de.useweb.backend.api.dto.project;

public record CreateProjectRequestDto(
        String name,
        String description,
        String template
) {
}
