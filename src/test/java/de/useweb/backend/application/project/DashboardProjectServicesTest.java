package de.useweb.backend.application.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.useweb.backend.api.dto.project.CreateProjectRequestDto;
import de.useweb.backend.api.dto.project.ImportProjectRequestDto;
import de.useweb.backend.api.dto.project.ImportProjectResponseDto;
import de.useweb.backend.api.dto.project.ProjectDto;
import de.useweb.backend.api.dto.project.ProjectSummaryDto;
import de.useweb.backend.domain.layout.LayoutInformation;
import de.useweb.backend.domain.project.Project;
import de.useweb.backend.domain.project.ProjectId;
import de.useweb.backend.domain.project.ProjectMetadata;
import de.useweb.backend.domain.snapshot.ObjectModel;
import de.useweb.backend.domain.snapshot.ObjectModelId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlModelId;
import de.useweb.backend.error.FeatureNotAvailableException;
import de.useweb.backend.error.InvalidProjectNameException;
import de.useweb.backend.error.InvalidProjectFormatException;
import de.useweb.backend.persistence.json.ProjectJsonFormat;
import de.useweb.backend.persistence.json.ProjectJsonSerializer;
import de.useweb.backend.persistence.project.InMemoryProjectRepository;

class DashboardProjectServicesTest {

    private final InMemoryProjectRepository repository = new InMemoryProjectRepository();
    private final ProjectJsonSerializer serializer = new ProjectJsonSerializer();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-16T12:00:00Z"), ZoneOffset.UTC);
    private final ProjectService projectService = new ProjectService(repository, serializer, clock);

    @Test
    void dashboardStartProjectReturnsProjectDtoForClassDiagramNavigation() {
        DashboardProjectService dashboardProjectService = new DashboardProjectService(projectService);

        ProjectDto project = dashboardProjectService.startProject(
                new CreateProjectRequestDto("  Dashboard Model  ", "Started from dashboard", "empty"));

        assertThat(project.formatVersion()).isEqualTo(ProjectJsonFormat.CURRENT_FORMAT_VERSION);
        assertThat(project.project().name()).isEqualTo("Dashboard Model");
        assertThat(project.umlModel().classes()).isEmpty();
        assertThat(project.objectModel().objects()).isEmpty();
        assertThat(repository.existsById(new ProjectId(project.project().id()))).isTrue();
    }

    @Test
    void dashboardStartProjectRejectsMissingProjectName() {
        DashboardProjectService dashboardProjectService = new DashboardProjectService(projectService);

        assertThatThrownBy(() -> dashboardProjectService.startProject(new CreateProjectRequestDto(" ", null, "empty")))
                .isInstanceOf(InvalidProjectNameException.class)
                .satisfies(exception -> {
                    InvalidProjectNameException nameException = (InvalidProjectNameException) exception;
                    assertThat(nameException.error().code()).isEqualTo("INVALID_PROJECT_NAME");
                    assertThat(nameException.error().userMessage()).isEqualTo("Bitte gib einen Projektnamen ein.");
                });
    }

    @Test
    void recentProjectsReturnsSummariesSortedByUpdatedAtDescending() {
        repository.save(project("project-university", "University System", Instant.parse("2026-07-15T09:00:00Z")));
        repository.save(project("project-hotel", "Hotel Management", Instant.parse("2026-07-14T16:15:00Z")));
        repository.save(project("project-bank-atm", "Bank ATM", Instant.parse("2026-07-13T11:45:00Z")));

        RecentProjectService recentProjectService = new RecentProjectService(repository);
        List<ProjectSummaryDto> recentProjects = recentProjectService.recentProjects(2);

        assertThat(recentProjects)
                .extracting(ProjectSummaryDto::name)
                .containsExactly("University System", "Hotel Management");
    }

