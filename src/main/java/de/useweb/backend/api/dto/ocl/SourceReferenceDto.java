package de.useweb.backend.api.dto.ocl;

public record SourceReferenceDto(
        String sourceId,
        String sourceKind,
        Long documentVersion,
        SourceRangeDto range) {
}
