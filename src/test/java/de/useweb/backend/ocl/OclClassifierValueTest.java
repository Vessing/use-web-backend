package de.useweb.backend.ocl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.useweb.backend.api.mapper.ProjectDtoMapper;
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
import de.useweb.backend.domain.uml.UmlDataType;
import de.useweb.backend.domain.uml.UmlDataTypeId;
import de.useweb.backend.domain.uml.UmlDataTypeProperty;
import de.useweb.backend.domain.uml.UmlEnumeration;
import de.useweb.backend.domain.uml.UmlEnumerationId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlModelId;
import de.useweb.backend.domain.uml.UmlModelImport;
import de.useweb.backend.domain.uml.UmlModelImportId;
import de.useweb.backend.domain.uml.UmlPackage;
import de.useweb.backend.domain.uml.UmlPackageId;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.ocl.evaluation.EvaluationContext;
import de.useweb.backend.ocl.evaluation.OclEvaluator;
import de.useweb.backend.ocl.parser.OclParser;
import de.useweb.backend.ocl.typecheck.OclType;
import de.useweb.backend.ocl.typecheck.OclTypeChecker;
import de.useweb.backend.ocl.typecheck.TypeEnvironment;
import de.useweb.backend.ocl.value.ClassifierValue;
import de.useweb.backend.ocl.value.RealValue;

class OclClassifierValueTest {
    private final OclParser parser = new OclParser();
    private final OclTypeChecker checker = new OclTypeChecker();
    private final OclEvaluator evaluator = new OclEvaluator();

    @Test
    void computesStructuralTupleEqualityConformanceAndCommonType() {
        OclType left = OclType.tupleOf(new LinkedHashMap<>(Map.of("name", OclType.STRING, "amount", OclType.INTEGER)));
        OclType reordered = OclType.tupleOf(new LinkedHashMap<>(Map.of("amount", OclType.INTEGER, "name", OclType.STRING)));
        OclType widened = OclType.tupleOf(Map.of("name", OclType.STRING, "amount", OclType.REAL));

        assertThat(left.sameTypeAs(reordered)).isTrue();
        assertThat(left.conformsTo(widened)).isTrue();
        assertThat(left.leastUpperBound(widened).tupleParts().get("amount")).isEqualTo(OclType.REAL);
    }

    @Test
    void resolvesQualifiedEnumsAndRejectsAmbiguousShortNames() {
        Fixture fixture = fixture();
        TypeEnvironment environment = new TypeEnvironment(fixture.model(), fixture.contextClass());

        assertThat(type("billing::Status::issued", environment).displayName()).isEqualTo("billing::Status");
        assertThat(type("b::Status::issued", environment).displayName()).isEqualTo("billing::Status");
        assertThat(type("shipping::Status::sent", environment).displayName()).isEqualTo("shipping::Status");
        var ambiguous = check("Status::issued", environment);
        assertThat(ambiguous.success()).isFalse();
        assertThat(ambiguous.diagnostics()).anyMatch(diagnostic -> diagnostic.code().equals("AMBIGUOUS_QUALIFIED_NAME"));
    }

    @Test
    void persistsAndEvaluatesDataTypeValuesAndClassifierValues() {
        Fixture fixture = fixture();
        UmlModel restored = ProjectDtoMapper.toDomain(ProjectDtoMapper.toDto(fixture.model()));
        assertThat(restored.dataTypes()).isEqualTo(fixture.model().dataTypes());
        assertThat(ProjectDtoMapper.toDto(fixture.model()).dataTypes().getFirst().qualifiedName())
                .isEqualTo("billing::Money");

        EvaluationContext context = new EvaluationContext(fixture.model(), fixture.snapshot(), fixture.self());
        assertThat(evaluate("self.price.amount", context)).isEqualTo(new RealValue(12.5));
        ClassifierValue classifier = (ClassifierValue) evaluate("self.oclType()", context);
        assertThat(classifier.classifierId()).isEqualTo(fixture.contextClass().id().value());
        assertThat(classifier.qualifiedName()).isEqualTo("core::Invoice");
        assertThat(type("self.oclType()", new TypeEnvironment(fixture.model(), fixture.contextClass())).kind())
                .isEqualTo(OclType.Kind.OCL_TYPE);
    }

    private OclType type(String expression, TypeEnvironment environment) {
        var result = check(expression, environment);
        assertThat(result.diagnostics()).isEmpty();
        return result.resultType();
    }

    private de.useweb.backend.ocl.typecheck.OclTypecheckResult check(String expression, TypeEnvironment environment) {
        var parsed = parser.parse(expression);
        assertThat(parsed.diagnostics()).isEmpty();
        return checker.checkExpression(environment, parsed.ast());
    }

    private de.useweb.backend.ocl.value.OclValue evaluate(String expression, EvaluationContext context) {
        var parsed = parser.parse(expression);
        assertThat(parsed.diagnostics()).isEmpty();
        var result = evaluator.evaluate(parsed.ast(), context);
        assertThat(result.diagnostics()).isEmpty();
        return result.value();
    }

    private Fixture fixture() {
        UmlPackage core = new UmlPackage(new UmlPackageId("pkg-core"), "core");
        UmlPackage billing = new UmlPackage(new UmlPackageId("pkg-billing"), "billing");
        UmlPackage shipping = new UmlPackage(new UmlPackageId("pkg-shipping"), "shipping");
        UmlDataType money = new UmlDataType(new UmlDataTypeId("datatype-money"), "Money",
                List.of(new UmlDataTypeProperty("property-amount", "amount", UmlType.REAL)), billing.id());
        UmlAttribute price = new UmlAttribute(new UmlAttributeId("attribute-price"), "price",
                UmlType.dataType("billing::Money"));
        UmlClass invoice = new UmlClass(new UmlClassId("class-invoice"), "Invoice", List.of(price), List.of(),
                false, List.of(), de.useweb.backend.domain.uml.UmlVisibility.PUBLIC, core.id());
        UmlModel model = new UmlModel(new UmlModelId("model-b13"), "B13", List.of(invoice), List.of(), List.of(),
                List.of(new UmlEnumeration(new UmlEnumerationId("enum-billing-status"), "Status",
                                List.of("issued"), billing.id()),
                        new UmlEnumeration(new UmlEnumerationId("enum-shipping-status"), "Status",
                                List.of("sent"), shipping.id())),
                List.of(core, billing, shipping), List.of(
                        new UmlModelImport(new UmlModelImportId("import-billing"), core.id(), billing.id(),
                                "b", null, "B13 test"),
                        new UmlModelImport(new UmlModelImportId("import-shipping"), core.id(), shipping.id(),
                                "s", null, "B13 test")), List.of(money));
        ObjectInstance self = new ObjectInstance(new ObjectInstanceId("invoice-1"), "invoice1", invoice.id(),
                List.of(new Slot(new SlotId("slot-price"), price.id(),
                        new SlotValue(Map.of("amount", 12.5), UmlType.dataType("billing::Money")))));
        ObjectModel snapshot = new ObjectModel(new ObjectModelId("snapshot-b13"), "Snapshot", List.of(self), List.of());
        return new Fixture(model, invoice, self, snapshot);
    }

    private record Fixture(UmlModel model, UmlClass contextClass, ObjectInstance self, ObjectModel snapshot) {}
}
