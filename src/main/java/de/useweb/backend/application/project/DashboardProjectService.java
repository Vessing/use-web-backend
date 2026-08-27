package de.useweb.backend.application.project;

import org.springframework.stereotype.Service;

import de.useweb.backend.api.dto.project.CreateProjectRequestDto;
import de.useweb.backend.api.dto.project.ProjectDto;
import de.useweb.backend.api.mapper.ProjectDtoMapper;

@Service
public class DashboardProjectService {

    private final ProjectService projectService;

    public DashboardProjectService(ProjectService projectService) {
        this.projectService = projectService;
    }

    public ProjectDto startProject(CreateProjectRequestDto request) {
        String name = request == null ? null : request.name();
        String description = request == null ? null : request.description();
        return ProjectDtoMapper.toDto(projectService.createProject(name, description));
    }
}
