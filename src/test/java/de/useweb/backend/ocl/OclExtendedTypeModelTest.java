package de.useweb.backend.ocl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import de.useweb.backend.domain.uml.UmlEnumeration;
import de.useweb.backend.domain.uml.UmlEnumerationId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlModelId;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.domain.uml.UmlGeneralizationException;
import de.useweb.backend.api.mapper.ProjectDtoMapper;
import de.useweb.backend.ocl.ast.EnumLiteralExpression;
import de.useweb.backend.ocl.ast.LiteralExpression;
import de.useweb.backend.ocl.ast.LiteralType;
import de.useweb.backend.ocl.collection.CollectionKind;
import de.useweb.backend.ocl.evaluation.EvaluationContext;
import de.useweb.backend.ocl.evaluation.OclEvaluator;
import de.useweb.backend.ocl.parser.OclParser;
import de.useweb.backend.ocl.typecheck.OclType;
import de.useweb.backend.ocl.typecheck.OclTypeChecker;
import de.useweb.backend.ocl.typecheck.TypeEnvironment;
import de.useweb.backend.ocl.value.BooleanValue;
import de.useweb.backend.ocl.value.CollectionValue;
import de.useweb.backend.ocl.value.EnumValue;
import de.useweb.backend.ocl.value.StringValue;
import de.useweb.backend.ocl.value.UnlimitedNaturalValue;

class OclExtendedTypeModelTest {

    private static final UmlClassId PERSON = new UmlClassId("class-person");
    private static final UmlClassId NAMED = new UmlClassId("class-named");
    private static final UmlClassId EMPLOYEE = new UmlClassId("class-employee");
    private static final UmlClassId MANAGER = new UmlClassId("class-manager");
    private static final UmlAttributeId NAME = new UmlAttributeId("attr-name");

    private final OclParser parser = new OclParser();
    private final OclTypeChecker typeChecker = new OclTypeChecker();
    private final OclEvaluator evaluator = new OclEvaluator();

