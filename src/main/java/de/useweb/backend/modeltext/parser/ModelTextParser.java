package de.useweb.backend.modeltext.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import de.useweb.backend.api.dto.ocl.OclDiagnosticDto;
import de.useweb.backend.api.dto.ocl.SourceRangeDto;

@Component
public class ModelTextParser {

    private static final Pattern MODEL_PATTERN = Pattern.compile("^model\\s+([A-Za-z_]\\w*)\\s*$");
    private static final Pattern CLASS_PATTERN = Pattern.compile("^class\\s+([A-Za-z_]\\w*)\\s*$");
    private static final Pattern ASSOCIATION_PATTERN = Pattern.compile("^association\\s+([A-Za-z_]\\w*)\\s+between\\s*$");
    private static final Pattern ASSOCIATION_END_PATTERN = Pattern.compile("^([A-Za-z_]\\w*)\\s*\\[([^]]+)](?:\\s+role\\s+([A-Za-z_]\\w*))?.*$");
    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile("^([A-Za-z_]\\w*)\\s*:\\s*([A-Za-z_]\\w*)\\s*$");
    private static final Pattern OPERATION_PATTERN = Pattern.compile("^([A-Za-z_]\\w*)\\s*\\(([^)]*)\\)\\s*(?::\\s*([A-Za-z_]\\w*))?\\s*$");
    private static final Pattern INVARIANT_PATTERN = Pattern.compile("^context\\s+([A-Za-z_]\\w*)\\s+inv\\s+([A-Za-z_]\\w*)\\s*:\\s*(.*)$");
    private static final Pattern CONTEXT_PATTERN = Pattern.compile("^context\\s+([A-Za-z_]\\w*)\\s*$");
    private static final Pattern CONTEXT_INVARIANT_PATTERN = Pattern.compile("^inv\\s+([A-Za-z_]\\w*)\\s*:\\s*(.*)$");

    public ModelTextParseResult parse(String modelText) {
        String[] rawLines = (modelText == null ? "" : modelText).replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        ParserState state = new ParserState(rawLines);
        while (state.hasMore()) {
            String line = state.currentCleanLine();
            if (line.isBlank()) {
                state.advance();
                continue;
            }
            Matcher modelMatcher = MODEL_PATTERN.matcher(line);
            Matcher classMatcher = CLASS_PATTERN.matcher(line);
            Matcher associationMatcher = ASSOCIATION_PATTERN.matcher(line);
            Matcher invariantMatcher = INVARIANT_PATTERN.matcher(line);
            Matcher contextMatcher = CONTEXT_PATTERN.matcher(line);
            if (modelMatcher.matches()) {
                state.modelName = modelMatcher.group(1);
                state.advance();
            } else if (classMatcher.matches()) {
                parseClass(state, classMatcher.group(1));
            } else if (associationMatcher.matches()) {
                parseAssociation(state, associationMatcher.group(1));
            } else if ("constraints".equals(line)) {
                state.advance();
            } else if (invariantMatcher.matches()) {
                parseInvariant(state, invariantMatcher);
            } else if (contextMatcher.matches()) {
                parseContextInvariantBlock(state, contextMatcher.group(1));
            } else {
                state.addUnsupported("UNSUPPORTED_SYNTAX", "Diese USE-Modelltext-Zeile wird im MVP-Apply-Flow nicht unterstuetzt.");
                state.advance();
            }
        }
        return new ModelTextParseResult(state.modelName, state.classes, state.associations, state.invariants, state.diagnostics);
    }

    private void parseClass(ParserState state, String className) {
        int startLine = state.lineNumber();
        state.advance();
        String section = "";
        List<ModelTextAttribute> attributes = new ArrayList<>();
        List<ModelTextOperation> operations = new ArrayList<>();
        while (state.hasMore()) {
            String line = state.currentCleanLine();
            if ("end".equals(line)) {
                state.advance();
                state.classes.add(new ModelTextClass(className, attributes, operations));
                return;
            }
            if ("attributes".equals(line) || "operations".equals(line)) {
                section = line;
                state.advance();
                continue;
            }
            if (line.isBlank()) {
                state.advance();
                continue;
            }
            if ("attributes".equals(section)) {
                Matcher matcher = ATTRIBUTE_PATTERN.matcher(line);
                if (matcher.matches()) {
                    attributes.add(new ModelTextAttribute(matcher.group(1), matcher.group(2)));
                } else {
                    state.addUnsupported("UNSUPPORTED_ATTRIBUTE_SYNTAX", "Dieses Attributformat wird im MVP nicht unterstuetzt.");
                }
            } else if ("operations".equals(section)) {
                parseOperation(state, line, operations);
            } else {
                state.addUnsupported("UNSUPPORTED_CLASS_SYNTAX", "Diese Klassenzeile wird im MVP nicht unterstuetzt.");
            }
            state.advance();
        }
        state.addError(startLine, "SYNTAX_ERROR", "Klasse '" + className + "' wurde nicht mit 'end' abgeschlossen.");
    }

