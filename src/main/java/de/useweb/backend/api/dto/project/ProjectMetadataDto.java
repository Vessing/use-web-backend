package de.useweb.backend.api.dto.project;

import java.time.Instant;

public record ProjectMetadataDto(
        String id,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
