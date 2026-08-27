package de.useweb.backend.application.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import de.useweb.backend.domain.project.Project;
import de.useweb.backend.domain.project.ProjectId;
import de.useweb.backend.error.InvalidProjectNameException;
import de.useweb.backend.error.ProjectNotFoundException;
import de.useweb.backend.persistence.json.ProjectJsonSerializer;
import de.useweb.backend.persistence.project.InMemoryProjectRepository;

class ProjectServiceTest {

    private final InMemoryProjectRepository repository = new InMemoryProjectRepository();
    private final ProjectJsonSerializer serializer = new ProjectJsonSerializer();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-16T12:00:00Z"), ZoneOffset.UTC);
    private final ProjectService projectService = new ProjectService(repository, serializer, clock);

    @Test
    void createProjectReturnsPersistedEmptyProjectForDashboardStartProject() {
        Project project = projectService.createProject("Library Model", "Created from dashboard");

        assertThat(project.id().value()).startsWith("project-");
        assertThat(project.metadata().name()).isEqualTo("Library Model");
        assertThat(project.metadata().formatVersion()).isEqualTo("0.1");
        assertThat(project.umlModel().classes()).isEmpty();
        assertThat(project.umlModel().associations()).isEmpty();
        assertThat(project.umlModel().invariants()).isEmpty();
        assertThat(project.objectModel().objects()).isEmpty();
        assertThat(project.objectModel().links()).isEmpty();
        assertThat(project.layout().classDiagram().nodes()).isEmpty();
        assertThat(projectService.loadProject(project.id())).isEqualTo(project);
    }

    @Test
    void createProjectTrimsDashboardProjectName() {
        Project project = projectService.createProject("  Library Model  ", "Created from dashboard");

        assertThat(project.metadata().name()).isEqualTo("Library Model");
    }

    @Test
    void createProjectRejectsMissingDashboardProjectName() {
        assertThatThrownBy(() -> projectService.createProject(null, "Created from dashboard"))
                .isInstanceOf(InvalidProjectNameException.class)
                .satisfies(exception -> {
                    InvalidProjectNameException nameException = (InvalidProjectNameException) exception;
                    assertThat(nameException.error().code()).isEqualTo("INVALID_PROJECT_NAME");
                    assertThat(nameException.error().details()).containsEntry("field", "name");
                });
    }

    @Test
    void createProjectRejectsBlankDashboardProjectName() {
        assertThatThrownBy(() -> projectService.createProject("   ", "Created from dashboard"))
                .isInstanceOf(InvalidProjectNameException.class)
                .satisfies(exception -> {
                    InvalidProjectNameException nameException = (InvalidProjectNameException) exception;
                    assertThat(nameException.error().code()).isEqualTo("INVALID_PROJECT_NAME");
                    assertThat(nameException.error().details()).containsEntry("field", "name");
                });
    }

    @Test
    void saveProjectPersistsProjectStateAndUpdatesTimestamp() {
        Project project = projectService.createProject("Library", null);

        Project saved = projectService.saveProject(project);

        assertThat(projectService.loadProject(project.id())).isEqualTo(saved);
        assertThat(saved.metadata().createdAt()).isEqualTo(Instant.parse("2026-07-16T12:00:00Z"));
        assertThat(saved.metadata().updatedAt()).isEqualTo(Instant.parse("2026-07-16T12:00:00Z"));
    }

    @Test
    void loadUnknownProjectReturnsStructuredProjectNotFoundError() {
        ProjectId missingId = new ProjectId("project-missing");

        assertThatThrownBy(() -> projectService.loadProject(missingId))
                .isInstanceOf(ProjectNotFoundException.class)
                .satisfies(exception -> {
                    ProjectNotFoundException notFound = (ProjectNotFoundException) exception;
                    assertThat(notFound.error().code()).isEqualTo("PROJECT_NOT_FOUND");
                    assertThat(notFound.error().details()).containsEntry("projectId", "project-missing");
                });
    }

    @Test
    void exportProjectReturnsCanonicalJsonProjectFormat() {
        Project project = projectService.createProject("Exported Model", "JSON export");

        String json = projectService.exportProject(project.id());

        assertThat(json).contains("\"formatVersion\" : \"0.1\"");
        assertThat(json).contains("\"name\" : \"Exported Model\"");
        assertThat(json).contains("\"umlModel\"");
        assertThat(json).contains("\"objectModel\"");
        assertThat(serializer.deserialize(json).id()).isEqualTo(project.id());
    }
}
