package de.useweb.backend.ocl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

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
                List.of(new UmlAttribute(new UmlAttributeId("attribute-name"), "name", UmlType.STRING)),
                List.of(pickInteger, pickReal, mixLeft, mixRight, secret));
        UmlClass other = new UmlClass(OTHER, "Other", List.of(), List.of());
        return new UmlModel(new UmlModelId("model-resolution"), "Resolution", List.of(item, other),
                List.of(), List.of());
    }

    private static UmlOperation operation(String id, String name, UmlType result,
            List<UmlParameter> parameters, UmlVisibility visibility) {
        return new UmlOperation(new UmlOperationId(id), name, result, parameters, null, visibility, false, true);
    }

    private static UmlParameter parameter(String id, UmlType type) {
        return new UmlParameter(new UmlParameterId(id), id, type);
    }
}
