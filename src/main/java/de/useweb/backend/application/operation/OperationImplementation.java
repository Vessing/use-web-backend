package de.useweb.backend.application.operation;

import de.useweb.backend.domain.uml.UmlOperationId;

public interface OperationImplementation {
    UmlOperationId operationId();
    OperationExecutionResult execute(OperationExecutionContext context);
}
