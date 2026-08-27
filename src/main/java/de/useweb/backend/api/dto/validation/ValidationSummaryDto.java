package de.useweb.backend.api.dto.validation;

public record ValidationSummaryDto(
        int errorCount,
        int warningCount,
        int infoCount
) {
}
