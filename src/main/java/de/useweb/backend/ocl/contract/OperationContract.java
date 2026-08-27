package de.useweb.backend.ocl.contract;

import de.useweb.backend.ocl.ast.OclAstNode;

public record OperationContract(
        OperationContractId id,
        String name,
        OperationContextReference reference,
        OperationConstraintKind kind,
        String expressionText,
        OclAstNode expression) {

    public OperationContract {
        if (id == null || reference == null || kind == null || expression == null) {
            throw new IllegalArgumentException("Operation contract metadata must be complete");
        }
        if (name == null || name.isBlank() || expressionText == null || expressionText.isBlank()) {
            throw new IllegalArgumentException("Operation contract name and expression must not be blank");
        }
    }
}
