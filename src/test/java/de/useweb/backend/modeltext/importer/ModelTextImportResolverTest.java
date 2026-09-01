package de.useweb.backend.modeltext.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.useweb.backend.modeltext.parser.ModelTextParser;

class ModelTextImportResolverTest {

    private final ModelTextImportResolver resolver = new ModelTextImportResolver(new ModelTextParser());

    @Test
    void resolvesRelativeSelectiveAndTransitiveImportsWithProvenance() {
        var result = resolver.resolve("models/main.use", """
                import { Member } from "domain/members.use"
                model Main
                class Application end
                """, Map.of(
                "models/domain/members.use", """
                        import Date from "../shared/dates.use"
                        model Members
                        class Member end
                        class InternalAudit end
                        """,
                "models/shared/dates.use", """
                        model Dates
                        dataType Date
                        end
                        dataType Time
                        end
                        """));

        assertThat(result.model().diagnostics()).isEmpty();
        assertThat(result.model().classes()).extracting(type -> type.name())
                .containsExactly("Member", "Application");
        assertThat(result.model().dataTypes()).extracting(type -> type.name()).containsExactly("Date");
        assertThat(result.provenance()).hasSize(3);
        assertThat(result.provenance()).extracting(source -> source.sourcePath())
                .containsExactly("models/main.use", "models/domain/members.use", "models/shared/dates.use");
        assertThat(result.provenance()).allSatisfy(source -> assertThat(source.sha256()).hasSize(64));
        assertThat(result.provenance().get(2).depth()).isEqualTo(2);
    }

    @Test
    void reportsCyclesWithoutRecursingForever() {
        var result = resolver.resolve("a.use", """
                import * from "b.use"
                model A
                class A end
                """, Map.of("b.use", """
                import * from "a.use"
                model B
                class B end
                """));

        assertThat(result.model().diagnostics()).extracting(problem -> problem.code()).contains("IMPORT_CYCLE");
        assertThat(result.model().classes()).extracting(type -> type.name()).contains("A", "B");
    }

    @Test
    void rejectsMissingSourcesAndPathsOutsideBundle() {
        var missing = resolver.resolve("main.use", """
                import * from "missing.use"
                model Main
                class Main end
                """, Map.of());
        var escaping = resolver.resolve("models/main.use", """
                import * from "../../secret.use"
                model Main
                class Main end
                """, Map.of());
        var absoluteEntry = resolver.resolve("C:/private/main.use", "model Main", Map.of());

        assertThat(missing.model().diagnostics()).extracting(problem -> problem.code())
                .contains("IMPORT_SOURCE_NOT_FOUND");
        assertThat(escaping.model().diagnostics()).extracting(problem -> problem.code())
                .contains("UNSAFE_IMPORT_PATH");
        assertThat(absoluteEntry.model().diagnostics()).extracting(problem -> problem.code())
                .contains("UNSAFE_IMPORT_PATH");
    }

    @Test
    void resolvesFilesOnlyInsideConfiguredRoot(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("domain"));
        Files.writeString(root.resolve("main.use"), """
                import * from "domain/types.use"
                model Main
                class Main end
                """);
        Files.writeString(root.resolve("domain/types.use"), """
                model Types
                class Imported end
                """);

        var result = resolver.resolve(root, Path.of("main.use"));

        assertThat(result.model().diagnostics()).isEmpty();
        assertThat(result.model().classes()).extracting(type -> type.name())
                .containsExactly("Imported", "Main");
        assertThat(result.provenance()).extracting(source -> source.sourcePath())
                .containsExactly("main.use", "domain/types.use");
    }

    @Test
    void inventoriesAllNonStateMachineExamples() throws IOException {
        Path examples = Path.of("..", "use", "use-core", "src", "main", "resources", "examples")
                .toAbsolutePath().normalize();
        List<Path> corpus;
        try (var paths = Files.walk(examples)) {
            corpus = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".use"))
                    .filter(path -> !path.toString().replace('\\', '/').contains("/StateMachines/"))
                    .sorted()
                    .toList();
        }

        assertThat(corpus).hasSize(84);
        List<String> rejected = new java.util.ArrayList<>();
        for (Path source : corpus) {
            var result = resolver.resolve(examples, examples.relativize(source));
            assertThat(result.provenance()).as(source.toString()).isNotEmpty();
            assertThat(result.model().diagnostics()).as(source.toString())
                    .noneMatch(problem -> List.of("IMPORT_SOURCE_NOT_FOUND", "UNSAFE_IMPORT_PATH", "IMPORT_CYCLE")
                            .contains(problem.code()));
            result.model().diagnostics().stream()
                    .filter(problem -> "ERROR".equals(problem.severity()))
                    .forEach(problem -> rejected.add(examples.relativize(source) + ": " + problem.code()
                            + " - " + problem.message()));
        }
        assertThat(rejected).isEmpty();
    }
}
