package de.useweb.backend.api.dto.modeltext;

public record ApplyModelTextRequestDto(
        String modelText,
        String format,
        String mode,
        boolean includeDiagnostics,
        String sourceName,
        String sourceFormat,
        String sourceOrigin,
        String baseVersion) {
}