    private void parseOperation(ParserState state, String line, List<ModelTextOperation> operations) {
        Matcher matcher = OPERATION_PATTERN.matcher(line);
        if (!matcher.matches()) {
            state.addUnsupported("UNSUPPORTED_OPERATION_SYNTAX", "Dieses Operationsformat wird im MVP nicht unterstuetzt.");
            return;
        }
        List<ModelTextParameter> parameters = new ArrayList<>();
        String rawParameters = matcher.group(2).trim();
        if (!rawParameters.isBlank()) {
            for (String rawParameter : rawParameters.split(",")) {
                Matcher parameterMatcher = ATTRIBUTE_PATTERN.matcher(rawParameter.trim());
                if (parameterMatcher.matches()) {
                    parameters.add(new ModelTextParameter(parameterMatcher.group(1), parameterMatcher.group(2)));
                } else {
                    state.addUnsupported("UNSUPPORTED_PARAMETER_SYNTAX", "Dieses Parameterformat wird im MVP nicht unterstuetzt.");
                }
            }
        }
        operations.add(new ModelTextOperation(matcher.group(1), matcher.group(3) == null ? "Void" : matcher.group(3), parameters));
    }

    private void parseAssociation(ParserState state, String associationName) {
        int startLine = state.lineNumber();
        state.advance();
        List<ModelTextAssociationEnd> ends = new ArrayList<>();
        while (state.hasMore()) {
            String line = state.currentCleanLine();
            if ("end".equals(line)) {
                state.advance();
                if (ends.size() == 2) {
                    state.associations.add(new ModelTextAssociation(associationName, ends));
                } else {
                    state.addError(startLine, "SYNTAX_ERROR", "Association '" + associationName + "' benoetigt genau zwei Enden.");
                }
                return;
            }
            if (!line.isBlank()) {
                Matcher matcher = ASSOCIATION_END_PATTERN.matcher(line);
                if (matcher.matches()) {
                    ends.add(new ModelTextAssociationEnd(matcher.group(1), matcher.group(2), matcher.group(3)));
                } else {
                    state.addUnsupported("UNSUPPORTED_ASSOCIATION_END_SYNTAX", "Dieses Association-Ende wird im MVP nicht unterstuetzt.");
                }
            }
            state.advance();
        }
        state.addError(startLine, "SYNTAX_ERROR", "Association '" + associationName + "' wurde nicht mit 'end' abgeschlossen.");
    }

    private void parseContextInvariantBlock(ParserState state, String contextClass) {
        int contextLine = state.lineNumber();
        boolean parsedInvariant = false;
        state.advance();
        while (state.hasMore()) {
            String line = state.currentCleanLine();
            if (line.isBlank()) {
                state.advance();
                continue;
            }
            if (isTopLevelStart(line)) {
                break;
            }
            Matcher invariantMatcher = CONTEXT_INVARIANT_PATTERN.matcher(line);
            if (invariantMatcher.matches()) {
                parseContextInvariant(state, contextClass, invariantMatcher);
                parsedInvariant = true;
            } else {
                state.addUnsupported("UNSUPPORTED_CONTEXT_SYNTAX", "Diese Context-Zeile wird im MVP nicht unterstuetzt.");
                state.advance();
            }
        }
        if (!parsedInvariant) {
            state.addError(contextLine, "SYNTAX_ERROR", "Context '" + contextClass + "' enthaelt keine unterstuetzte Invariante.");
        }
    }

    private void parseInvariant(ParserState state, Matcher matcher) {
        String expression = matcher.group(3).trim();
        if (expression.isBlank()) {
            int expressionLine = state.lineNumber() + 1;
            StringBuilder expressionBuilder = new StringBuilder();
            state.advance();
            while (state.hasMore()) {
                String line = state.currentCleanLine();
                if (line.isBlank()) {
                    state.advance();
                    continue;
                }
                if (isTopLevelStart(line)) {
                    break;
                }
                if (!expressionBuilder.isEmpty()) {
                    expressionBuilder.append(' ');
                }
                expressionBuilder.append(line);
                state.advance();
            }
            expression = expressionBuilder.toString().trim();
            if (expression.isBlank()) {
                state.addError(expressionLine, "SYNTAX_ERROR", "Invariante '" + matcher.group(2) + "' hat keinen OCL-Ausdruck.");
                return;
            }
        } else {
            state.advance();
        }
        state.invariants.add(new ModelTextInvariant(matcher.group(1), matcher.group(2), expression));
    }

