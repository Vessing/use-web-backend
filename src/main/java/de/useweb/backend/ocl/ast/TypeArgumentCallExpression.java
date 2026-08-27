package de.useweb.backend.ocl.ast;

import de.useweb.backend.ocl.diagnostics.SourceRange;

public record TypeArgumentCallExpression(
        OclAstNode receiver,
        String operationName,
        String typeName,
        CallNavigationOperator navigationOperator,
        SourceRange operationRange,
        SourceRange typeRange,
        SourceRange sourceRange) implements OclAstNode {
    public TypeArgumentCallExpression(OclAstNode receiver, String operationName, String typeName,
            SourceRange operationRange, SourceRange typeRange, SourceRange sourceRange) {
        this(receiver, operationName, typeName, CallNavigationOperator.DOT,
                operationRange, typeRange, sourceRange);
    }

    public TypeArgumentCallExpression {
        navigationOperator = navigationOperator == null ? CallNavigationOperator.NONE : navigationOperator;
    }
}
