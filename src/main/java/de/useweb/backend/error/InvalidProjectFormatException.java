package de.useweb.backend.error;

import java.time.Instant;
import java.util.Map;

import de.useweb.backend.api.dto.error.ApiErrorDto;

public class InvalidProjectFormatException extends RuntimeException {

    private final ApiErrorDto error;

    public InvalidProjectFormatException(String message) {
        this(message, Map.of());
    }

    public InvalidProjectFormatException(String message, Map<String, Object> details) {
        super(message);
        this.error = new ApiErrorDto(
                "INVALID_PROJECT_FORMAT",
                message,
                "Die Datei ist kein gueltiges USE-Web-Projekt.",
                null,
                Instant.now(),
                null,
                details);
    }

    public InvalidProjectFormatException(String message, Throwable cause) {
        super(message, cause);
        this.error = new ApiErrorDto(
                "INVALID_PROJECT_FORMAT",
                message,
                "Die Datei ist kein gueltiges USE-Web-Projekt.",
                null,
                Instant.now(),
                null,
                Map.of("cause", cause.getClass().getSimpleName()));
    }

    public ApiErrorDto error() {
        return error;
    }
}
