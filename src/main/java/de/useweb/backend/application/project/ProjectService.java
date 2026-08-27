package de.useweb.backend.application.project;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.useweb.backend.domain.layout.LayoutInformation;
import de.useweb.backend.domain.project.Project;
import de.useweb.backend.domain.project.ProjectId;
import de.useweb.backend.domain.project.ProjectMetadata;
import de.useweb.backend.domain.snapshot.ObjectModel;
import de.useweb.backend.domain.snapshot.ObjectModelId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlModelId;
import de.useweb.backend.api.dto.project.ProjectSummaryDto;
import de.useweb.backend.api.mapper.ProjectDtoMapper;
import de.useweb.backend.error.InvalidProjectNameException;
import de.useweb.backend.error.ProjectNotFoundException;
import de.useweb.backend.persistence.json.ProjectJsonFormat;
import de.useweb.backend.persistence.json.ProjectJsonSerializer;
import de.useweb.backend.persistence.project.ProjectRepository;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectJsonSerializer projectJsonSerializer;
    private final Clock clock;

    @Autowired
    public ProjectService(ProjectRepository projectRepository, ProjectJsonSerializer projectJsonSerializer) {
        this(projectRepository, projectJsonSerializer, Clock.systemUTC());
    }

    public ProjectService(ProjectRepository projectRepository, ProjectJsonSerializer projectJsonSerializer, Clock clock) {
        this.projectRepository = projectRepository;
        this.projectJsonSerializer = projectJsonSerializer;
        this.clock = clock;
    }

    public Project createProject(String name, String description) {
        String projectName = normalizeProjectName(name);
        ProjectId projectId = new ProjectId("project-" + UUID.randomUUID());
        Instant now = Instant.now(clock);
        Project project = new Project(
                projectId,
                new ProjectMetadata(
                        projectName,
                        description,
                        ProjectJsonFormat.CURRENT_FORMAT_VERSION,
                        now,
                        now),
                new UmlModel(new UmlModelId("uml-" + projectId.value()), "Class Model", List.of(), List.of(), List.of()),
                new ObjectModel(new ObjectModelId("snapshot-" + projectId.value()), "Current Snapshot", List.of(), List.of()),
                LayoutInformation.empty());

        return projectRepository.save(project);
    }

    private String normalizeProjectName(String name) {
        if (name == null) {
            throw new InvalidProjectNameException("Project name is required.");
        }
        String normalizedName = name.trim();
        if (normalizedName.isBlank()) {
            throw new InvalidProjectNameException("Project name is required.");
        }
        return normalizedName;
    }

    public Project loadProject(ProjectId projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
    }

    public List<ProjectSummaryDto> listProjectSummaries(String search) {
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return projectRepository.findAll().stream()
                .filter(project -> matchesSearch(project, normalizedSearch))
                .sorted(Comparator.comparing(project -> project.metadata().updatedAt(), Comparator.reverseOrder()))
                .map(ProjectDtoMapper::toSummaryDto)
                .toList();
    }

    public Project saveProject(Project project) {
        Project updatedProject = touch(project);
        return projectRepository.save(updatedProject);
    }

    public Project replaceProject(ProjectId projectId, Project replacement) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }
        if (!projectId.equals(replacement.id())) {
            throw new IllegalArgumentException("Replacement project id must match target project id");
        }
        return saveProject(replacement);
    }

    public String exportProject(ProjectId projectId) {
        return projectJsonSerializer.serialize(loadProject(projectId));
    }

    private boolean matchesSearch(Project project, String normalizedSearch) {
        if (normalizedSearch.isBlank()) {
            return true;
        }
        return containsIgnoreCase(project.metadata().name(), normalizedSearch)
                || containsIgnoreCase(project.metadata().description(), normalizedSearch);
    }

    private boolean containsIgnoreCase(String value, String normalizedSearch) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedSearch);
    }

    private Project touch(Project project) {
        Instant now = Instant.now(clock);
        return new Project(
                project.id(),
                new ProjectMetadata(
                        project.metadata().name(),
                        project.metadata().description(),
                        project.metadata().formatVersion(),
                        project.metadata().createdAt(),
                        now),
                project.modelText(),
                project.umlModel(),
                project.objectModel(),
                project.layout());
    }
}
