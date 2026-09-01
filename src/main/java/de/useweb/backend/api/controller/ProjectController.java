package de.useweb.backend.api.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.useweb.backend.api.dto.modeltext.ApplyModelTextRequestDto;
import de.useweb.backend.api.dto.modeltext.ApplyModelTextResponseDto;
import de.useweb.backend.api.dto.modeltext.ModelTextDto;
import de.useweb.backend.api.dto.project.CreateProjectRequestDto;
import de.useweb.backend.api.dto.project.ImportProjectRequestDto;
import de.useweb.backend.api.dto.project.ImportProjectResponseDto;
import de.useweb.backend.api.dto.project.ProjectDto;
import de.useweb.backend.api.dto.project.ProjectSummaryDto;
import de.useweb.backend.api.dto.projection.ProjectReadModelDto;
import de.useweb.backend.api.mapper.ProjectDtoMapper;
import de.useweb.backend.application.modeltext.ModelTextApplicationService;
import de.useweb.backend.application.project.DashboardProjectService;
import de.useweb.backend.application.project.ProjectImportService;
import de.useweb.backend.application.project.ProjectService;
import de.useweb.backend.application.project.RecentProjectService;
import de.useweb.backend.application.projection.ProjectReadModelService;
import de.useweb.backend.domain.project.ProjectId;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final DashboardProjectService dashboardProjectService;
    private final ProjectService projectService;
    private final ProjectImportService projectImportService;
    private final RecentProjectService recentProjectService;
    private final ModelTextApplicationService modelTextApplicationService;
    private final ProjectReadModelService projectReadModelService;

    public ProjectController(
            DashboardProjectService dashboardProjectService,
            ProjectService projectService,
            ProjectImportService projectImportService,
            RecentProjectService recentProjectService,
            ModelTextApplicationService modelTextApplicationService,
            ProjectReadModelService projectReadModelService) {
        this.dashboardProjectService = dashboardProjectService;
        this.projectService = projectService;
        this.projectImportService = projectImportService;
        this.recentProjectService = recentProjectService;
        this.modelTextApplicationService = modelTextApplicationService;
        this.projectReadModelService = projectReadModelService;
    }

    @PostMapping
    public ResponseEntity<ProjectDto> createProject(@RequestBody(required = false) CreateProjectRequestDto request) {
        ProjectDto project = dashboardProjectService.startProject(request);
        return ResponseEntity
                .created(URI.create("/api/v1/projects/" + project.project().id()))
                .body(project);
    }

    @GetMapping
    public List<ProjectSummaryDto> listProjects(@RequestParam(required = false) String search) {
        return projectService.listProjectSummaries(search);
    }

    @GetMapping("/recent")
    public List<ProjectSummaryDto> recentProjects(@RequestParam(defaultValue = "5") int limit) {
        return recentProjectService.recentProjects(limit);
    }

    @PostMapping("/import")
    public ImportProjectResponseDto importProject(@RequestBody ImportProjectRequestDto request) {
        return projectImportService.importProject(request);
    }

    @GetMapping("/{projectId}")
    public ProjectDto loadProject(@PathVariable String projectId) {
        return ProjectDtoMapper.toDto(projectService.loadProject(new ProjectId(projectId)));
    }

    @GetMapping("/{projectId}/read-model")
    public ProjectReadModelDto readModel(@PathVariable String projectId) {
        return projectReadModelService.get(new ProjectId(projectId));
    }

    @PutMapping("/{projectId}")
    public ProjectDto saveProject(@PathVariable String projectId, @RequestBody ProjectDto project) {
        return ProjectDtoMapper.toDto(projectService.replaceProject(new ProjectId(projectId), ProjectDtoMapper.toDomain(project)));
    }

    @GetMapping("/{projectId}/model-text")
    public ModelTextDto getModelText(@PathVariable String projectId) {
        return modelTextApplicationService.getModelText(new ProjectId(projectId));
    }

    @PostMapping("/{projectId}/model-text/apply")
    public ApplyModelTextResponseDto applyModelText(
            @PathVariable String projectId,
            @RequestBody ApplyModelTextRequestDto request) {
        return modelTextApplicationService.applyModelText(new ProjectId(projectId), request);
    }

    @GetMapping(value = "/{projectId}/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> exportProject(@PathVariable String projectId) {
        return ResponseEntity.ok(projectService.exportProject(new ProjectId(projectId)));
    }
}
