package de.useweb.backend.application.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import de.useweb.backend.api.mapper.ProjectDtoMapper;
import de.useweb.backend.domain.project.Project;
import de.useweb.backend.persistence.project.InMemoryProjectRepository;

class ExampleProjectSeederTest {

    private final InMemoryProjectRepository repository = new InMemoryProjectRepository();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-20T08:30:00Z"), ZoneOffset.UTC);
    private final ExampleProjectSeeder seeder = new ExampleProjectSeeder(repository, clock);

    @Test
    void seedsUniversitySystemExampleWithModelAndSnapshotContent() {
        seeder.seedExampleProjects();

        Project project = repository.findById(ExampleProjectSeeder.UNIVERSITY_SYSTEM_PROJECT_ID).orElseThrow();

        assertThat(project.metadata().name()).isEqualTo("University System");
        assertThat(ProjectDtoMapper.toSummaryDto(project).sourceFormat()).isEqualTo("example");
        assertThat(project.modelText().sourceOrigin()).isEqualTo("example-project");
        assertThat(project.umlModel().classes()).extracting("name")
                .containsExactly("Student", "Course", "Professor");
        assertThat(project.umlModel().classes())
                .allSatisfy(umlClass -> assertThat(umlClass.attributes()).isNotEmpty())
                .allSatisfy(umlClass -> assertThat(umlClass.operations()).isNotEmpty());
        assertThat(project.umlModel().associations()).extracting("name")
                .containsExactly("Enrollment", "Teaches");
        assertThat(project.umlModel().invariants()).extracting("name")
                .containsExactly("positiveSemester", "maxCredits");
        assertThat(project.objectModel().objects()).extracting("name")
                .containsExactly("alice", "bob", "ocl", "uml", "profSmith");
        assertThat(project.objectModel().links()).hasSize(4);
        assertThat(project.layout().classDiagram().nodes()).hasSize(3);
        assertThat(project.layout().objectDiagram().nodes()).hasSize(5);
    }

    @Test
    void doesNotOverwriteExistingUniversitySystemProject() {
        Project firstSeededProject = seeder.universitySystemProject(Instant.parse("2026-07-19T08:30:00Z"));
        repository.save(firstSeededProject);

        seeder.seedExampleProjects();

        Project project = repository.findById(ExampleProjectSeeder.UNIVERSITY_SYSTEM_PROJECT_ID).orElseThrow();

        assertThat(project.metadata().createdAt()).isEqualTo(Instant.parse("2026-07-19T08:30:00Z"));
    }
}
