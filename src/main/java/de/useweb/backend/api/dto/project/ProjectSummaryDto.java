package de.useweb.backend.api.dto.project;

import java.time.Instant;

public record ProjectSummaryDto(
        String id,
        String name,
        String description,
        Instant updatedAt,
        String sourceFormat
) {
}
