package de.useweb.backend.api.dto.ocl;

public record SourceRangeDto(
        int startLine,
        int startColumn,
        int startOffset,
        int endLine,
        int endColumn,
        int endOffset
) {
}
