package de.useweb.backend.api.dto.validation;

import de.useweb.backend.api.dto.ocl.SourceRangeDto;

public record ElementTargetDto(
        String elementType,
        String elementId,
        String path,
        String label,
        SourceRangeDto range
) {
    public ElementTargetDto(String elementType, String elementId, String path) {
        this(elementType, elementId, path, null, null);
    }
}
