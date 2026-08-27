package de.useweb.backend.api.dto.modeltext;

import java.util.List;

import de.useweb.backend.api.dto.ocl.OclDiagnosticDto;
import de.useweb.backend.api.dto.project.ProjectDto;

public record ApplyModelTextResponseDto(
        boolean success,
        String status,
        ProjectDto project,
        ModelTextDto modelText,
        List<OclDiagnosticDto> diagnostics,
        List<String> changedElementIds) {
}