    @Test
    void projectListReturnsSummariesSortedByUpdatedAtDescending() {
        repository.save(project("project-bank-atm", "Bank ATM", Instant.parse("2026-07-13T11:45:00Z")));
        repository.save(project("project-university", "University System", Instant.parse("2026-07-15T09:00:00Z")));
        repository.save(project("project-hotel", "Hotel Management", Instant.parse("2026-07-14T16:15:00Z")));

        List<ProjectSummaryDto> projects = projectService.listProjectSummaries(null);

        assertThat(projects)
                .extracting(ProjectSummaryDto::name)
                .containsExactly("University System", "Hotel Management", "Bank ATM");
        assertThat(projects.getFirst().id()).isEqualTo("project-university");
        assertThat(projectService.loadProject(new ProjectId(projects.getFirst().id())).metadata().name())
                .isEqualTo("University System");
    }

    @Test
    void projectListSearchFiltersByNameOrDescription() {
        repository.save(project(
                "project-library",
                "Library Model",
                "Books and borrowing",
                Instant.parse("2026-07-15T09:00:00Z")));
        repository.save(project(
                "project-hotel",
                "Hotel Management",
                "Rooms and bookings",
                Instant.parse("2026-07-14T16:15:00Z")));

        assertThat(projectService.listProjectSummaries("library"))
                .extracting(ProjectSummaryDto::id)
                .containsExactly("project-library");
        assertThat(projectService.listProjectSummaries("rooms"))
                .extracting(ProjectSummaryDto::id)
                .containsExactly("project-hotel");
    }

    @Test
    void jsonImportStoresProjectAndReturnsImportedResponse() {
        ProjectImportService importService = new ProjectImportService(projectService, serializer);
        String json = serializer.serialize(project("project-imported", "Imported Project", Instant.parse("2026-07-15T10:00:00Z")));

        ImportProjectResponseDto response = importService.importProject(new ImportProjectRequestDto("json", json));

        assertThat(response.status()).isEqualTo("IMPORTED");
        assertThat(response.project().project().id()).isEqualTo("project-imported");
        assertThat(response.diagnostics()).isEmpty();
        assertThat(projectService.loadProject(new ProjectId("project-imported")).metadata().name()).isEqualTo("Imported Project");
    }

    @Test
    void invalidJsonImportReturnsInvalidProjectFormat() {
        ProjectImportService importService = new ProjectImportService(projectService, serializer);

        assertThatThrownBy(() -> importService.importProject(new ImportProjectRequestDto("json", "{ broken json")))
                .isInstanceOf(InvalidProjectFormatException.class)
                .satisfies(exception -> {
                    InvalidProjectFormatException formatException = (InvalidProjectFormatException) exception;
                    assertThat(formatException.error().code()).isEqualTo("INVALID_PROJECT_FORMAT");
                });
    }

    @Test
    void useImportIsExplicitlyMarkedAsUnavailableForMvp() {
        ProjectImportService importService = new ProjectImportService(projectService, serializer);

        assertThatThrownBy(() -> importService.importProject(new ImportProjectRequestDto("use", "model Library")))
                .isInstanceOf(FeatureNotAvailableException.class)
                .satisfies(exception -> {
                    FeatureNotAvailableException featureException = (FeatureNotAvailableException) exception;
                    assertThat(featureException.error().code()).isEqualTo("FEATURE_NOT_AVAILABLE");
                    assertThat(featureException.error().details()).containsEntry("feature", ".use Import");
                });
    }

    private static Project project(String id, String name, Instant updatedAt) {
        return project(id, name, null, updatedAt);
    }

    private static Project project(String id, String name, String description, Instant updatedAt) {
        return new Project(
                new ProjectId(id),
                new ProjectMetadata(
                        name,
                        description,
                        ProjectJsonFormat.CURRENT_FORMAT_VERSION,
                        Instant.parse("2026-07-01T00:00:00Z"),
                        updatedAt),
                new UmlModel(new UmlModelId("uml-" + id), "Class Model", List.of(), List.of(), List.of()),
                new ObjectModel(new ObjectModelId("snapshot-" + id), "Current Snapshot", List.of(), List.of()),
                LayoutInformation.empty());
    }
}
