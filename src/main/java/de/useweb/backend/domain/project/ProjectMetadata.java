package de.useweb.backend.domain.project;

import java.time.Instant;

public record ProjectMetadata(
        String name,
        String description,
        String formatVersion,
        Instant createdAt,
        Instant updatedAt) {

    public ProjectMetadata {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Project name must not be blank");
        }
        if (formatVersion == null || formatVersion.isBlank()) {
            throw new IllegalArgumentException("Project formatVersion must not be blank");
        }
    }
}
