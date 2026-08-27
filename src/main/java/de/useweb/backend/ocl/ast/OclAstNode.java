package de.useweb.backend.ocl.ast;

import de.useweb.backend.ocl.diagnostics.SourceRange;

public sealed interface OclAstNode permits
        AllInstancesExpression,
        AtPreExpression,
        BinaryExpression,
        CollectionLiteralExpression,
        EnumLiteralExpression,
        IfExpression,
        LetExpression,
        IterateExpression,
        IteratorExpression,
        LiteralExpression,
        OperationCallExpression,
        ParenthesizedExpression,
        PropertyAccessExpression,
        QualifiedPropertyAccessExpression,
        ResultExpression,
        SelfExpression,
        TupleExpression,
        TypeArgumentCallExpression,
        UnaryExpression,
        VariableExpression {

    SourceRange sourceRange();
}
