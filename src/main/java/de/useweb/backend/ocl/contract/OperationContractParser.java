package de.useweb.backend.ocl.contract;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.ocl.diagnostics.OclDiagnostic;
import de.useweb.backend.ocl.diagnostics.SourcePosition;
import de.useweb.backend.ocl.diagnostics.SourceRange;
import de.useweb.backend.ocl.parser.OclParser;

public final class OperationContractParser {
    private static final Pattern DECLARATION = Pattern.compile(
            "(?s)^\\s*context\\s+([A-Za-z_][A-Za-z0-9_]*)::([A-Za-z_][A-Za-z0-9_]*)"
                    + "\\s*(?:\\([^)]*\\))?\\s+(pre|post)\\s*"
                    + "(?:([A-Za-z_][A-Za-z0-9_]*)\\s*)?:\\s*(.+?)\\s*$");

    private final OclParser expressionParser = new OclParser();

    public OperationContractParseResult parse(OperationContractId id, String source, UmlModel model) {
        if (source == null || source.isBlank()) {
            return failure("EMPTY_CONTRACT", "Operation contract declaration must not be blank.", "");
        }
        Matcher matcher = DECLARATION.matcher(source);
        if (!matcher.matches()) {
            return failure("INVALID_CONTRACT_DECLARATION",
                    "Expected 'context Class::operation(...) pre|post name: expression'.", source);
        }
        var owner = model.findClassByName(matcher.group(1));
        if (owner.isEmpty()) {
            return new OperationContractParseResult(null,
                    List.of(OclDiagnostic.unknownClass(matcher.group(1), range(source))));
        }
        var operation = owner.get().operations().stream()
                .filter(candidate -> candidate.name().equals(matcher.group(2))).findFirst();
        if (operation.isEmpty()) {
            return failure("UNKNOWN_CONTEXT_OPERATION",
                    "Unknown operation '" + matcher.group(2) + "' in class '" + matcher.group(1) + "'.", source);
        }
        String expressionText = matcher.group(5);
        var expression = expressionParser.parse(expressionText);
        if (!expression.success()) {
            return new OperationContractParseResult(null, expression.diagnostics());
        }
        OperationConstraintKind kind = matcher.group(3).equals("pre")
                ? OperationConstraintKind.PRECONDITION : OperationConstraintKind.POSTCONDITION;
        String name = matcher.group(4) == null ? kind.name().toLowerCase() : matcher.group(4);
        return new OperationContractParseResult(new OperationContract(id, name,
                new OperationContextReference(owner.get().id(), operation.get().id()), kind,
                expressionText, expression.ast()), List.of());
    }

    private OperationContractParseResult failure(String code, String message, String source) {
        return new OperationContractParseResult(null, List.of(OclDiagnostic.parserError(code, message,
                range(source), List.of("operation contract declaration"), source)));
    }

    private static SourceRange range(String source) {
        int length = source == null ? 0 : source.length();
        return new SourceRange(new SourcePosition(1, 1, 0), new SourcePosition(1, length + 1, length));
    }
}
