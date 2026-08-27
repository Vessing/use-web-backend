package de.useweb.backend.error;

import java.time.Instant;
import java.util.Map;

import de.useweb.backend.api.dto.error.ApiErrorDto;
import de.useweb.backend.domain.project.ProjectId;

public class ProjectNotFoundException extends RuntimeException {

    private final ApiErrorDto error;

    public ProjectNotFoundException(ProjectId projectId) {
        super("Project not found: " + projectId.value());
        this.error = new ApiErrorDto(
                "PROJECT_NOT_FOUND",
                "Project not found: " + projectId.value(),
                "Das Projekt konnte nicht gefunden werden.",
                null,
                Instant.now(),
                null,
                Map.of("projectId", projectId.value()));
    }

    public ApiErrorDto error() {
        return error;
    }
}
