package de.useweb.backend.ocl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.useweb.backend.domain.snapshot.ObjectInstance;
import de.useweb.backend.domain.snapshot.ObjectInstanceId;
import de.useweb.backend.domain.snapshot.ObjectModel;
import de.useweb.backend.domain.snapshot.ObjectModelId;
import de.useweb.backend.domain.snapshot.Slot;
import de.useweb.backend.domain.snapshot.SlotId;
import de.useweb.backend.domain.snapshot.SlotValue;
import de.useweb.backend.domain.uml.UmlAttribute;
import de.useweb.backend.domain.uml.UmlAttributeId;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlModelId;
import de.useweb.backend.domain.uml.UmlOperation;
import de.useweb.backend.domain.uml.UmlOperationId;
import de.useweb.backend.domain.uml.UmlParameter;
import de.useweb.backend.domain.uml.UmlParameterId;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.ocl.definition.OclDefinition;
import de.useweb.backend.ocl.definition.OclDefinitionId;
import de.useweb.backend.ocl.definition.OclDefinitionParser;
import de.useweb.backend.ocl.definition.OclDefinitionService;
import de.useweb.backend.ocl.evaluation.EvaluationContext;
import de.useweb.backend.ocl.evaluation.OclEvaluator;
import de.useweb.backend.ocl.parser.OclParser;
import de.useweb.backend.ocl.value.IntegerValue;
import de.useweb.backend.ocl.typecheck.OclTypeChecker;
import de.useweb.backend.ocl.typecheck.TypeEnvironment;

class OclDefinitionServiceTest {
    private static final UmlClassId ITEM = new UmlClassId("class-item");
    private static final UmlAttributeId BASE = new UmlAttributeId("attribute-base");
    private static final UmlAttributeId TOTAL = new UmlAttributeId("attribute-total");
    private static final UmlAttributeId ENABLED = new UmlAttributeId("attribute-enabled");
    private static final UmlAttributeId A = new UmlAttributeId("attribute-a");
    private static final UmlAttributeId B = new UmlAttributeId("attribute-b");
    private static final UmlOperationId COMPUTE = new UmlOperationId("operation-compute");
    private static final UmlParameterId VALUE = new UmlParameterId("parameter-value");

    private final OclDefinitionParser parser = new OclDefinitionParser();

    @Test
    void parsesAndEvaluatesDerivedAttributesLazily() {
        UmlModel model = model();
        OclDefinition derived = parse(model, "derived-total",
                "context Item::total : Integer derive: self.base + 1");
        OclDefinitionService service = new OclDefinitionService(model, List.of(derived));

        var result = new OclEvaluator().evaluate(new OclParser().parse("self.total").ast(),
                context(model, service));

        assertThat(service.check(derived, model).success()).isTrue();
        assertThat(result.success()).isTrue();
        assertThat(result.value()).isEqualTo(new IntegerValue(3));
    }

    @Test
    void appliesInitDefinitionsAsServerSideCreationDefaults() {
        UmlModel model = model();
        OclDefinition init = parse(model, "init-enabled",
                "context Item::enabled : Boolean init: true");
        OclDefinitionService service = new OclDefinitionService(model, List.of(init));

        var values = service.initialValues(model, snapshot(), item());

        assertThat(values).containsKey(ENABLED);
        assertThat(values.get(ENABLED).typeName()).isEqualTo("Boolean");
    }

