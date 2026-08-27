package de.useweb.backend.error;

import java.time.Instant;
import java.util.Map;

import de.useweb.backend.api.dto.error.ApiErrorDto;

public class ObjectModelException extends RuntimeException {

    private final ApiErrorDto error;

    public ObjectModelException(String code, String message, String userMessage, Map<String, Object> details) {
        super(message);
        this.error = new ApiErrorDto(
                code,
                message,
                userMessage,
                null,
                Instant.now(),
                null,
                details == null ? Map.of() : Map.copyOf(details));
    }

    public ApiErrorDto error() {
        return error;
    }
}
