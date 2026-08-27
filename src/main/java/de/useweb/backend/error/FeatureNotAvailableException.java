package de.useweb.backend.error;

import java.time.Instant;
import java.util.Map;

import de.useweb.backend.api.dto.error.ApiErrorDto;

public class FeatureNotAvailableException extends RuntimeException {

    private final ApiErrorDto error;

    public FeatureNotAvailableException(String feature) {
        super(feature + " is not available in the MVP.");
        this.error = new ApiErrorDto(
                "FEATURE_NOT_AVAILABLE",
                feature + " is not available in the MVP.",
                "Diese Funktion ist im MVP noch nicht verfuegbar.",
                null,
                Instant.now(),
                null,
                Map.of("feature", feature));
    }

    public ApiErrorDto error() {
        return error;
    }
}
