package de.useweb.backend.ocl.definition;

import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.useweb.backend.domain.uml.UmlAttribute;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlOperation;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.ocl.diagnostics.OclDiagnostic;
import de.useweb.backend.ocl.diagnostics.SourcePosition;
import de.useweb.backend.ocl.diagnostics.SourceRange;
import de.useweb.backend.ocl.parser.OclParser;

public final class OclDefinitionParser {
    private static final Pattern PROPERTY = Pattern.compile(
            "(?is)^\\s*context\\s+([A-Za-z_][\\w]*)::([A-Za-z_][\\w]*)\\s*(?::\\s*([A-Za-z_][\\w() ,]*))?\\s+(derive|init)\\s*:\\s*(.+?)\\s*$");
    private static final Pattern BODY = Pattern.compile(
            "(?is)^\\s*context\\s+([A-Za-z_][\\w]*)::([A-Za-z_][\\w]*)\\s*(?:\\([^)]*\\))?\\s*(?::\\s*([A-Za-z_][\\w() ,]*))?\\s+body\\s*:\\s*(.+?)\\s*$");
    private static final Pattern DEF = Pattern.compile(
            "(?is)^\\s*context\\s+([A-Za-z_][\\w]*)\\s+def\\s*:\\s*([A-Za-z_][\\w]*)(\\s*\\([^)]*\\))?\\s*:\\s*([A-Za-z_][\\w() ,]*)\\s*=\\s*(.+?)\\s*$");

    private final OclParser expressionParser = new OclParser();

    public OclDefinitionParseResult parse(OclDefinitionId id, String source, UmlModel model) {
        if (source == null || source.isBlank()) {
            return failure("EMPTY_DEFINITION", "OCL definition must not be blank.", source);
        }
        Matcher property = PROPERTY.matcher(source);
        if (property.matches()) {
            return property(id, source, model, property);
        }
        Matcher body = BODY.matcher(source);
        if (body.matches()) {
            return body(id, source, model, body);
        }
        Matcher definition = DEF.matcher(source);
        if (definition.matches()) {
            return additionalDefinition(id, source, model, definition);
        }
        return failure("INVALID_DEFINITION_DECLARATION",
                "Expected a derive, init, body or def context declaration.", source);
    }

    private OclDefinitionParseResult property(OclDefinitionId id, String source, UmlModel model, Matcher matcher) {
        UmlClass owner = owner(model, matcher.group(1));
        if (owner == null) return unknown("class", matcher.group(1), source);
        UmlAttribute attribute = model.findAttribute(owner.id(), matcher.group(2)).orElse(null);
        if (attribute == null) return unknown("attribute", matcher.group(2), source);
        OclDefinitionKind kind = matcher.group(4).toLowerCase(Locale.ROOT).equals("derive")
                ? OclDefinitionKind.DERIVE : OclDefinitionKind.INIT;
        return parsed(id, kind, owner, matcher.group(2), attribute.id(), null, attribute.type(), List.of(),
                matcher.group(5));
    }

    private OclDefinitionParseResult body(OclDefinitionId id, String source, UmlModel model, Matcher matcher) {
        UmlClass owner = owner(model, matcher.group(1));
        if (owner == null) return unknown("class", matcher.group(1), source);
        UmlOperation operation = model.typeConformanceOrder(owner.id()).stream()
                .map(model::findClass).flatMap(java.util.Optional::stream)
                .flatMap(type -> type.operations().stream())
                .filter(candidate -> candidate.name().equals(matcher.group(2))).findFirst().orElse(null);
        if (operation == null) return unknown("operation", matcher.group(2), source);
        return parsed(id, OclDefinitionKind.BODY, owner, operation.name(), null, operation.id(),
                operation.returnType(), operation.parameters(), matcher.group(4));
    }

    private OclDefinitionParseResult additionalDefinition(OclDefinitionId id, String source, UmlModel model,
            Matcher matcher) {
        UmlClass owner = owner(model, matcher.group(1));
        if (owner == null) return unknown("class", matcher.group(1), source);
        OclDefinitionKind kind = matcher.group(3) == null
                ? OclDefinitionKind.PROPERTY_DEF : OclDefinitionKind.OPERATION_DEF;
        return parsed(id, kind, owner, matcher.group(2), null, null,
                new UmlType(matcher.group(4).trim()), parameters(id, matcher.group(3)), matcher.group(5));
    }

    private List<de.useweb.backend.domain.uml.UmlParameter> parameters(OclDefinitionId definitionId,
            String declaration) {
        if (declaration == null || declaration.isBlank()) return List.of();
        String content = declaration.trim();
        content = content.substring(1, content.length() - 1).trim();
        if (content.isEmpty()) return List.of();
        List<de.useweb.backend.domain.uml.UmlParameter> parameters = new ArrayList<>();
        String[] parts = content.split(",");
        for (int index = 0; index < parts.length; index++) {
            String[] pair = parts[index].trim().split("\\s*:\\s*", 2);
            if (pair.length != 2 || pair[0].isBlank() || pair[1].isBlank()) {
                throw new IllegalArgumentException("Invalid def parameter declaration: " + parts[index]);
            }
            parameters.add(new de.useweb.backend.domain.uml.UmlParameter(
                    new de.useweb.backend.domain.uml.UmlParameterId(
                            definitionId.value() + "-parameter-" + index),
                    pair[0], new UmlType(pair[1])));
        }
        return List.copyOf(parameters);
    }

    private OclDefinitionParseResult parsed(OclDefinitionId id, OclDefinitionKind kind, UmlClass owner,
            String featureName, de.useweb.backend.domain.uml.UmlAttributeId attributeId,
            de.useweb.backend.domain.uml.UmlOperationId operationId, UmlType resultType,
            List<de.useweb.backend.domain.uml.UmlParameter> parameters, String expressionText) {
        var parsed = expressionParser.parse(expressionText);
        if (!parsed.success()) return new OclDefinitionParseResult(null, parsed.diagnostics());
        return new OclDefinitionParseResult(new OclDefinition(id, kind, owner.id(), featureName, attributeId,
                operationId, resultType, parameters, expressionText, parsed.ast()), List.of());
    }

    private UmlClass owner(UmlModel model, String name) {
        return model == null ? null : model.findClassByName(name).orElse(null);
    }

    private OclDefinitionParseResult unknown(String kind, String name, String source) {
        return failure("UNKNOWN_DEFINITION_CONTEXT", "Unknown " + kind + " '" + name + "'.", source);
    }

    private OclDefinitionParseResult failure(String code, String message, String source) {
        int length = source == null ? 0 : source.length();
        SourceRange range = new SourceRange(new SourcePosition(1, 1, 0),
                new SourcePosition(1, length + 1, length));
        return new OclDefinitionParseResult(null,
                List.of(OclDiagnostic.parserError(code, message, range,
                        List.of("context declaration"), source)));
    }
}
