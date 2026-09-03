package de.useweb.backend.api.dto.modeltext;

import java.time.Instant;
import java.util.List;

public record ModelTextDto(
        String projectId,
        String modelText,
        String format,
        String version,
        String sourceName,
        String sourceOrigin,
        String lineEnding,
        Instant updatedAt,
        List<ModelTextSourceProvenanceDto> sources,
        List<ModelTextSourceFileDto> sourceFiles) {

    public ModelTextDto(String projectId, String modelText, String format, String version,
            String sourceName, String sourceOrigin, String lineEnding, Instant updatedAt) {
        this(projectId, modelText, format, version, sourceName, sourceOrigin, lineEnding, updatedAt, List.of(), List.of());
    }
}
