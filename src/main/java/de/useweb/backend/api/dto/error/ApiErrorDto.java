package de.useweb.backend.api.dto.error;

import java.time.Instant;
import java.util.Map;

public record ApiErrorDto(
        String kind,
        String code,
        String severity,
        String message,
        String userMessage,
        String technicalMessage,
        String path,
        Instant timestamp,
        String requestId,
        Map<String, Object> details
) {
    public ApiErrorDto(
            String code,
            String message,
            String userMessage,
            String path,
            Instant timestamp,
            String requestId,
            Map<String, Object> details) {
        this("API_ERROR", code, "ERROR", message, userMessage, message, path, timestamp, requestId, details);
    }
}
