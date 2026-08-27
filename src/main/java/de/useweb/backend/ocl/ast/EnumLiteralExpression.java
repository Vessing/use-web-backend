package de.useweb.backend.ocl.ast;

import de.useweb.backend.ocl.diagnostics.SourceRange;

public record EnumLiteralExpression(
        String enumerationName,
        String literalName,
        SourceRange enumerationRange,
        SourceRange literalRange,
        SourceRange sourceRange) implements OclAstNode {

    public EnumLiteralExpression {
        if (enumerationName == null || enumerationName.isBlank()
                || literalName == null || literalName.isBlank()) {
            throw new IllegalArgumentException("Enumeration and literal names must not be blank");
        }
        if (enumerationRange == null || literalRange == null || sourceRange == null) {
            throw new IllegalArgumentException("Enum literal source ranges must not be null");
        }
    }
}
