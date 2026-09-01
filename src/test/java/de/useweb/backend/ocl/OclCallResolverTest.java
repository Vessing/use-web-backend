package de.useweb.backend.ocl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.useweb.backend.domain.uml.UmlAttribute;
import de.useweb.backend.domain.uml.UmlAttributeId;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlClassifierValue;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlModelId;
import de.useweb.backend.domain.uml.UmlOperation;
import de.useweb.backend.domain.uml.UmlOperationId;
import de.useweb.backend.domain.uml.UmlParameter;
import de.useweb.backend.domain.uml.UmlParameterId;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.domain.uml.UmlVisibility;
import de.useweb.backend.ocl.resolution.OclCallKind;
import de.useweb.backend.ocl.resolution.OclCallResolutionResult;
import de.useweb.backend.ocl.resolution.OclCallResolver;
import de.useweb.backend.ocl.typecheck.OclType;
import de.useweb.backend.ocl.typecheck.TypeEnvironment;

class OclCallResolverTest {
    private static final UmlClassId ITEM = new UmlClassId("class-item");
    private static final UmlClassId OTHER = new UmlClassId("class-other");

    @Test
    void resolvesStandardLibraryAndStableAttributeIdentity() {
        UmlModel model = model();
        OclCallResolver resolver = resolver(model, ITEM);

        var standard = resolver.resolveOperation(OclType.STRING, "size", List.of());
        var property = resolver.resolveProperty(OclType.classType(model.findClass(ITEM).orElseThrow(), model), "name");

        assertThat(standard.status()).isEqualTo(OclCallResolutionResult.Status.RESOLVED);
        assertThat(standard.resolution().kind()).isEqualTo(OclCallKind.STANDARD_LIBRARY);
        assertThat(property.resolution().kind()).isEqualTo(OclCallKind.UML_ATTRIBUTE);
        assertThat(property.resolution().featureId()).isEqualTo("attribute-name");
    }

    @Test
    void selectsTheMostSpecificVisibleOverload() {
        UmlModel model = model();
        OclCallResolver resolver = resolver(model, ITEM);
        OclType receiver = OclType.classType(model.findClass(ITEM).orElseThrow(), model);

        var integer = resolver.resolveOperation(receiver, "pick", List.of(OclType.INTEGER));
        var unlimited = resolver.resolveOperation(receiver, "pick", List.of(OclType.UNLIMITED_NATURAL));
        var real = resolver.resolveOperation(receiver, "pick", List.of(OclType.REAL));

        assertThat(integer.resolution().featureId()).isEqualTo("operation-pick-integer");
        assertThat(unlimited.resolution().featureId()).isEqualTo("operation-pick-integer");
        assertThat(real.resolution().featureId()).isEqualTo("operation-pick-real");
    }

    @Test
    void reportsAmbiguousAndInaccessibleCallsExplicitly() {
        UmlModel model = model();
        OclType receiver = OclType.classType(model.findClass(ITEM).orElseThrow(), model);

        var ambiguous = resolver(model, ITEM).resolveOperation(receiver, "mix",
                List.of(OclType.INTEGER, OclType.INTEGER));
        var inaccessible = resolver(model, OTHER).resolveOperation(receiver, "secret", List.of());

        assertThat(ambiguous.status()).isEqualTo(OclCallResolutionResult.Status.AMBIGUOUS);
        assertThat(inaccessible.status()).isEqualTo(OclCallResolutionResult.Status.INACCESSIBLE);
    }

    @Test
    void dispatchesExplicitlyRedefinedFeaturesToTheLocalFeature() {
        UmlClass left = redefinableClass("left", "Left", "left-name", "left-display", List.of());
        UmlClass right = redefinableClass("right", "Right", "right-name", "right-display", List.of());
        UmlAttribute localName = new UmlAttribute(new UmlAttributeId("child-name"), "name", UmlType.STRING,
                false, null, null, UmlVisibility.PUBLIC,
                List.of(new UmlAttributeId("left-name"), new UmlAttributeId("right-name")));
        UmlOperation localDisplay = new UmlOperation(new UmlOperationId("child-display"), "displayName",
                UmlType.STRING, List.of(), null, UmlVisibility.PUBLIC, false, true, List.of(),
                List.of(new UmlOperationId("left-display"), new UmlOperationId("right-display")));
        UmlClass child = new UmlClass(new UmlClassId("child"), "Child", List.of(localName),
                List.of(localDisplay), false, List.of(left.id(), right.id()));
        UmlModel model = new UmlModel(new UmlModelId("model-redefinition"), "Redefinition",
                List.of(left, right, child), List.of(), List.of());
        OclCallResolver resolver = resolver(model, child.id());
        OclType receiver = OclType.classType(child, model);

        assertThat(resolver.resolveProperty(receiver, "name").resolution().featureId()).isEqualTo("child-name");
        assertThat(resolver.resolveOperation(receiver, "displayName", List.of()).resolution().featureId())
                .isEqualTo("child-display");
    }

