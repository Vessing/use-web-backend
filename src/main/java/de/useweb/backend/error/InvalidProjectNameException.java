package de.useweb.backend.error;

import java.time.Instant;
import java.util.Map;

import de.useweb.backend.api.dto.error.ApiErrorDto;

public class InvalidProjectNameException extends RuntimeException {

    private final ApiErrorDto error;

    public InvalidProjectNameException(String message) {
        super(message);
        this.error = new ApiErrorDto(
                "INVALID_PROJECT_NAME",
                message,
                "Bitte gib einen Projektnamen ein.",
                null,
                Instant.now(),
                null,
                Map.of("field", "name"));
    }

    public ApiErrorDto error() {
        return error;
    }
}
