package de.useweb.backend.api.dto.ocl;

public record OclTokenDto(
        String type,
        String text,
        SourceRangeDto sourceRange
) {
}
