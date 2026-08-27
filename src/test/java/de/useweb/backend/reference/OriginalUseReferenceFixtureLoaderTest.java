package de.useweb.backend.reference;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.useweb.backend.ocl.contract.OperationContractResult;
import de.useweb.backend.ocl.evaluation.OclEvaluator;
import de.useweb.backend.ocl.parser.OclParser;
import de.useweb.backend.ocl.typecheck.OclTypeChecker;

class OriginalUseReferenceFixtureLoaderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void assignsNewObjectToShellVariableWithoutParsingCreationAsOcl() {
        var fixture = loadFromSources("""
                model AssignmentModel
                class Person
                attributes
                  name : String
                end
                """, """
                !person := new Person('ada')
                !set person.name := 'Ada'
                """);

        assertThat(fixture.unsupportedSetup()).isEmpty();
        assertThat(evaluate(fixture, "person.name")).isEqualTo("Ada");
    }

    @Test
    void loadsCaseModelAndReplaysBasicSnapshotCommands() throws Exception {
        var fixture = load("shell/t002.use", "shell/t002.in", 17);

        assertThat(fixture.unsupportedSetup()).isEmpty();
        assertThat(fixture.typeEnvironment().umlModel().classes())
                .extracting(umlClass -> umlClass.name())
                .contains("A", "B", "C");
        assertThat(fixture.evaluationContext().objectModel().objects())
                .extracting(object -> object.name())
                .containsExactly("a1", "a2", "b1", "c1");
        assertThat(fixture.evaluationContext().objectModel().objects().get(0).slots().get(0).value().value())
                .isEqualTo(5);
        assertThat(fixture.evaluationContext().objectModel().links()).hasSize(2);
        assertThat(fixture.typeEnvironment().findVariable("a1")).isPresent();
        assertThat(fixture.evaluationContext().findVariable("c1")).isPresent();
    }

    @Test
    void resolvesSingleValuedAssociationNavigationForSet() throws Exception {
        var fixture = load("shell/t013.use", "shell/t013.in", 15);

        assertThat(fixture.unsupportedSetup()).isEmpty();
        var objectB = fixture.evaluationContext().objectModel().objects().stream()
                .filter(object -> object.name().equals("b"))
                .findFirst().orElseThrow();
        assertThat(objectB.slots().get(0).value().value()).isEqualTo(42);
    }

    @Test
    void destroysObjectAndClearsLinksAndObjectReferences() throws Exception {
        var fixture = load("shell/t009.use", "shell/t009.in", 19);

        assertThat(fixture.unsupportedSetup()).isEmpty();
        assertThat(fixture.evaluationContext().objectModel().objects())
                .extracting(object -> object.name())
                .containsExactly("a");
        assertThat(fixture.evaluationContext().objectModel().objects().get(0).slots().get(0).value().value())
                .isNull();
        assertThat(fixture.evaluationContext().findVariable("b")).isEmpty();
    }

    @Test
    void destroysMultipleObjectsWithoutAddingSyntheticObjectToSnapshot() throws Exception {
        var fixture = load("shell/t018.use", "shell/t018.in", 12);

        assertThat(fixture.unsupportedSetup()).isEmpty();
        assertThat(fixture.evaluationContext().objectModel().objects()).isEmpty();
    }

    @Test
    void deletesExistingBinaryAssociationLink() throws Exception {
        var beforeDelete = load("shell/t112.use", "shell/t112.in", 35);
        var afterDelete = load("shell/t112.use", "shell/t112.in", 36);

        assertThat(beforeDelete.unsupportedSetup()).isEmpty();
        assertThat(afterDelete.unsupportedSetup()).isEmpty();
        assertThat(afterDelete.evaluationContext().objectModel().links())
                .hasSize(beforeDelete.evaluationContext().objectModel().links().size() - 1);
    }

    @Test
    void reportsUnsupportedSnapshotSelectorWithSourceLine() throws Exception {
        var fixture = load("shell/t018.use", "shell/t018.in", 21);

        assertThat(fixture.unsupportedSetup())
                .anyMatch(message -> message.contains("shell/t018.in:21") && message.contains("destroy selector"));
    }

    @Test
    void evaluatesTypedUntypedAndCollectionShellVariablesInOrder() {
        var fixture = loadFromSources("""
                model Variables
                class Item
                end
                """, """
                !create item : Item
                !let count : Integer = 1
                !count := count + 1
                !let names = Sequence{'A','B'}
                !let pair = Tuple{first = 1, second = 'A'}
                !let selected = item
                !let item = 7
                """);

        assertThat(fixture.unsupportedSetup()).isEmpty();
        assertThat(fixture.typeEnvironment().findVariable("count")).get()
                .extracting(type -> type.displayName()).isEqualTo("Integer");
        assertThat(fixture.evaluationContext().findVariable("count")).get()
                .extracting(value -> value.rawValue()).isEqualTo(2);
        assertThat(fixture.typeEnvironment().findVariable("names")).get()
                .extracting(type -> type.displayName()).isEqualTo("Sequence(String)");
        assertThat(fixture.typeEnvironment().findVariable("pair")).get()
                .satisfies(type -> assertThat(type.tupleParts()).containsOnlyKeys("first", "second"));
        assertThat(fixture.evaluationContext().findVariable("selected")).isPresent();
        assertThat(fixture.evaluationContext().findVariable("item")).get()
                .extracting(value -> value.rawValue()).isEqualTo(7);
    }

    @Test
    void supportsLegacyAssignCreateAndBindsGeneratedObject() {
        var fixture = loadFromSources("""
                model Variables
                class Item
                end
                """, "!assign created := create Item");

        assertThat(fixture.unsupportedSetup()).isEmpty();
        assertThat(fixture.evaluationContext().objectModel().objects())
                .extracting(object -> object.name()).containsExactly("Item1");
        assertThat(fixture.typeEnvironment().findVariable("created")).isPresent();
        assertThat(fixture.evaluationContext().findVariable("created")).isPresent();
    }

    @Test
    void rejectsIncompatibleReassignmentWithoutChangingLanguageStatus() {
        var fixture = loadFromSources("""
                model Variables
                class Item
                end
                """, """
                !let count : Integer = 1
                !count := 'wrong'
                """);

        assertThat(fixture.unsupportedSetup())
                .singleElement().asString().contains("requires Integer");
        assertThat(fixture.oclSetupFailure()).isFalse();
    }

    @Test
    void marksUnsupportedOclInsideAssignmentAsLanguageGap() {
        var fixture = loadFromSources("""
                model Variables
                class Item
                end
                """, "!let broken = unknownVariable + 1");

        assertThat(fixture.unsupportedSetup()).hasSize(1);
        assertThat(fixture.oclSetupFailure()).isTrue();
    }

    @Test
    void keepsShellVariablesIsolatedBetweenReferenceCases() {
        var first = loadFromSources("model Variables\nclass Item\nend", "!let local = 1");
        var second = loadFromSources("model Variables\nclass Item\nend", "");

        assertThat(first.typeEnvironment().findVariable("local")).isPresent();
        assertThat(second.typeEnvironment().findVariable("local")).isEmpty();
    }

    @Test
    void loadsAndEvaluatesDeclarativeQueryOperationFromOriginalT052Model() throws Exception {
        var fixture = load("shell/t052.use", "shell/t052.in", 8);
        assertThat(fixture.unsupportedSetup()).isEmpty();
        assertThat(evaluate(fixture, "p1.getNameOCLImplemented('Ein ')"))
                .isEqualTo("Ein Tester");
    }

    @Test
    void replaysExplicitOperationFrameWithSelfLocalsAndAssociationNavigation() throws Exception {
        var fixture = load("shell/t012.use", "shell/t012.in", 27);
        assertThat(fixture.unsupportedSetup()).isEmpty();
        assertThat(evaluate(fixture, "a.rb1 = b2")).isEqualTo(true);
        assertThat(evaluate(fixture, "a.rb2 = b1")).isEqualTo(true);
    }

    @Test
    void evaluatesPreAndPostconditionsWithResultAndSeparatePreState() {
        var fixture = loadFromSources("""
                model Contracts
                class User
                attributes
                  name : String
                operations
                  rename(newName : String) : Boolean
                    pre NotBlank: newName <> ''
                    post Changed: self.name@pre <> self.name and result
                end
                """, """
                !create alice : User
                !set alice.name := 'Alice'
                !openter alice rename('Alicia')
                !set self.name := 'Alicia'
                !opexit true
                """);
        assertThat(fixture.unsupportedSetup()).isEmpty();
        assertThat(fixture.operationResults()).extracting(OperationContractResult::status)
                .containsExactly(OperationContractResult.Status.SATISFIED,
                        OperationContractResult.Status.SATISFIED);
    }

    @Test
    void reportsContractContextErrorsAsOclSetupDiagnostics() {
        var fixture = loadFromSources("""
                model Contracts
                class User
                operations
                  resultRequired() : Boolean
                    post HasResult: result
                end
                """, """
                !create alice : User
                !openter alice resultRequired()
                !opexit
                """);
        assertThat(fixture.oclSetupFailure()).isTrue();
        assertThat(fixture.unsupportedSetup()).singleElement().asString()
                .contains("cannot evaluate operation contract");
    }

    @Test
    void supportsBoundAndGeneratedNewObjectsInACommandSequence() {
        var fixture = loadFromSources("""
                model NewObjects
                class Item
                end
                """, "!new Item('named'); new Item; new Item");

        assertThat(fixture.unsupportedSetup()).isEmpty();
        assertThat(fixture.evaluationContext().objectModel().objects())
                .extracting(object -> object.name())
                .containsExactly("named", "@Item1", "@Item2");
    }

    @Test
    void executesSupportedImperativeOperationBodyWithInitValueAndPostcondition() {
        var fixture = loadFromSources("""
                model CounterModel
                class Counter
                attributes
                  value : Integer init: 0
                operations
                  increment()
                    begin self.value := self.value + 1 end
                    post Incremented: self.value = self.value@pre + 1
                end
                """, """
                !new Counter('counter')
                !counter.increment()
                """);

        assertThat(fixture.unsupportedSetup()).isEmpty();
        assertThat(evaluate(fixture, "counter.value")).isEqualTo(1);
        assertThat(fixture.operationResults()).extracting(OperationContractResult::status)
                .containsExactly(OperationContractResult.Status.SATISFIED);
    }

    @Test
    void executesOverriddenImperativeBodyFromOriginalT105Trace() throws Exception {
        var fixture = load("shell/t105.use", "shell/t105.in", 3);

        assertThat(fixture.unsupportedSetup()).isEmpty();
        assertThat(evaluate(fixture, "d1.n")).isEqualTo(2);
    }

    @Test
    void rollsBackImperativeOperationBodyWhenACommandIsUnsupported() {
        var fixture = loadFromSources("""
                model CounterModel
                class Counter
                attributes
                  value : Integer init: 0
                operations
                  broken()
                    begin
                      self.value := 1
                      destroy self
                    end
                end
                """, """
                !new Counter('counter')
                !counter.broken()
                """);

        assertThat(fixture.unsupportedSetup()).singleElement().asString()
                .contains("unsupported operation body command");
        assertThat(evaluate(fixture, "counter.value")).isEqualTo(0);
    }

    private OriginalUseReferenceFixtureLoader.Fixture load(
            String modelFile, String replayFile, int replayThroughLine) throws Exception {
        ObjectNode referenceCase = objectMapper.createObjectNode();
        ObjectNode setup = referenceCase.putObject("setup");
        setup.put("modelFile", modelFile);
        setup.put("replaySourceFile", replayFile);
        setup.put("replayThroughLine", replayThroughLine);
        return OriginalUseReferenceFixtureLoader.load(referenceCase);
    }

    private OriginalUseReferenceFixtureLoader.Fixture loadFromSources(String model, String replay) {
        return OriginalUseReferenceFixtureLoader.loadFromSources(
                model, replay, "reference/test-shell-variables.in", Math.toIntExact(replay.lines().count()));
    }

    private Object evaluate(OriginalUseReferenceFixtureLoader.Fixture fixture, String expression) {
        var parsed = new OclParser().parse(expression);
        assertThat(parsed.success()).isTrue();
        var checked = new OclTypeChecker().checkExpression(fixture.typeEnvironment(), parsed.ast());
        assertThat(checked.success()).isTrue();
        var evaluated = new OclEvaluator().evaluate(parsed.ast(), fixture.evaluationContext());
        assertThat(evaluated.success()).isTrue();
        return evaluated.value().rawValue();
    }
}