    @Test
    void executesQueryBodiesAndAdditionalDefinitionsWithScopedParameters() {
        UmlModel model = model();
        OclDefinition helper = parse(model, "def-plus-one",
                "context Item def: plusOne(x : Integer) : Integer = x + 1");
        OclDefinition body = parse(model, "body-compute",
                "context Item::compute(value : Integer) : Integer body: self.plusOne(value) + self.base");
        OclDefinition property = parse(model, "def-bonus",
                "context Item def: bonus : Integer = self.total + 1");
        OclDefinition derived = parse(model, "derived-total",
                "context Item::total : Integer derive: self.base + 1");
        OclDefinitionService service = new OclDefinitionService(model, List.of(helper, body, property, derived));

        var bodyResult = service.evaluate(body, model, snapshot(), item(),
                Map.of("value", new IntegerValue(4)));
        var propertyResult = new OclEvaluator().evaluate(new OclParser().parse("self.bonus").ast(),
                context(model, service));

        assertThat(service.check(helper, model).success()).isTrue();
        assertThat(bodyResult.value()).isEqualTo(new IntegerValue(7));
        assertThat(propertyResult.value()).isEqualTo(new IntegerValue(4));
    }

    @Test
    void usesTheSameDefinitionOverloadRulesDuringTypeCheckingAndEvaluation() {
        UmlModel model = model();
        OclDefinition helper = parse(model, "def-plus-one",
                "context Item def: plusOne(x : Integer) : Integer = x + 1");
        OclDefinitionService service = new OclDefinitionService(model, List.of(helper));
        var expression = new OclParser().parse("self.plusOne('wrong')").ast();
        var environment = new TypeEnvironment(model, model.findClass(ITEM).orElseThrow(), Map.of(), null, service);

        var checked = new OclTypeChecker().checkExpression(environment, expression);
        var evaluated = new OclEvaluator().evaluate(expression, context(model, service));

        assertThat(checked.success()).isFalse();
        assertThat(checked.diagnostics()).extracting("code").contains("INVALID_OPERATION");
        assertThat(evaluated.success()).isFalse();
        assertThat(evaluated.diagnostics()).extracting("code").contains("CALL_RESOLUTION_ERROR");
    }

    @Test
    void resolvesImplicitSelfForPropertiesAndOperations() {
        UmlModel model = model();
        OclDefinition helper = parse(model, "def-plus-one",
                "context Item def: plusOne(x : Integer) : Integer = x + 1");
        OclDefinitionService service = new OclDefinitionService(model, List.of(helper));
        var expression = new OclParser().parse("plusOne(base)").ast();
        var environment = new TypeEnvironment(model, model.findClass(ITEM).orElseThrow(), Map.of(), null, service);

        var checked = new OclTypeChecker().checkExpression(environment, expression);
        var evaluated = new OclEvaluator().evaluate(expression, context(model, service));

        assertThat(checked.success()).isTrue();
        assertThat(checked.resultType()).isEqualTo(de.useweb.backend.ocl.typecheck.OclType.INTEGER);
        assertThat(evaluated.success()).isTrue();
        assertThat(evaluated.value()).isEqualTo(new IntegerValue(3));
    }

    @Test
    void reportsTargetTypeMismatchesPerDefinitionKind() {
        UmlModel model = model();
        OclDefinition invalid = parse(model, "init-invalid",
                "context Item::enabled : Boolean init: 1");

        var result = new OclDefinitionService(model, List.of(invalid)).check(invalid, model);

        assertThat(result.success()).isFalse();
        assertThat(result.diagnostics()).extracting("code").containsExactly("INIT_TYPE_MISMATCH");
    }

    @Test
    void stopsDirectAndIndirectDerivedCyclesWithStructuredDiagnostics() {
        UmlModel model = model();
        OclDefinition first = parse(model, "derived-a", "context Item::a : Integer derive: self.b");
        OclDefinition second = parse(model, "derived-b", "context Item::b : Integer derive: self.a");
        OclDefinitionService service = new OclDefinitionService(model, List.of(first, second));

        var result = new OclEvaluator().evaluate(new OclParser().parse("self.a").ast(),
                context(model, service));

        assertThat(result.success()).isFalse();
        assertThat(result.diagnostics()).extracting("code").contains("DERIVATION_CYCLE");
    }

