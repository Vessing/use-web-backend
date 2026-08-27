package de.useweb.backend.api.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.useweb.backend.api.dto.validation.ValidationRequestDto;
import de.useweb.backend.api.dto.validation.ValidationResultDto;
import de.useweb.backend.api.mapper.ProjectDtoMapper;
import de.useweb.backend.application.project.ProjectService;
import de.useweb.backend.domain.project.Project;
import de.useweb.backend.domain.project.ProjectId;
import de.useweb.backend.validation.service.ValidationService;

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class ValidationController {

    private final ProjectService projectService;
    private final ValidationService validationService;

    public ValidationController(ProjectService projectService, ValidationService validationService) {
        this.projectService = projectService;
        this.validationService = validationService;
    }

    @PostMapping("/validate")
    public ValidationResultDto validate(@PathVariable String projectId, @RequestBody(required = false) ValidationRequestDto request) {
        Project project = projectService.loadProject(new ProjectId(projectId));
        return ProjectDtoMapper.toDto(validationService.validate(project));
    }
}
