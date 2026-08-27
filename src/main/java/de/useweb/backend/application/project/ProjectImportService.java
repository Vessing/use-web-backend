package de.useweb.backend.application.project;

import java.util.List;

import org.springframework.stereotype.Service;

import de.useweb.backend.api.dto.project.ImportProjectRequestDto;
import de.useweb.backend.api.dto.project.ImportProjectResponseDto;
import de.useweb.backend.api.mapper.ProjectDtoMapper;
import de.useweb.backend.domain.project.Project;
import de.useweb.backend.error.FeatureNotAvailableException;
import de.useweb.backend.error.InvalidProjectFormatException;
import de.useweb.backend.persistence.json.ProjectJsonSerializer;

@Service
public class ProjectImportService {

    private final ProjectService projectService;
    private final ProjectJsonSerializer projectJsonSerializer;

    public ProjectImportService(ProjectService projectService, ProjectJsonSerializer projectJsonSerializer) {
        this.projectService = projectService;
        this.projectJsonSerializer = projectJsonSerializer;
    }

    public ImportProjectResponseDto importProject(ImportProjectRequestDto request) {
        if (request == null || request.format() == null || request.format().isBlank()) {
            throw new InvalidProjectFormatException("Importformat fehlt.");
        }
        if ("json".equalsIgnoreCase(request.format())) {
            return importJson(request.content());
        }
        if ("use".equalsIgnoreCase(request.format())) {
            throw new FeatureNotAvailableException(".use Import");
        }
        throw new InvalidProjectFormatException("Importformat wird nicht unterstuetzt.");
    }

    public ImportProjectResponseDto importJson(String json) {
        Project importedProject = projectJsonSerializer.deserialize(json);
        Project savedProject = projectService.saveProject(importedProject);
        return new ImportProjectResponseDto(
                "IMPORTED",
                ProjectDtoMapper.toDto(savedProject),
                List.of());
    }

    public void importUsePlaceholder() {
        throw new FeatureNotAvailableException(".use Import");
    }
}
