package de.useweb.backend.ocl.contract;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import de.useweb.backend.domain.snapshot.ObjectInstance;
import de.useweb.backend.domain.snapshot.ObjectInstanceId;
import de.useweb.backend.domain.snapshot.ObjectModel;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlOperation;
import de.useweb.backend.domain.uml.UmlParameterId;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.ocl.evaluation.EvaluationContext;
import de.useweb.backend.ocl.typecheck.OclType;
import de.useweb.backend.ocl.typecheck.TypeEnvironment;
import de.useweb.backend.ocl.value.OclValue;

public record OperationContext(
        OperationInvocationId invocationId,
        OperationContextReference reference,
        UmlModel umlModel,
        ObjectInstanceId receiverId,
        ObjectModel preState,
        ObjectModel postState,
        Map<UmlParameterId, OclValue> arguments,
        OperationResultSlot resultSlot) {

    public OperationContext {
        if (invocationId == null || reference == null || umlModel == null || receiverId == null || preState == null) {
            throw new IllegalArgumentException("Operation context requires invocation, model, receiver and pre-state");
        }
        resultSlot = resultSlot == null ? OperationResultSlot.unavailable() : resultSlot;
        arguments = Map.copyOf(arguments == null ? Map.of() : arguments);

        UmlClass owner = owner(umlModel, reference);
        UmlOperation operation = operation(owner, reference);
        ObjectInstance receiver = preState.findObject(receiverId)
                .orElseThrow(() -> new IllegalArgumentException("Receiver does not exist in pre-state"));
        if (!umlModel.isSubtypeOf(receiver.classId(), owner.id())) {
            throw new IllegalArgumentException("Receiver is not compatible with operation owner");
        }
        if (postState != null && postState.id().equals(preState.id())) {
            throw new IllegalArgumentException("Pre-state and post-state must be distinct snapshots");
        }
        var expectedParameterIds = operation.parameters().stream().map(parameter -> parameter.id()).toList();
        if (arguments.size() != expectedParameterIds.size() || !arguments.keySet().containsAll(expectedParameterIds)) {
            throw new IllegalArgumentException("Operation arguments must bind every declared parameter exactly once");
        }
        if (arguments.values().stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("Operation argument values must not be null");
        }
    }

    public UmlClass ownerClass() {
        return owner(umlModel, reference);
    }

    public UmlOperation operation() {
        return operation(ownerClass(), reference);
    }

    public Optional<ObjectModel> optionalPostState() {
        return Optional.ofNullable(postState);
    }

    public TypeEnvironment typeEnvironment(OperationConstraintKind kind) {
        Map<String, OclType> bindings = new LinkedHashMap<>();
        operation().parameters().forEach(parameter ->
                bindings.put(parameter.name(), OclType.fromUmlType(parameter.type(),
                        new TypeEnvironment(umlModel, ownerClass()))));
        if (kind == OperationConstraintKind.POSTCONDITION
                && !operation().returnType().equals(UmlType.VOID)) {
            bindings.put("result", OclType.fromUmlType(operation().returnType(),
                    new TypeEnvironment(umlModel, ownerClass())));
        }
        return new TypeEnvironment(umlModel, ownerClass(), bindings, kind);
    }

    public EvaluationContext evaluationContext(OperationConstraintKind kind) {
        ObjectModel snapshot = kind == OperationConstraintKind.PRECONDITION
                ? preState
                : optionalPostState().orElseThrow(() -> new IllegalStateException("Post-state is not available"));
        ObjectInstance receiver = snapshot.findObject(receiverId)
                .orElseThrow(() -> new IllegalStateException("Receiver does not exist in selected state"));
        Map<String, OclValue> bindings = new LinkedHashMap<>();
        operation().parameters().forEach(parameter -> bindings.put(parameter.name(), arguments.get(parameter.id())));
        if (kind == OperationConstraintKind.POSTCONDITION
                && !operation().returnType().equals(UmlType.VOID)) {
            bindings.put("result", resultSlot.optionalValue()
                    .orElseThrow(() -> new IllegalStateException("Operation result is not available")));
        }
        return new EvaluationContext(umlModel, snapshot, receiver, bindings, preState);
    }

    private static UmlClass owner(UmlModel model, OperationContextReference reference) {
        return model.findClass(reference.ownerClassId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown operation owner class"));
    }

    private static UmlOperation operation(UmlClass owner, OperationContextReference reference) {
        return owner.operations().stream().filter(candidate -> candidate.id().equals(reference.operationId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown operation in owner class"));
    }
}
