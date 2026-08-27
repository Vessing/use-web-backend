package de.useweb.backend.api.dto.project;

public record ImportProjectRequestDto(
        String format,
        String content
) {
}
