package de.useweb.backend.ocl.contract;

import java.util.List;

import de.useweb.backend.domain.snapshot.ObjectInstanceId;
import de.useweb.backend.ocl.diagnostics.OclDiagnostic;

public record OperationContractResult(
        OperationInvocationId invocationId,
        OperationContractId contractId,
        OperationContextReference reference,
        ObjectInstanceId receiverId,
        OperationConstraintKind constraintKind,
        Status status,
        List<OclDiagnostic> diagnostics) {

    public enum Status {
        CONTEXT_READY,
        CONTEXT_ERROR,
        SATISFIED,
        VIOLATED
    }

    public OperationContractResult {
        if (invocationId == null || contractId == null || reference == null || receiverId == null
                || constraintKind == null || status == null) {
            throw new IllegalArgumentException("Operation contract result metadata must be complete");
        }
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
        if (status == Status.CONTEXT_ERROR && diagnostics.isEmpty()) {
            throw new IllegalArgumentException("A context error requires diagnostics");
        }
    }

    public static OperationContractResult contextReady(OperationContext context, OperationConstraintKind kind) {
        return new OperationContractResult(context.invocationId(), new OperationContractId("context-ready"),
                context.reference(), context.receiverId(), kind,
                Status.CONTEXT_READY, List.of());
    }
}
