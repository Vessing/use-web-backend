package de.useweb.backend.error;

import java.time.Instant;
import java.util.Map;

import de.useweb.backend.api.dto.error.ApiErrorDto;

public class CommandException extends RuntimeException {
    private final ApiErrorDto error;
    private final int status;

    public CommandException(int status, String code, String message, String userMessage, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.error = new ApiErrorDto(code, message, userMessage, null, Instant.now(), null,
                details == null ? Map.of() : Map.copyOf(details));
    }

    public ApiErrorDto error() { return error; }
    public int status() { return status; }
}
