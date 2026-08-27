package de.useweb.backend.ocl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import de.useweb.backend.domain.snapshot.ObjectInstance;
import de.useweb.backend.domain.snapshot.ObjectInstanceId;
import de.useweb.backend.domain.snapshot.ObjectModel;
import de.useweb.backend.domain.snapshot.ObjectModelId;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlModelId;
import de.useweb.backend.ocl.evaluation.EvaluationContext;
import de.useweb.backend.ocl.evaluation.OclEvaluator;
import de.useweb.backend.ocl.parser.OclParser;
import de.useweb.backend.ocl.typecheck.OclTypeChecker;
import de.useweb.backend.ocl.typecheck.TypeEnvironment;

@Tag("ocl-24-compliance")
class Ocl24ComplianceHarnessTest {

    private static final String CASE_RESOURCE = "/compliance/ocl-2.4-normative-cases.tsv";
    private static final String INVENTORY_RESOURCE = "/compliance/ocl-2.4-normative-inventory.csv";
    private static final String MATRIX_FILTER = "compliance.matrixId";
    private static final Set<String> PIPELINES = Set.of("SYNTAX", "TYPECHECK", "EVALUATION", "DIAGNOSTIC");

    private final OclParser parser = new OclParser();
    private final OclTypeChecker typeChecker = new OclTypeChecker();
    private final OclEvaluator evaluator = new OclEvaluator();
    private final Fixture fixture = fixture();

    @TestFactory
    Stream<DynamicTest> executesNormativeCasesByMatrixId() throws IOException {
        String matrixFilter = System.getProperty(MATRIX_FILTER, "").trim();
        List<Case> selected = readCases().stream()
                .filter(testCase -> matrixFilter.isEmpty() || testCase.matrixId().equals(matrixFilter))
                .toList();

        assertThat(selected)
                .as("normative cases selected by -D%s=%s", MATRIX_FILTER, matrixFilter)
                .isNotEmpty();

        return selected.stream().map(testCase -> DynamicTest.dynamicTest(
                testCase.matrixId() + " / " + testCase.id(),
                () -> execute(testCase)));
    }

    @Test
    void caseMetadataReferencesTheNormativeInventory() throws IOException {
        Set<String> inventoryIds = readInventoryIds();
        Set<String> caseIds = new HashSet<>();

        for (Case testCase : readCases()) {
            assertThat(caseIds.add(testCase.id())).as("unique case ID %s", testCase.id()).isTrue();
            assertThat(testCase.id()).matches("OCL24-CASE-[A-Z]+-[0-9]{3}");
            assertThat(inventoryIds).contains(testCase.inventoryId());
            assertThat(testCase.matrixId()).matches("CM-(OCL|LIB|CTX)-[0-9]{3}");
            assertThat(testCase.pipeline()).isIn(PIPELINES);
            assertThat(testCase.expression()).isNotBlank();
        }

        assertThat(readCases().stream().map(Case::pipeline).collect(java.util.stream.Collectors.toSet()))
                .containsExactlyInAnyOrderElementsOf(PIPELINES);
    }

    private void execute(Case testCase) {
        var parseResult = parser.parse(testCase.expression());

        if (testCase.pipeline().equals("SYNTAX")) {
            assertThat(parseResult.success()).isEqualTo(testCase.expectedSuccess());
            return;
        }

        if (!parseResult.success()) {
            assertDiagnostic(testCase, parseResult.diagnostics());
            return;
        }

        var typeResult = typeChecker.checkExpression(
                new TypeEnvironment(fixture.umlModel(), fixture.umlClass()), parseResult.ast());
        if (testCase.pipeline().equals("TYPECHECK")) {
            assertThat(typeResult.success()).isEqualTo(testCase.expectedSuccess());
            if (!testCase.expectedType().equals("-")) {
                assertThat(typeResult.resultType().displayName()).isEqualTo(testCase.expectedType());
            }
            return;
        }

        if (testCase.pipeline().equals("DIAGNOSTIC") && !typeResult.success()) {
            assertDiagnostic(testCase, typeResult.diagnostics());
            return;
        }

        assertThat(typeResult.success()).isTrue();
        var evaluationResult = evaluator.evaluate(parseResult.ast(),
                new EvaluationContext(fixture.umlModel(), fixture.objectModel(), fixture.self()));

        if (testCase.pipeline().equals("DIAGNOSTIC")) {
            assertDiagnostic(testCase, evaluationResult.diagnostics());
            return;
        }

        assertThat(evaluationResult.success()).isEqualTo(testCase.expectedSuccess());
        assertThat(evaluationResult.value().typeName()).isEqualTo(testCase.expectedType());
        assertThat(String.valueOf(evaluationResult.value().rawValue())).isEqualTo(testCase.expectedValue());
    }

    private void assertDiagnostic(Case testCase,
            List<de.useweb.backend.ocl.diagnostics.OclDiagnostic> diagnostics) {
        assertThat(testCase.expectedSuccess()).isFalse();
        assertThat(diagnostics).anyMatch(diagnostic -> diagnostic.code().equals(testCase.expectedDiagnosticCode()));
    }

    private List<Case> readCases() throws IOException {
        try (BufferedReader reader = resourceReader(CASE_RESOURCE)) {
            List<String> lines = reader.lines().filter(line -> !line.isBlank()).toList();
            assertThat(lines.getFirst()).isEqualTo(
                    "id\tinventoryId\tmatrixId\tpipeline\texpression\texpectedSuccess\texpectedType\texpectedValue\texpectedDiagnosticCode");
            return lines.stream().skip(1).map(this::parseCase).toList();
        }
    }

    private Case parseCase(String line) {
        String[] columns = line.split("\t", -1);
        assertThat(columns).as("nine TSV columns in %s", line).hasSize(9);
        return new Case(columns[0], columns[1], columns[2], columns[3], columns[4],
                Boolean.parseBoolean(columns[5]), columns[6], columns[7], columns[8]);
    }

    private Set<String> readInventoryIds() throws IOException {
        try (BufferedReader reader = resourceReader(INVENTORY_RESOURCE)) {
            return reader.lines().skip(1).filter(line -> !line.isBlank())
                    .map(line -> line.split(",", 2)[0])
                    .collect(java.util.stream.Collectors.toSet());
        }
    }

    private BufferedReader resourceReader(String resource) {
        InputStream stream = getClass().getResourceAsStream(resource);
        assertThat(stream).as("resource %s", resource).isNotNull();
        return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    private static Fixture fixture() {
        UmlClassId classId = new UmlClassId("class-compliance-context");
        UmlClass umlClass = new UmlClass(classId, "ComplianceContext", List.of(), List.of());
        UmlModel umlModel = new UmlModel(new UmlModelId("model-compliance"), "OCL 2.4 Compliance",
                List.of(umlClass), List.of(), List.of());
        ObjectInstance self = new ObjectInstance(new ObjectInstanceId("object-compliance-context"),
                "complianceContext", classId, List.of());
        ObjectModel objectModel = new ObjectModel(new ObjectModelId("snapshot-compliance"),
                "OCL 2.4 Compliance", List.of(self), List.of());
        return new Fixture(umlModel, umlClass, objectModel, self);
    }

    private record Case(String id, String inventoryId, String matrixId, String pipeline, String expression,
            boolean expectedSuccess, String expectedType, String expectedValue, String expectedDiagnosticCode) {
    }

    private record Fixture(UmlModel umlModel, UmlClass umlClass, ObjectModel objectModel, ObjectInstance self) {
    }
}
