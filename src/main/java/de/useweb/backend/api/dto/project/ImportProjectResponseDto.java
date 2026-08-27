package de.useweb.backend.api.dto.project;

import java.util.List;

import de.useweb.backend.api.dto.error.ApiErrorDto;

public record ImportProjectResponseDto(
        String status,
        ProjectDto project,
        List<ApiErrorDto> diagnostics
) {
}