    @Test
    void recordsDefinitionDependenciesAndDoesNotReuseValuesAcrossSnapshots() {
        UmlModel model = model();
        OclDefinition total = parse(model, "derived-total",
                "context Item::total : Integer derive: self.base + 1");
        OclDefinition bonus = parse(model, "def-bonus",
                "context Item def: bonus : Integer = self.total + 1");
        OclDefinitionService service = new OclDefinitionService(model, List.of(total, bonus));
        ObjectModel first = snapshot();
        EvaluationContext firstContext = new EvaluationContext(model, first, first.objects().getFirst(),
                Map.of(), first, service);

        var firstResult = new OclEvaluator().evaluate(new OclParser().parse("self.bonus").ast(), firstContext);
        ObjectInstance changed = new ObjectInstance(item().id(), item().name(), item().classId(), List.of(
                new Slot(new SlotId("slot-base"), BASE, SlotValue.ofInteger(9)),
                new Slot(new SlotId("slot-total"), TOTAL, new SlotValue(null, UmlType.INTEGER)),
                new Slot(new SlotId("slot-enabled"), ENABLED, new SlotValue(null, UmlType.BOOLEAN)),
                new Slot(new SlotId("slot-a"), A, new SlotValue(null, UmlType.INTEGER)),
                new Slot(new SlotId("slot-b"), B, new SlotValue(null, UmlType.INTEGER))));
        ObjectModel second = new ObjectModel(first.id(), first.name(), List.of(changed), List.of());
        EvaluationContext secondContext = new EvaluationContext(model, second, changed, Map.of(), second, service);
        var secondResult = new OclEvaluator().evaluate(new OclParser().parse("self.bonus").ast(), secondContext);

        assertThat(firstResult.value()).isEqualTo(new IntegerValue(4));
        assertThat(secondResult.value()).isEqualTo(new IntegerValue(11));
        assertThat(service.dependencyGraph(firstContext).values()).anySatisfy(dependencies ->
                assertThat(dependencies).contains("derived-total@object-item"));
    }

    private OclDefinition parse(UmlModel model, String id, String source) {
        return parser.parse(new OclDefinitionId(id), source, model).optionalDefinition().orElseThrow();
    }

    private EvaluationContext context(UmlModel model, OclDefinitionService service) {
        return new EvaluationContext(model, snapshot(), item(), Map.of(), snapshot(), service);
    }

    private static UmlModel model() {
        UmlOperation compute = new UmlOperation(COMPUTE, "compute", UmlType.INTEGER,
                List.of(new UmlParameter(VALUE, "value", UmlType.INTEGER)));
        UmlClass item = new UmlClass(ITEM, "Item", List.of(
                new UmlAttribute(BASE, "base", UmlType.INTEGER),
                new UmlAttribute(TOTAL, "total", UmlType.INTEGER),
                new UmlAttribute(ENABLED, "enabled", UmlType.BOOLEAN),
                new UmlAttribute(A, "a", UmlType.INTEGER),
                new UmlAttribute(B, "b", UmlType.INTEGER)), List.of(compute));
        return new UmlModel(new UmlModelId("model-definitions"), "Definitions", List.of(item), List.of(), List.of());
    }

    private static ObjectInstance item() {
        return new ObjectInstance(new ObjectInstanceId("object-item"), "item", ITEM, List.of(
                new Slot(new SlotId("slot-base"), BASE, SlotValue.ofInteger(2)),
                new Slot(new SlotId("slot-total"), TOTAL, new SlotValue(null, UmlType.INTEGER)),
                new Slot(new SlotId("slot-enabled"), ENABLED, new SlotValue(null, UmlType.BOOLEAN)),
                new Slot(new SlotId("slot-a"), A, new SlotValue(null, UmlType.INTEGER)),
                new Slot(new SlotId("slot-b"), B, new SlotValue(null, UmlType.INTEGER))));
    }

    private static ObjectModel snapshot() {
        return new ObjectModel(new ObjectModelId("snapshot-definitions"), "Definitions", List.of(item()), List.of());
    }
}
