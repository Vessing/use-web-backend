package de.useweb.backend.api.dto.modeltext;

import java.util.Map;

public record ApplyModelTextRequestDto(
        String modelText,
        String format,
        String mode,
        boolean includeDiagnostics,
        String sourceName,
        String sourceFormat,
        String sourceOrigin,
        String baseVersion,
        Map<String, String> sourceFiles,
        boolean replaceSourceFiles) {

    public ApplyModelTextRequestDto(String modelText, String format, String mode, boolean includeDiagnostics,
            String sourceName, String sourceFormat, String sourceOrigin, String baseVersion) {
        this(modelText, format, mode, includeDiagnostics, sourceName, sourceFormat, sourceOrigin, baseVersion, Map.of(), false);
    }

    public ApplyModelTextRequestDto(String modelText, String format, String mode, boolean includeDiagnostics,
            String sourceName, String sourceFormat, String sourceOrigin, String baseVersion,
            Map<String, String> sourceFiles) {
        this(modelText, format, mode, includeDiagnostics, sourceName, sourceFormat, sourceOrigin, baseVersion,
                sourceFiles, false);
    }

    public ApplyModelTextRequestDto {
        sourceFiles = Map.copyOf(sourceFiles == null ? Map.of() : sourceFiles);
    }
}