    @Test
    void modelsMultipleInheritanceAndRejectsGeneralizationCycles() {
        UmlModel model = model();
        assertThat(model.isSubtypeOf(EMPLOYEE, PERSON)).isTrue();
        assertThat(model.isSubtypeOf(EMPLOYEE, NAMED)).isTrue();
        assertThat(model.isSubtypeOf(MANAGER, PERSON)).isTrue();
        assertThat(model.typeConformanceOrder(MANAGER))
                .containsExactly(MANAGER, EMPLOYEE, PERSON, NAMED);

        UmlClass a = new UmlClass(new UmlClassId("a"), "A", List.of(), List.of(), false,
                List.of(new UmlClassId("b")));
        UmlClass b = new UmlClass(new UmlClassId("b"), "B", List.of(), List.of(), false,
                List.of(new UmlClassId("a")));
        assertThatThrownBy(() -> new UmlModel(new UmlModelId("cycle"), "Cycle",
                List.of(a, b), List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void resolvesDiamondsAndRejectsOnlyGenuinelyAmbiguousInheritedFeatures() {
        UmlClass root = new UmlClass(new UmlClassId("root"), "Root",
                List.of(new UmlAttribute(new UmlAttributeId("root-code"), "code", UmlType.STRING)), List.of());
        UmlClass left = new UmlClass(new UmlClassId("left"), "Left", List.of(), List.of(), false,
                List.of(root.id()));
        UmlClass right = new UmlClass(new UmlClassId("right"), "Right", List.of(), List.of(), false,
                List.of(root.id()));
        UmlClass diamond = new UmlClass(new UmlClassId("diamond"), "Diamond", List.of(), List.of(), false,
                List.of(left.id(), right.id()));

        UmlModel valid = new UmlModel(new UmlModelId("diamond-model"), "Diamond",
                List.of(root, left, right, diamond), List.of(), List.of());
        assertThat(valid.findAttribute(diamond.id(), "code")).isPresent();
        assertThat(valid.leastCommonSuperClass(left.id(), right.id())).contains(root);

        UmlClass leftWithConflict = new UmlClass(left.id(), left.name(),
                List.of(new UmlAttribute(new UmlAttributeId("left-label"), "label", UmlType.STRING)),
                List.of(), false, List.of(root.id()));
        UmlClass rightWithConflict = new UmlClass(right.id(), right.name(),
                List.of(new UmlAttribute(new UmlAttributeId("right-label"), "label", UmlType.STRING)),
                List.of(), false, List.of(root.id()));

        assertThatThrownBy(() -> new UmlModel(new UmlModelId("conflict"), "Conflict",
                List.of(root, leftWithConflict, rightWithConflict, diamond), List.of(), List.of()))
                .isInstanceOf(UmlGeneralizationException.class)
                .satisfies(exception -> {
                    UmlGeneralizationException conflict = (UmlGeneralizationException) exception;
                    assertThat(conflict.code()).isEqualTo("AMBIGUOUS_INHERITED_FEATURE");
                    assertThat(conflict.details()).containsEntry("className", "Diamond")
                            .containsEntry("featureName", "label");
                });
    }

    @Test
    void computesHierarchyAwareLeastUpperBoundsWithoutParentOrderPriority() {
        UmlClass root = new UmlClass(new UmlClassId("root"), "Root", List.of(), List.of(), true, List.of());
        UmlClass left = new UmlClass(new UmlClassId("left"), "Left", List.of(), List.of(), false, List.of(root.id()));
        UmlClass right = new UmlClass(new UmlClassId("right"), "Right", List.of(), List.of(), false, List.of(root.id()));
        UmlModel model = new UmlModel(new UmlModelId("lub"), "LUB", List.of(root, left, right), List.of(), List.of());

        OclType lub = OclType.classType(left, model).leastUpperBound(OclType.classType(right, model), model);

        assertThat(lub.classId()).isEqualTo(root.id());
        TypeEnvironment environment = new TypeEnvironment(model, left, Map.of(
                "left", OclType.classType(left, model), "right", OclType.classType(right, model)));
        assertThat(type("if true then left else right endif", environment).classId()).isEqualTo(root.id());
    }

    @Test
    void typechecksSubtypingInheritedPropertiesEnumsAndUnlimitedNatural() {
        UmlModel model = model();
        UmlClass employee = model.findClass(EMPLOYEE).orElseThrow();
        UmlClass manager = model.findClass(MANAGER).orElseThrow();
        TypeEnvironment environment = new TypeEnvironment(model, employee, Map.of(
                "employee", OclType.classType(employee, model),
                "manager", OclType.classType(manager, model)));

        assertThat(type("self.name", environment)).isEqualTo(OclType.STRING);
        assertThat(type("let person : Person = self in person.name", environment)).isEqualTo(OclType.STRING);
        assertThat(type("if true then employee else manager endif", environment).classId()).isEqualTo(EMPLOYEE);
        assertThat(type("Status::active", environment).kind()).isEqualTo(OclType.Kind.ENUM);
        assertThat(type("*", environment)).isEqualTo(OclType.UNLIMITED_NATURAL);
        assertThat(OclType.classType(manager, model).conformsTo(OclType.classType(employee, model))).isTrue();

        var unknownLiteral = check("Status::missing", environment);
        assertThat(unknownLiteral.success()).isFalse();
        assertThat(unknownLiteral.diagnostics()).anyMatch(diagnostic -> diagnostic.code().equals("UNKNOWN_ENUM_LITERAL"));
    }

    @Test
    void evaluatesEnumValuesInheritedSlotsAndAllInstancesIncludingSubtypes() {
        UmlModel model = model();
        ObjectInstance employee = object("employee-1", "alice", EMPLOYEE, "Alice", "active");
        ObjectInstance manager = object("manager-1", "bob", MANAGER, "Bob", "inactive");
        ObjectModel snapshot = new ObjectModel(new ObjectModelId("snapshot"), "Snapshot",
                List.of(employee, manager), List.of());
        EvaluationContext context = new EvaluationContext(model, snapshot, employee);

        assertThat(evaluate("self.name", context)).isEqualTo(new StringValue("Alice"));
        assertThat(evaluate("self.status", context))
                .isEqualTo(new EnumValue(new UmlEnumerationId("enum-status"), "Status", "active"));
        assertThat(evaluate("Status::active = self.status", context)).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("*", context)).isEqualTo(UnlimitedNaturalValue.UNLIMITED);

        CollectionValue people = (CollectionValue) evaluate("Person.allInstances()", context);
        assertThat(people.collectionKind()).isEqualTo(CollectionKind.SET);
        assertThat(people.values()).hasSize(2);
        assertThat(((CollectionValue) evaluate("Employee.allInstances()", context)).values()).hasSize(2);
        assertThat(((CollectionValue) evaluate("Manager.allInstances()", context)).values()).hasSize(1);
    }

    @Test
    void parsesQualifiedEnumAndUnlimitedNaturalLiteralsWithRanges() {
        var enumParse = parser.parse("Status::active");
        assertThat(enumParse.success()).isTrue();
        assertThat(enumParse.ast()).isInstanceOfSatisfying(EnumLiteralExpression.class, expression -> {
            assertThat(expression.enumerationName()).isEqualTo("Status");
            assertThat(expression.literalName()).isEqualTo("active");
            assertThat(expression.sourceRange().end().offset()).isEqualTo(14);
        });
        var unlimited = parser.parse("*");
        assertThat(unlimited.success()).isTrue();
        assertThat(unlimited.ast()).isEqualTo(new LiteralExpression(LiteralType.UNLIMITED_NATURAL, "*",
                unlimited.ast().sourceRange()));
    }

    @Test
    void preservesGeneralizationsAbstractFlagsAndEnumerationsInDtos() {
        UmlModel original = model();
        var dto = ProjectDtoMapper.toDto(original);
        UmlModel restored = ProjectDtoMapper.toDomain(dto);

        assertThat(dto.primitiveTypes()).contains("UnlimitedNatural");
        assertThat(restored.enumerations()).isEqualTo(original.enumerations());
        assertThat(restored.findClass(PERSON).orElseThrow().abstractClass()).isTrue();
        assertThat(restored.findClass(EMPLOYEE).orElseThrow().superClassIds())
                .containsExactly(PERSON, NAMED);
    }

    private OclType type(String text, TypeEnvironment environment) {
        var result = check(text, environment);
        assertThat(result.diagnostics()).isEmpty();
        return result.resultType();
    }

    private de.useweb.backend.ocl.typecheck.OclTypecheckResult check(String text, TypeEnvironment environment) {
        var parse = parser.parse(text);
        assertThat(parse.diagnostics()).isEmpty();
        return typeChecker.checkExpression(environment, parse.ast());
    }

    private de.useweb.backend.ocl.value.OclValue evaluate(String text, EvaluationContext context) {
        var parse = parser.parse(text);
        assertThat(parse.diagnostics()).isEmpty();
        var result = evaluator.evaluate(parse.ast(), context);
        assertThat(result.diagnostics()).isEmpty();
        return result.value();
    }

    private UmlModel model() {
        UmlClass person = new UmlClass(PERSON, "Person",
                List.of(new UmlAttribute(NAME, "name", UmlType.STRING)), List.of(), true, List.of());
        UmlClass named = new UmlClass(NAMED, "Named", List.of(), List.of(), true, List.of());
        UmlClass employee = new UmlClass(EMPLOYEE, "Employee",
                List.of(new UmlAttribute(new UmlAttributeId("attr-status"), "status",
                        UmlType.enumerationType("Status"))),
                List.of(), false, List.of(PERSON, NAMED));
        UmlClass manager = new UmlClass(MANAGER, "Manager", List.of(), List.of(), false, List.of(EMPLOYEE));
        UmlEnumeration status = new UmlEnumeration(new UmlEnumerationId("enum-status"), "Status",
                List.of("active", "inactive"));
        return new UmlModel(new UmlModelId("model"), "Types",
                List.of(person, named, employee, manager), List.of(), List.of(), List.of(status));
    }

    private ObjectInstance object(String id, String name, UmlClassId classId, String displayName, String status) {
        return new ObjectInstance(new ObjectInstanceId(id), name, classId, List.of(
                new Slot(new SlotId("slot-name-" + id), NAME, new SlotValue(displayName, UmlType.STRING)),
                new Slot(new SlotId("slot-status-" + id), new UmlAttributeId("attr-status"),
                        new SlotValue(status, UmlType.enumerationType("Status")))));
    }
}