    private void parseContextInvariant(ParserState state, String contextClass, Matcher matcher) {
        String expression = matcher.group(2).trim();
        if (expression.isBlank()) {
            int expressionLine = state.lineNumber() + 1;
            StringBuilder expressionBuilder = new StringBuilder();
            state.advance();
            while (state.hasMore()) {
                String line = state.currentCleanLine();
                if (line.isBlank()) {
                    state.advance();
                    continue;
                }
                if (isTopLevelStart(line) || CONTEXT_INVARIANT_PATTERN.matcher(line).matches()) {
                    break;
                }
                if (!expressionBuilder.isEmpty()) {
                    expressionBuilder.append(' ');
                }
                expressionBuilder.append(line);
                state.advance();
            }
            expression = expressionBuilder.toString().trim();
            if (expression.isBlank()) {
                state.addError(expressionLine, "SYNTAX_ERROR", "Invariante '" + matcher.group(1) + "' hat keinen OCL-Ausdruck.");
                return;
            }
        } else {
            state.advance();
        }
        state.invariants.add(new ModelTextInvariant(contextClass, matcher.group(1), expression));
    }

    private boolean isTopLevelStart(String line) {
        return MODEL_PATTERN.matcher(line).matches()
                || CLASS_PATTERN.matcher(line).matches()
                || ASSOCIATION_PATTERN.matcher(line).matches()
                || INVARIANT_PATTERN.matcher(line).matches()
                || CONTEXT_PATTERN.matcher(line).matches()
                || "constraints".equals(line);
    }

    private static final class ParserState {
        private final String[] lines;
        private int index;
        private String modelName;
        private final List<ModelTextClass> classes = new ArrayList<>();
        private final List<ModelTextAssociation> associations = new ArrayList<>();
        private final List<ModelTextInvariant> invariants = new ArrayList<>();
        private final List<OclDiagnosticDto> diagnostics = new ArrayList<>();

        private ParserState(String[] lines) {
            this.lines = lines;
        }

        private boolean hasMore() {
            return index < lines.length;
        }

        private String currentCleanLine() {
            return stripComment(lines[index]).trim();
        }

        private int lineNumber() {
            return index + 1;
        }

        private void advance() {
            index++;
        }

        private void addUnsupported(String code, String message) {
            diagnostics.add(new OclDiagnosticDto(
                    null,
                    "VALIDATION_ERROR",
                    code,
                    "WARNING",
                    message,
                    message,
                    message,
                    range(lineNumber()),
                    List.of(),
                    lines[index].trim(),
                    List.of(),
                    Map.of("line", lineNumber()),
                    "Diese Zeile vorerst manuell im Diagramm modellieren oder auf eine spaetere Import-Erweiterung warten."));
        }

        private void addError(int lineNumber, String code, String message) {
            diagnostics.add(new OclDiagnosticDto(
                    null,
                    "VALIDATION_ERROR",
                    code,
                    "ERROR",
                    message,
                    message,
                    message,
                    range(lineNumber),
                    List.of(),
                    null,
                    List.of(),
                    Map.of("line", lineNumber),
                    "Korrigiere den Modelltext an der markierten Stelle."));
        }

        private SourceRangeDto range(int lineNumber) {
            return new SourceRangeDto(lineNumber, 1, -1, lineNumber, 1, -1);
        }

        private String stripComment(String line) {
            int commentStart = line.indexOf("--");
            return commentStart >= 0 ? line.substring(0, commentStart) : line;
        }
    }

    public record ModelTextParseResult(
            String modelName,
            List<ModelTextClass> classes,
            List<ModelTextAssociation> associations,
            List<ModelTextInvariant> invariants,
            List<OclDiagnosticDto> diagnostics) {

        public boolean hasSupportedModelParts() {
            return !classes.isEmpty() || !associations.isEmpty() || !invariants.isEmpty();
        }
    }

    public record ModelTextClass(String name, List<ModelTextAttribute> attributes, List<ModelTextOperation> operations) {
    }

    public record ModelTextAttribute(String name, String type) {
    }

    public record ModelTextOperation(String name, String returnType, List<ModelTextParameter> parameters) {
    }

    public record ModelTextParameter(String name, String type) {
    }

    public record ModelTextAssociation(String name, List<ModelTextAssociationEnd> ends) {
    }

    public record ModelTextAssociationEnd(String className, String multiplicity, String roleName) {
    }

    public record ModelTextInvariant(String contextClass, String name, String expression) {
    }
}
