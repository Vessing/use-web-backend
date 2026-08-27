package de.useweb.backend.ocl.diagnostics;

import java.util.List;

public record OclDiagnostic(
        OclDiagnosticPhase phase,
        String code,
        String severity,
        String message,
        SourceRange sourceRange,
        List<String> expected,
        String actual) {

    public OclDiagnostic {
        if (phase == null) {
            throw new IllegalArgumentException("phase must not be null");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (severity == null || severity.isBlank()) {
            throw new IllegalArgumentException("severity must not be blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        expected = List.copyOf(expected == null ? List.of() : expected);
    }

    public static OclDiagnostic syntaxError(String message, SourceRange sourceRange, List<String> expected, String actual) {
        return parserError("SYNTAX_ERROR", message, sourceRange, expected, actual);
    }

    public static OclDiagnostic lexerError(String code, String message, SourceRange sourceRange, List<String> expected, String actual) {
        return new OclDiagnostic(OclDiagnosticPhase.LEXER, code, "ERROR", message, sourceRange, expected, actual);
    }

    public static OclDiagnostic parserError(String code, String message, SourceRange sourceRange, List<String> expected, String actual) {
        return new OclDiagnostic(OclDiagnosticPhase.PARSER, code, "ERROR", message, sourceRange, expected, actual);
    }

    public static OclDiagnostic typeError(String message, SourceRange sourceRange, List<String> expected, String actual) {
        return new OclDiagnostic(OclDiagnosticPhase.TYPECHECK, "TYPE_ERROR", "ERROR", message, sourceRange, expected, actual);
    }

    public static OclDiagnostic unknownClass(String classId, SourceRange sourceRange) {
        return new OclDiagnostic(OclDiagnosticPhase.TYPECHECK,
                "UNKNOWN_CLASS",
                "ERROR",
                "Unknown context class '" + classId + "'.",
                sourceRange,
                List.of("known UML class"),
                classId);
    }

    public static OclDiagnostic unknownAttribute(String propertyName, SourceRange sourceRange) {
        return new OclDiagnostic(OclDiagnosticPhase.TYPECHECK,
                "UNKNOWN_ATTRIBUTE",
                "ERROR",
                "Unknown attribute or association role '" + propertyName + "'.",
                sourceRange,
                List.of("attribute", "association role"),
                propertyName);
    }

    public static OclDiagnostic unknownOperation(String operationName, String receiverSignature, SourceRange sourceRange) {
        return new OclDiagnostic(OclDiagnosticPhase.TYPECHECK,
                "INVALID_OPERATION",
                "ERROR",
                "Unknown operation or incompatible arguments for '" + operationName + "'.",
                sourceRange,
                List.of("matching operation signature"),
                receiverSignature);
    }

    public static OclDiagnostic evaluationError(String message, SourceRange sourceRange) {
        return new OclDiagnostic(OclDiagnosticPhase.EVALUATION, "EVALUATION_ERROR", "ERROR", message, sourceRange, List.of(), null);
    }

    public static OclDiagnostic evaluationError(String code, String message, SourceRange sourceRange) {
        return new OclDiagnostic(OclDiagnosticPhase.EVALUATION, code, "ERROR", message, sourceRange, List.of(), null);
    }
}