    @Test
    void classifierValuesResolveOnlyStaticAttributes() {
        UmlModel model = model();
        OclCallResolver resolver = resolver(model, ITEM);
        OclType itemType = OclType.classType(model.findClass(ITEM).orElseThrow(), model);
        OclType classifier = OclType.classifierValueType(itemType);

        assertThat(resolver.resolveProperty(classifier, "nextNumber").status())
                .isEqualTo(OclCallResolutionResult.Status.RESOLVED);
        assertThat(resolver.resolveProperty(classifier, "name").status())
                .isEqualTo(OclCallResolutionResult.Status.UNKNOWN);
    }

    private static OclCallResolver resolver(UmlModel model, UmlClassId context) {
        return new OclCallResolver(new TypeEnvironment(model, model.findClass(context).orElseThrow()));
    }

    private static UmlModel model() {
        UmlOperation pickInteger = operation("operation-pick-integer", "pick", UmlType.INTEGER,
                List.of(parameter("parameter-int", UmlType.INTEGER)), UmlVisibility.PUBLIC);
        UmlOperation pickReal = operation("operation-pick-real", "pick", UmlType.REAL,
                List.of(parameter("parameter-real", UmlType.REAL)), UmlVisibility.PUBLIC);
        UmlOperation mixLeft = operation("operation-mix-left", "mix", UmlType.INTEGER,
                List.of(parameter("parameter-left-int", UmlType.INTEGER),
                        parameter("parameter-left-real", UmlType.REAL)), UmlVisibility.PUBLIC);
        UmlOperation mixRight = operation("operation-mix-right", "mix", UmlType.INTEGER,
                List.of(parameter("parameter-right-real", UmlType.REAL),
                        parameter("parameter-right-int", UmlType.INTEGER)), UmlVisibility.PUBLIC);
        UmlOperation secret = operation("operation-secret", "secret", UmlType.INTEGER, List.of(),
                UmlVisibility.PRIVATE);
        UmlClass item = new UmlClass(ITEM, "Item",
                List.of(new UmlAttribute(new UmlAttributeId("attribute-name"), "name", UmlType.STRING),
                        new UmlAttribute(new UmlAttributeId("attribute-next"), "nextNumber", UmlType.INTEGER,
                                false, null, null, UmlVisibility.PUBLIC, List.of(), true,
                                new UmlClassifierValue(UmlType.INTEGER, 42))),
                List.of(pickInteger, pickReal, mixLeft, mixRight, secret));
        UmlClass other = new UmlClass(OTHER, "Other", List.of(), List.of());
        return new UmlModel(new UmlModelId("model-resolution"), "Resolution", List.of(item, other),
                List.of(), List.of());
    }

    private static UmlOperation operation(String id, String name, UmlType result,
            List<UmlParameter> parameters, UmlVisibility visibility) {
        return new UmlOperation(new UmlOperationId(id), name, result, parameters, null, visibility, false, true);
    }

    private static UmlClass redefinableClass(String id, String name, String attributeId, String operationId,
            List<UmlClassId> parents) {
        return new UmlClass(new UmlClassId(id), name,
                List.of(new UmlAttribute(new UmlAttributeId(attributeId), "name", UmlType.STRING)),
                List.of(new UmlOperation(new UmlOperationId(operationId), "displayName", UmlType.STRING,
                        List.of())),
                false, parents);
    }

    private static UmlParameter parameter(String id, UmlType type) {
        return new UmlParameter(new UmlParameterId(id), id, type);
    }
}
