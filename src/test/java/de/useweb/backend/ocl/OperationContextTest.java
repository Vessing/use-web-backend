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
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlModelId;
import de.useweb.backend.domain.uml.UmlOperation;
import de.useweb.backend.domain.uml.UmlOperationId;
import de.useweb.backend.domain.uml.UmlParameter;
import de.useweb.backend.domain.uml.UmlParameterId;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.ocl.contract.OperationConstraintKind;
import de.useweb.backend.ocl.contract.OperationContractResult;
import de.useweb.backend.ocl.contract.OperationContext;
import de.useweb.backend.ocl.contract.OperationContextReference;
import de.useweb.backend.ocl.contract.OperationInvocationId;
import de.useweb.backend.ocl.contract.OperationResultSlot;
import de.useweb.backend.ocl.typecheck.OclType;
import de.useweb.backend.ocl.value.BooleanValue;
import de.useweb.backend.ocl.value.StringValue;

class OperationContextTest {
    private static final UmlClassId USER = new UmlClassId("class-user");
    private static final UmlOperationId RENAME = new UmlOperationId("operation-rename");
    private static final UmlParameterId NEW_NAME = new UmlParameterId("parameter-new-name");
    private static final UmlAttributeId NAME = new UmlAttributeId("attribute-name");
    private static final ObjectInstanceId ALICE = new ObjectInstanceId("object-alice");

    @Test
    void buildsDeterministicPreconditionScopesFromStableOperationMetadata() {
        OperationContext context = context(postState("Alicia"), OperationResultSlot.of(new BooleanValue(true)));
        OperationContractResult preparedResult = OperationContractResult.contextReady(
                context, OperationConstraintKind.PRECONDITION);

        assertThat(context.operation().id()).isEqualTo(RENAME);
        assertThat(context.typeEnvironment(OperationConstraintKind.PRECONDITION).findVariable("newName"))
                .contains(OclType.STRING);
        assertThat(context.typeEnvironment(OperationConstraintKind.PRECONDITION).findVariable("result")).isEmpty();
        assertThat(context.evaluationContext(OperationConstraintKind.PRECONDITION).findVariable("newName"))
                .contains(new StringValue("Alicia"));
        assertThat(context.evaluationContext(OperationConstraintKind.PRECONDITION).self().name()).isEqualTo("alice");
        assertThat(preparedResult.status()).isEqualTo(OperationContractResult.Status.CONTEXT_READY);
        assertThat(preparedResult.invocationId()).isEqualTo(context.invocationId());
    }

    @Test
    void keepsPreAndPostSnapshotsIsolatedAndExposesResultOnlyForPostconditions() {
        OperationContext context = context(postState("Alicia"), OperationResultSlot.of(new BooleanValue(true)));

        assertThat(context.preState().findObject(ALICE).orElseThrow().findSlot(NAME).orElseThrow().value().value())
                .isEqualTo("Alice");
        assertThat(context.postState().findObject(ALICE).orElseThrow().findSlot(NAME).orElseThrow().value().value())
                .isEqualTo("Alicia");
        assertThat(context.typeEnvironment(OperationConstraintKind.POSTCONDITION).findVariable("result"))
                .contains(OclType.BOOLEAN);
        assertThat(context.evaluationContext(OperationConstraintKind.POSTCONDITION).findVariable("result"))
                .contains(new BooleanValue(true));
    }

    @Test
    void reportsMissingPostStateAndResultBeforeContractEvaluation() {
        OperationContext noPostState = context(null, OperationResultSlot.unavailable());
        OperationContext noResult = context(postState("Alicia"), OperationResultSlot.unavailable());

        assertThatThrownBy(() -> noPostState.evaluationContext(OperationConstraintKind.POSTCONDITION))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Post-state");
        assertThatThrownBy(() -> noResult.evaluationContext(OperationConstraintKind.POSTCONDITION))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("result");
    }

    @Test
    void rejectsIncompleteParameterBindingsAndUnknownOperationReferences() {
        assertThatThrownBy(() -> new OperationContext(new OperationInvocationId("invocation-1"),
                new OperationContextReference(USER, RENAME), model(), ALICE, preState(), null, Map.of(),
                OperationResultSlot.unavailable()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bind every declared parameter");

        assertThatThrownBy(() -> new OperationContext(new OperationInvocationId("invocation-1"),
                new OperationContextReference(USER, new UmlOperationId("missing")), model(), ALICE, preState(), null,
                Map.of(NEW_NAME, new StringValue("Alicia")), OperationResultSlot.unavailable()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unknown operation");
    }

    private static OperationContext context(ObjectModel postState, OperationResultSlot result) {
        return new OperationContext(new OperationInvocationId("invocation-rename-alice"),
                new OperationContextReference(USER, RENAME), model(), ALICE, preState(), postState,
                Map.of(NEW_NAME, new StringValue("Alicia")), result);
    }

    private static UmlModel model() {
        UmlOperation rename = new UmlOperation(RENAME, "rename", UmlType.BOOLEAN,
                List.of(new UmlParameter(NEW_NAME, "newName", UmlType.STRING)));
        UmlClass user = new UmlClass(USER, "User", List.of(new UmlAttribute(NAME, "name", UmlType.STRING)),
                List.of(rename));
        return new UmlModel(new UmlModelId("model-contract"), "Contract model", List.of(user), List.of(), List.of());
    }

    private static ObjectModel preState() {
        return snapshot("snapshot-before", "Alice");
    }

    private static ObjectModel postState(String name) {
        return snapshot("snapshot-after", name);
    }

    private static ObjectModel snapshot(String id, String name) {
        ObjectInstance alice = new ObjectInstance(ALICE, "alice", USER,
                List.of(new Slot(new SlotId("slot-name"), NAME, SlotValue.ofString(name))));
        return new ObjectModel(new ObjectModelId(id), id, List.of(alice), List.of());
    }
}
