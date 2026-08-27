package de.useweb.backend.application.operation;

import java.util.Map;
import de.useweb.backend.domain.snapshot.ObjectModel;
import de.useweb.backend.domain.snapshot.SlotValue;
import de.useweb.backend.domain.uml.UmlParameterId;

public record OperationExecutionResult(
        ObjectModel candidateState,
        SlotValue result,
        Map<UmlParameterId, SlotValue> outValues) {
    public OperationExecutionResult {
        if (candidateState == null) throw new IllegalArgumentException("Candidate state is required");
        outValues = Map.copyOf(outValues == null ? Map.of() : outValues);
    }

    public static OperationExecutionResult unchanged(ObjectModel state, SlotValue result) {
        return new OperationExecutionResult(state, result, Map.of());
    }
}
