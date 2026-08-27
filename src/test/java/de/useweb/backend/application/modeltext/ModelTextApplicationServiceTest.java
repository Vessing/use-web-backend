package de.useweb.backend.application.modeltext;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import de.useweb.backend.api.dto.modeltext.ApplyModelTextRequestDto;
import de.useweb.backend.api.dto.modeltext.ApplyModelTextResponseDto;
import de.useweb.backend.application.ocl.OclParseService;
import de.useweb.backend.application.project.ProjectService;
import de.useweb.backend.domain.project.Project;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlInvariantId;
import de.useweb.backend.modeltext.parser.ModelTextParser;
import de.useweb.backend.persistence.json.ProjectJsonSerializer;
import de.useweb.backend.persistence.project.InMemoryProjectRepository;

class ModelTextApplicationServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-23T08:00:00Z"), ZoneOffset.UTC);
    private final InMemoryProjectRepository repository = new InMemoryProjectRepository();
    private final ProjectService projectService = new ProjectService(repository, new ProjectJsonSerializer(), clock);
    private final ModelTextApplicationService service = new ModelTextApplicationService(
            projectService,
            new ModelTextParser(),
            new OclParseService(),
            clock);

    @Test
    void appliesLibraryModelTextToUmlModelAndStoresText() {
        Project project = projectService.createProject("Library", null);

        ApplyModelTextResponseDto response = service.applyModelText(project.id(), new ApplyModelTextRequestDto(
                libraryModelText(),
                "USE_MODEL_TEXT",
                "REPLACE_UML_MODEL",
                true,
                "library.use",
                "use",
                "open-existing",
                null));

        Project saved = projectService.loadProject(project.id());
        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo("APPLIED");
        assertThat(response.diagnostics()).isEmpty();
        assertThat(response.changedElementIds()).contains("class-user", "class-book", "assoc-borrows", "inv-max-books");
        assertThat(saved.modelText().text()).contains("context User");
        assertThat(saved.modelText().text()).contains("inv maxBooks:");
        assertThat(saved.umlModel().findClass(new UmlClassId("class-user"))).isPresent();
        assertThat(saved.umlModel().findClass(new UmlClassId("class-book"))).isPresent();
        assertThat(saved.umlModel().associations()).singleElement()
                .satisfies(association -> assertThat(association.name()).isEqualTo("Borrows"));
        assertThat(saved.umlModel().findInvariant(new UmlInvariantId("inv-max-books")))
                .get()
                .satisfies(invariant -> assertThat(invariant.expression().text()).isEqualTo("self.books <= 5"));
    }

    @Test
    void returnsUnsupportedSyntaxDiagnosticWithoutApplyingEmptyImportText() {
        Project project = projectService.createProject("Import", null);

        ApplyModelTextResponseDto response = service.applyModelText(project.id(), new ApplyModelTextRequestDto(
                "import other.use",
                "USE_MODEL_TEXT",
                "REPLACE_UML_MODEL",
                true,
                "unsupported.use",
                "use",
                "open-existing",
                null));

        Project saved = projectService.loadProject(project.id());
        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo("NOT_APPLIED");
        assertThat(response.changedElementIds()).isEmpty();
        assertThat(response.diagnostics()).singleElement()
                .satisfies(diagnostic -> {
                    assertThat(diagnostic.code()).isEqualTo("UNSUPPORTED_SYNTAX");
                    assertThat(diagnostic.severity()).isEqualTo("WARNING");
                });
        assertThat(saved.modelText().text()).isEqualTo("import other.use");
        assertThat(saved.umlModel().classes()).isEmpty();
    }

    private String libraryModelText() {
        return """
                model Library

                class User
                attributes
                  name : String
                  books : Integer
                operations
                  canBorrow(count : Integer) : Boolean
                end

                class Book
                attributes
                  title : String
                  available : Boolean
                end

                association Borrows between
                  User[1] role borrower
                  Book[0..*] role borrowedBooks
                end

                constraints
                context User
                  inv maxBooks:
                  self.books <= 5
                """;
    }
}
