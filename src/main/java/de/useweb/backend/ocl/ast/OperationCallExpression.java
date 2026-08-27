package de.useweb.backend.ocl.ast;

import java.util.List;

import de.useweb.backend.ocl.diagnostics.SourceRange;

public record OperationCallExpression(
        OclAstNode receiver,
        String operationName,
        List<OclAstNode> arguments,
        CallNavigationOperator navigationOperator,
        SourceRange operationRange,
        SourceRange sourceRange) implements OclAstNode {

    public OperationCallExpression {
        arguments = List.copyOf(arguments == null ? List.of() : arguments);
        navigationOperator = navigationOperator == null ? CallNavigationOperator.NONE : navigationOperator;
    }
}
