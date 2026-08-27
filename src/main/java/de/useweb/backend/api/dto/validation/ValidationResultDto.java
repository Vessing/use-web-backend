package de.useweb.backend.api.dto.validation;

import java.time.Instant;
import java.util.List;

public record ValidationResultDto(
        String id,
        String projectId,
        String objectModelId,
        String status,
        Instant checkedAt,
        List<ValidationErrorDto> findings,
        ValidationSummaryDto summary
) {
}
