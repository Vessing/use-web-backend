package de.useweb.backend.application.operation;

import java.util.Map;
import de.useweb.backend.domain.project.Project;
import de.useweb.backend.domain.snapshot.ObjectInstance;
import de.useweb.backend.domain.snapshot.SlotValue;
import de.useweb.backend.domain.uml.UmlOperation;
import de.useweb.backend.domain.uml.UmlParameterId;

public record OperationExecutionContext(
        Project project,
        ObjectInstance receiver,
        UmlOperation operation,
        Map<UmlParameterId, SlotValue> arguments) {
    public OperationExecutionContext {
        arguments = Map.copyOf(arguments);
    }
}
