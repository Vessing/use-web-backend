package de.useweb.backend.api.dto.modeltext;

import java.time.Instant;

public record ModelTextDto(
        String projectId,
        String modelText,
        String format,
        String version,
        String sourceName,
        String sourceOrigin,
        String lineEnding,
        Instant updatedAt) {
}
