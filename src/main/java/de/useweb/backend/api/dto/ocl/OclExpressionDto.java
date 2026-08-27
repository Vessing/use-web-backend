package de.useweb.backend.api.dto.ocl;

public record OclExpressionDto(
        String id,
        String text,
        String language,
        String languageVersion
) {
}
