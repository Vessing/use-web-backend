package de.useweb.backend.api.dto.validation;

import java.time.Instant;

public record ValidationStateDto(
        Instant lastCheckedAt,
        String status,
        ValidationSummaryDto summary
) {
}
