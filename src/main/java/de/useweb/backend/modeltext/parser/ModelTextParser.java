package de.useweb.backend.modeltext.parser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import de.useweb.backend.api.dto.ocl.OclDiagnosticDto;
import de.useweb.backend.api.dto.ocl.SourceRangeDto;
import de.useweb.backend.modeltext.parser.ModelTextLexer.LexResult;
import de.useweb.backend.modeltext.parser.ModelTextLexer.LexerProblem;
import de.useweb.backend.modeltext.parser.ModelTextLexer.Token;
import de.useweb.backend.modeltext.parser.ModelTextLexer.TokenType;

@Component
public class ModelTextParser {

    private static final Set<String> TOP_LEVEL_KEYWORDS = Set.of(
            "import", "model", "abstract", "class", "enum", "datatype", "association", "aggregation", "composition",
            "associationclass", "constraints", "context");
    private static final Set<String> CLASS_SECTIONS = Set.of("attributes", "operations", "constraints", "end");
    private final ModelTextLexer lexer = new ModelTextLexer();

    public ModelTextParseResult parse(String modelText) {
        return parse(lexer.lex(modelText));
    }

    public ModelTextParseResult parse(byte[] modelTextBytes) {
        return parse(lexer.lex(lexer.decode(modelTextBytes)));
    }

    private ModelTextParseResult parse(LexResult lexResult) {
        ParserState state = new ParserState(lexResult.tokens());
        lexResult.problems().forEach(state::addLexerProblem);

        while (!state.atEnd()) {
            state.skipSeparators();
            if (state.atEnd()) {
                break;
            }
            if (state.matchKeyword("import")) {
                parseImport(state);
            } else if (state.matchKeyword("model")) {
                parseModel(state);
            } else if (state.matchKeyword("abstract") && state.peekNonNewline(1).isKeyword("class")) {
                state.advance();
                parseClass(state, true);
            } else if (state.matchKeyword("abstract") && state.peekNonNewline(1).isKeyword("associationclass")) {
                state.advance();
                parseAssociationClass(state, true);
            } else if (state.matchKeyword("class")) {
                parseClass(state, false);
            } else if (state.matchKeyword("enum")) {
                parseEnumeration(state);
            } else if (state.matchKeyword("datatype")) {
                parseDataType(state);
            } else if (state.matchKeyword("association") || state.matchKeyword("aggregation")
                    || state.matchKeyword("composition")) {
                parseAssociation(state, state.current().text().toUpperCase());
            } else if (state.matchKeyword("associationclass")) {
                parseAssociationClass(state, false);
            } else if (state.matchKeyword("constraints")) {
                state.advance();
                state.recoverToLineBoundary();
            } else if (state.matchKeyword("context")) {
                parseContext(state);
            } else {
                Token unsupported = state.current();
                state.recoverStatement();
                state.addUnsupported(unsupported, state.previousOr(unsupported), "UNSUPPORTED_SYNTAX",
                        "Diese USE-Modelltext-Deklaration wird im Apply-Flow nicht unterstuetzt.");
            }
        }

        return new ModelTextParseResult(
                state.modelName,
                List.copyOf(state.imports),
                List.copyOf(state.classes),
                List.copyOf(state.enumerations),
                List.copyOf(state.dataTypes),
                List.copyOf(state.associations),
                List.copyOf(state.invariants),
                List.copyOf(state.operationContexts),
                List.copyOf(state.diagnostics));
    }

    private void parseImport(ParserState state) {
        Token importToken = state.advance();
        List<String> selectedNames = new ArrayList<>();
        boolean wildcard = false;
        if (state.consumeSymbol("*")) {
            wildcard = true;
        } else if (state.consumeSymbol("{")) {
            while (!state.atEnd() && !state.current().is("}")) {
                Token selected = state.consumeIdentifier("Importierter Elementname erwartet.");
                if (selected == null) {
                    state.recoverToAny(Set.of("}", ";"));
                    break;
                }
                selectedNames.add(selected.text());
                if (!state.consumeSymbol(",")) {
                    break;
                }
            }
            if (!state.consumeSymbol("}")) {
                state.addError(importToken, state.previousOr(importToken), "INVALID_IMPORT_SELECTION",
                        "Die selektive Importliste ist nicht abgeschlossen.");
            }
        } else {
            Token selected = state.consumeIdentifier("Importierter Elementname oder '*' erwartet.");
            if (selected != null) {
                selectedNames.add(selected.text());
            }
        }
        if (!state.matchKeyword("from")) {
            state.addError(importToken, state.previousOr(importToken), "INVALID_IMPORT_SYNTAX",
                    "Import benoetigt 'from' und einen relativen Dateipfad.");
            state.recoverStatement();
            return;
        }
        state.advance();
        Token source = state.current().type() == TokenType.STRING ? state.advance() : null;
        if (source == null) {
            state.addError(importToken, state.previousOr(importToken), "INVALID_IMPORT_SOURCE",
                    "Importquelle muss ein String-Literal sein.");
            state.recoverStatement();
            return;
        }
        state.imports.add(new ModelTextImport(unquote(source.text()), wildcard, List.copyOf(selectedNames),
                combine(importToken.range(), source.range())));
        state.recoverToLineBoundary();
    }

    private static String unquote(String value) {
        if (value == null || value.length() < 2) {
            return value == null ? "" : value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return value.substring(1, value.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\'", "'")
                    .replace("\\\\", "\\");
        }
        return value;
    }

    private static SourceRangeDto combine(SourceRangeDto start, SourceRangeDto end) {
        return new SourceRangeDto(start.startLine(), start.startColumn(), start.startOffset(),
                end.endLine(), end.endColumn(), end.endOffset());
    }

    private void parseModel(ParserState state) {
        Token declaration = state.advance();
        Token name = state.consumeIdentifier("Modellname erwartet.");
        if (name != null) {
            state.modelName = name.text();
        }
        state.recoverToLineBoundary();
        if (name == null) {
            state.addError(declaration, state.previousOr(declaration), "SYNTAX_ERROR", "Die model-Deklaration hat keinen Namen.");
        }
    }

    private void parseClass(ParserState state, boolean abstractClass) {
        Token classToken = state.advance();
        Token nameToken = state.consumeIdentifier("Klassenname erwartet.");
        if (nameToken == null) {
            state.recoverStatement();
            return;
        }

        boolean inlineEnd = false;
        List<String> superClassNames = new ArrayList<>();
        while (!state.atEnd() && !state.current().is(";") && state.current().type() != TokenType.NEWLINE) {
            if (state.current().isKeyword("end")) {
                inlineEnd = true;
            } else if (state.consumeSymbol("<")) {
                while (!state.atEnd() && state.current().type() != TokenType.NEWLINE
                        && !state.current().is(";") && !state.current().isKeyword("end")) {
                    Token superClass = state.consumeIdentifier("Name der Oberklasse erwartet.");
                    if (superClass != null) {
                        superClassNames.add(superClass.text());
                    }
                    if (!state.consumeSymbol(",")) {
                        break;
                    }
                }
                continue;
            }
            state.advance();
        }
        state.skipSeparators();
        if (inlineEnd) {
            state.classes.add(new ModelTextClass(nameToken.text(), abstractClass, List.copyOf(superClassNames),
                    List.of(), List.of()));
            return;
        }

        List<ModelTextAttribute> attributes = new ArrayList<>();
        List<ModelTextOperation> operations = new ArrayList<>();
        String section = "";
        while (!state.atEnd()) {
            state.skipSeparators();
            if (state.atEnd()) {
                break;
            }
            if (state.matchKeyword("end")) {
                state.advance();
                state.recoverToLineBoundary();
                state.classes.add(new ModelTextClass(nameToken.text(), abstractClass, List.copyOf(superClassNames),
                        List.copyOf(attributes), List.copyOf(operations)));
                return;
            }
            if (state.isTopLevelStart() && !state.matchKeyword("constraints")) {
                state.addError(classToken, state.previousOr(nameToken), "SYNTAX_ERROR",
                        "Klasse '" + nameToken.text() + "' wurde nicht mit 'end' abgeschlossen.");
                state.classes.add(new ModelTextClass(nameToken.text(), abstractClass, List.copyOf(superClassNames),
                        List.copyOf(attributes), List.copyOf(operations)));
                return;
            }
            if (state.matchKeyword("attributes") || state.matchKeyword("operations") || state.matchKeyword("constraints")) {
                section = state.advance().text().toLowerCase();
                state.recoverToLineBoundary();
                continue;
            }
            if ("attributes".equals(section)) {
                parseAttribute(state, attributes);
            } else if ("operations".equals(section)) {
                parseOperation(state, operations);
            } else if ("constraints".equals(section)) {
                parseClassifierInvariant(state, nameToken.text());
            } else {
                Token unsupported = state.current();
                state.recoverStatement();
                state.addUnsupported(unsupported, state.previousOr(unsupported), "UNSUPPORTED_CLASS_SYNTAX",
                        "Diese Klassenzeile wird nicht unterstuetzt.");
            }
        }
        state.addError(classToken, state.previousOr(nameToken), "SYNTAX_ERROR",
                "Klasse '" + nameToken.text() + "' wurde nicht mit 'end' abgeschlossen.");
    }

    private void parseAttribute(ParserState state, List<ModelTextAttribute> attributes) {
        Token start = state.current();
        Token name = state.consumeIdentifier("Attributname erwartet.");
        if (name == null || !state.consumeSymbol(":")) {
            state.recoverStatement();
            state.addUnsupported(start, state.previousOr(start), "UNSUPPORTED_ATTRIBUTE_SYNTAX",
                    "Dieses Attributformat wird nicht unterstuetzt.");
            return;
        }
        List<Token> typeTokens = state.collectType(Set.of(";", "="), Set.of("derive", "init"));
        String type = compact(typeTokens);
        if (type.isBlank()) {
            state.addError(name, state.previousOr(name), "SYNTAX_ERROR", "Attribut '" + name.text() + "' hat keinen Typ.");
        } else {
            String deriveExpression = null;
            String initExpression = null;
            if (!state.atEnd() && (state.current().isKeyword("derive") || state.current().isKeyword("init"))) {
                boolean derived = state.current().isKeyword("derive");
                state.advance();
                state.consumeSymbol("=");
                state.consumeSymbol(":");
                String body = expression(state.collectMemberExpression(start.range().startColumn()));
                deriveExpression = derived ? body : null;
                initExpression = derived ? null : body;
            } else if (state.consumeSymbol("=")) {
                initExpression = expression(state.collectMemberExpression(start.range().startColumn()));
            }
            attributes.add(new ModelTextAttribute(name.text(), type, deriveExpression != null,
                    deriveExpression, initExpression));
        }
    }

    private void parseOperation(ParserState state, List<ModelTextOperation> operations) {
        Token start = state.current();
        Token name = state.consumeIdentifier("Operationsname erwartet.");
        if (name == null || !state.consumeSymbol("(")) {
            state.recoverStatement();
            state.addUnsupported(start, state.previousOr(start), "UNSUPPORTED_OPERATION_SYNTAX",
                    "Dieses Operationsformat wird nicht unterstuetzt.");
            return;
        }

        List<ModelTextParameter> parameters = new ArrayList<>();
        boolean parameterListClosed = parseParameters(state, parameters);
        if (!parameterListClosed) {
            state.addError(start, state.previousOr(start), "UNBALANCED_TYPE_DELIMITER",
                    "Die Parameterliste von Operation '" + name.text() + "' ist nicht abgeschlossen.");
            state.recoverMember();
            return;
        }

        if (state.current().type() == TokenType.NEWLINE && state.peekAfterNewlines().is(":")) {
            state.skipNewlines();
        }
        String returnType = "Void";
        if (state.consumeSymbol(":")) {
            List<Token> returnTypeTokens = state.collectType(Set.of(";", "="), Set.of("begin"));
            returnType = compact(returnTypeTokens);
            if (returnType.isBlank()) {
                state.addError(name, state.previousOr(name), "SYNTAX_ERROR", "Operation '" + name.text() + "' hat keinen Rueckgabetyp.");
                returnType = "Void";
            }
        }
        boolean hasBody = state.current().is("=") || state.matchKeyword("begin")
                || state.current().type() == TokenType.NEWLINE
                && (state.peekAfterNewlines().is("=") || state.peekAfterNewlines().isKeyword("begin"));
        String bodyExpression = null;
        if (hasBody) {
            state.skipNewlines();
            state.consumeSymbol("=");
            boolean blockBody = state.matchKeyword("begin");
            bodyExpression = expression(state.collectOperationBody(blockBody, start.range().startColumn()));
        } else {
            state.recoverToLineBoundary();
        }
        List<ModelTextOperationContract> contracts = new ArrayList<>();
        parseOperationContracts(state, contracts, start.range().startColumn());
        operations.add(new ModelTextOperation(name.text(), returnType, List.copyOf(parameters), bodyExpression,
                List.copyOf(contracts)));
    }

    private void parseEnumeration(ParserState state) {
        Token declaration = state.advance();
        Token name = state.consumeIdentifier("Enumeration-Name erwartet.");
        if (name == null) {
            state.recoverStatement();
            return;
        }
        state.skipNewlines();
        if (!state.consumeSymbol("{")) {
            state.addError(declaration, state.previousOr(name), "SYNTAX_ERROR",
                    "Enumeration '" + name.text() + "' benoetigt einen Literalblock.");
            state.recoverStatement();
            return;
        }
        List<String> literals = new ArrayList<>();
        while (!state.atEnd() && !state.current().is("}")) {
            state.skipSeparators();
            if (state.current().is("}")) {
                break;
            }
            Token literal = state.consumeIdentifier("Enumeration-Literal erwartet.");
            if (literal != null) {
                literals.add(literal.text());
            } else {
                state.recoverToAny(Set.of(",", "}"));
            }
            state.consumeSymbol(",");
        }
        if (!state.consumeSymbol("}")) {
            state.addError(declaration, state.previousOr(name), "SYNTAX_ERROR",
                    "Enumeration '" + name.text() + "' wurde nicht mit '}' abgeschlossen.");
        }
        state.consumeSymbol(";");
        state.enumerations.add(new ModelTextEnumeration(name.text(), List.copyOf(literals)));
    }

    private void parseDataType(ParserState state) {
        Token declaration = state.advance();
        Token name = state.consumeIdentifier("DataType-Name erwartet.");
        if (name == null) {
            state.recoverStatement();
            return;
        }
        state.recoverToLineBoundary();
        List<ModelTextAttribute> properties = new ArrayList<>();
        List<ModelTextOperation> operations = new ArrayList<>();
        String section = "";
        while (!state.atEnd()) {
            state.skipSeparators();
            if (state.matchKeyword("end")) {
                state.advance();
                state.recoverToLineBoundary();
                state.dataTypes.add(dataType(name.text(), properties, operations));
                return;
            }
            if (state.isTopLevelStart() && !state.matchKeyword("constraints")) {
                state.addError(declaration, state.previousOr(name), "SYNTAX_ERROR",
                        "DataType '" + name.text() + "' wurde nicht mit 'end' abgeschlossen.");
                state.dataTypes.add(dataType(name.text(), properties, operations));
                return;
            }
            if (state.matchKeyword("attributes") || state.matchKeyword("operations") || state.matchKeyword("constraints")) {
                section = state.advance().text().toLowerCase();
                state.recoverToLineBoundary();
                continue;
            }
            if ("attributes".equals(section)) {
                parseAttribute(state, properties);
            } else if ("operations".equals(section)) {
                parseOperation(state, operations);
            } else if ("constraints".equals(section)) {
                Token unsupported = state.current();
                state.recoverStatement();
                state.addUnsupported(unsupported, state.previousOr(unsupported), "UNSUPPORTED_DATATYPE_CONSTRAINT",
                        "DataType-Constraints besitzen im aktuellen Objektmodell keinen auswertbaren Instanzkontext.");
            } else {
                Token unsupported = state.current();
                state.recoverStatement();
                state.addUnsupported(unsupported, state.previousOr(unsupported), "UNSUPPORTED_DATATYPE_SYNTAX",
                        "Diese DataType-Deklaration wird nicht unterstuetzt.");
            }
        }
        state.addError(declaration, state.previousOr(name), "SYNTAX_ERROR",
                "DataType '" + name.text() + "' wurde nicht mit 'end' abgeschlossen.");
        state.dataTypes.add(dataType(name.text(), properties, operations));
    }

    private ModelTextDataType dataType(String name, List<ModelTextAttribute> declaredProperties,
            List<ModelTextOperation> parsedOperations) {
        List<ModelTextAttribute> properties = new ArrayList<>(declaredProperties);
        List<ModelTextOperation> operations = new ArrayList<>();
        for (ModelTextOperation operation : parsedOperations) {
            if (operation.name().equals(name) && "Void".equals(operation.returnType())
                    && operation.bodyExpression() == null) {
                operation.parameters().forEach(parameter -> properties.add(new ModelTextAttribute(
                        parameter.name(), parameter.type(), false, null, null)));
            } else {
                operations.add(operation);
            }
        }
        return new ModelTextDataType(name, List.copyOf(properties), List.copyOf(operations));
    }

    private boolean parseParameters(ParserState state, List<ModelTextParameter> parameters) {
        while (!state.atEnd()) {
            state.skipNewlines();
            if (state.consumeSymbol(")")) {
                return true;
            }
            Token parameterStart = state.current();
            Token parameterName = state.consumeIdentifier("Parametername erwartet.");
            if (parameterName == null || !state.consumeSymbol(":")) {
                state.addUnsupported(parameterStart, state.previousOr(parameterStart), "UNSUPPORTED_PARAMETER_SYNTAX",
                        "Dieses Parameterformat wird nicht unterstuetzt.");
                state.recoverToAny(Set.of(",", ")"));
            } else {
                List<Token> typeTokens = state.collectType(Set.of(",", ")"), Set.of());
                String type = compact(typeTokens);
                if (type.isBlank()) {
                    state.addError(parameterName, state.previousOr(parameterName), "SYNTAX_ERROR",
                            "Parameter '" + parameterName.text() + "' hat keinen Typ.");
                } else {
                    parameters.add(new ModelTextParameter(parameterName.text(), type));
                }
            }
            if (state.consumeSymbol(",")) {
                continue;
            }
            if (state.consumeSymbol(")")) {
                return true;
            }
            if (state.current().type() == TokenType.NEWLINE) {
                continue;
            }
        }
        return false;
    }

    private void parseAssociation(ParserState state, String kind) {
        Token associationToken = state.advance();
        Token name = state.consumeIdentifier("Association-Name erwartet.");
        if (name == null) {
            state.recoverStatement();
            return;
        }
        state.skipSeparators();
        if (!state.matchKeyword("between")) {
            state.addError(associationToken, state.previousOr(name), "SYNTAX_ERROR",
                    "Association '" + name.text() + "' benoetigt das Schluesselwort 'between'.");
        } else {
            state.advance();
        }
        state.recoverToLineBoundary();

        List<ModelTextAssociationEnd> ends = new ArrayList<>();
        while (!state.atEnd()) {
            state.skipSeparators();
            if (state.matchKeyword("end")) {
                state.advance();
                state.recoverToLineBoundary();
                if (ends.size() >= 2) {
                    state.associations.add(new ModelTextAssociation(name.text(), kind, List.copyOf(ends), null));
                } else {
                    state.addError(associationToken, state.previousOr(name), "SYNTAX_ERROR",
                            "Association '" + name.text() + "' benoetigt mindestens zwei Enden.");
                }
                return;
            }
            if (state.isTopLevelStart()) {
                state.addError(associationToken, state.previousOr(name), "SYNTAX_ERROR",
                        "Association '" + name.text() + "' wurde nicht mit 'end' abgeschlossen.");
                return;
            }
            parseAssociationEnd(state, ends);
        }
        state.addError(associationToken, state.previousOr(name), "SYNTAX_ERROR",
                "Association '" + name.text() + "' wurde nicht mit 'end' abgeschlossen.");
    }

    private void parseAssociationClass(ParserState state, boolean abstractClass) {
        Token declaration = state.advance();
        Token name = state.consumeIdentifier("Association-Class-Name erwartet.");
        if (name == null) {
            state.recoverStatement();
            return;
        }
        List<String> superClassNames = new ArrayList<>();
        if (state.consumeSymbol("<")) {
            while (!state.atEnd() && state.current().type() != TokenType.NEWLINE && !state.current().is(";")) {
                Token superClass = state.consumeIdentifier("Name der Oberklasse erwartet.");
                if (superClass != null) {
                    superClassNames.add(superClass.text());
                }
                if (!state.consumeSymbol(",")) {
                    break;
                }
            }
        }
        state.skipSeparators();

        List<ModelTextAssociationEnd> ends = new ArrayList<>();
        List<ModelTextAttribute> attributes = new ArrayList<>();
        List<ModelTextOperation> operations = new ArrayList<>();
        String section = "";
        if (state.matchKeyword("between")) {
            state.advance();
            state.recoverToLineBoundary();
            section = "between";
        }
        while (!state.atEnd()) {
            state.skipSeparators();
            if (state.matchKeyword("end")) {
                state.advance();
                state.recoverToLineBoundary();
                state.classes.add(new ModelTextClass(name.text(), abstractClass, List.copyOf(superClassNames),
                        List.copyOf(attributes), List.copyOf(operations)));
                if (ends.size() >= 2) {
                    state.associations.add(new ModelTextAssociation(name.text(), "ASSOCIATION_CLASS",
                            List.copyOf(ends), name.text()));
                } else {
                    state.addError(declaration, state.previousOr(name), "SYNTAX_ERROR",
                            "Association Class '" + name.text() + "' benoetigt mindestens zwei Enden.");
                }
                return;
            }
            if (state.matchKeyword("attributes") || state.matchKeyword("operations") || state.matchKeyword("constraints")) {
                section = state.advance().text().toLowerCase();
                state.recoverToLineBoundary();
                continue;
            }
            if (state.isTopLevelStart() && !state.matchKeyword("constraints")) {
                state.addError(declaration, state.previousOr(name), "SYNTAX_ERROR",
                        "Association Class '" + name.text() + "' wurde nicht mit 'end' abgeschlossen.");
                return;
            }
            if ("between".equals(section)) {
                parseAssociationEnd(state, ends);
            } else if ("attributes".equals(section)) {
                parseAttribute(state, attributes);
            } else if ("operations".equals(section)) {
                parseOperation(state, operations);
            } else if ("constraints".equals(section)) {
                parseClassifierInvariant(state, name.text());
            } else {
                Token unsupported = state.current();
                state.recoverStatement();
                state.addUnsupported(unsupported, state.previousOr(unsupported), "UNSUPPORTED_ASSOCIATION_CLASS_SYNTAX",
                        "Diese Association-Class-Deklaration wird nicht unterstuetzt.");
            }
        }
        state.addError(declaration, state.previousOr(name), "SYNTAX_ERROR",
                "Association Class '" + name.text() + "' wurde nicht mit 'end' abgeschlossen.");
    }

    private void parseAssociationEnd(ParserState state, List<ModelTextAssociationEnd> ends) {
        Token start = state.current();
        Token className = state.consumeIdentifier("Classifier eines Association Ends erwartet.");
        if (className == null || !state.consumeSymbol("[")) {
            state.recoverStatement();
            state.addUnsupported(start, state.previousOr(start), "UNSUPPORTED_ASSOCIATION_END_SYNTAX",
                    "Dieses Association-Ende wird nicht unterstuetzt.");
            return;
        }
        List<Token> multiplicityTokens = state.collectBalancedContent("[", "]");
        if (!state.consumeSymbol("]")) {
            state.addError(start, state.previousOr(start), "UNBALANCED_MULTIPLICITY",
                    "Die Multiplizitaet des Association Ends ist nicht abgeschlossen.");
            state.recoverStatement();
            return;
        }
        String roleName = null;
        boolean ordered = false;
        boolean unique = true;
        boolean derived = false;
        boolean union = false;
        String deriveExpression = null;
        List<String> subsettedRoleNames = new ArrayList<>();
        List<String> redefinedRoleNames = new ArrayList<>();
        List<ModelTextParameter> qualifiers = new ArrayList<>();
        while (!state.atEnd() && state.current().type() != TokenType.NEWLINE && !state.current().is(";")) {
            if (state.matchKeyword("role")) {
                state.advance();
                Token role = state.consumeIdentifier("Rollenname erwartet.");
                roleName = role == null ? null : role.text();
            } else if (state.matchKeyword("ordered")) {
                ordered = true;
                state.advance();
            } else if (state.matchKeyword("nonunique")) {
                unique = false;
                state.advance();
            } else if (state.matchKeyword("subsets")) {
                state.advance();
                Token role = state.consumeIdentifier("Name des subsettierten Association Ends erwartet.");
                if (role != null) subsettedRoleNames.add(role.text());
            } else if (state.matchKeyword("redefines")) {
                state.advance();
                Token role = state.consumeIdentifier("Name des redefinierten Association Ends erwartet.");
                if (role != null) redefinedRoleNames.add(role.text());
            } else if (state.matchKeyword("union")) {
                union = true;
                derived = true;
                state.advance();
            } else if (state.matchKeyword("qualifier")) {
                state.advance();
                if (!state.consumeSymbol("(") || !parseParameters(state, qualifiers)) {
                    state.addError(start, state.previousOr(start), "SYNTAX_ERROR",
                            "Qualifier-Deklaration ist nicht abgeschlossen.");
                    state.recoverStatement();
                    return;
                }
            } else if (state.matchKeyword("derived") || state.matchKeyword("derive")) {
                derived = true;
                state.advance();
                if (state.consumeSymbol("(")) {
                    List<ModelTextParameter> ignoredDeriveParameters = new ArrayList<>();
                    if (!parseParameters(state, ignoredDeriveParameters)) {
                        state.addError(start, state.previousOr(start), "SYNTAX_ERROR",
                                "Parameterliste des derived Association Ends ist nicht abgeschlossen.");
                        state.recoverStatement();
                        return;
                    }
                }
                if (state.consumeSymbol("=")) {
                    deriveExpression = expression(state.collectAssociationEndExpression(start.range().startColumn()));
                }
                break;
            } else {
                state.advance();
            }
        }
        state.consumeSymbol(";");
        state.skipNewlines();
        String multiplicity = compact(multiplicityTokens);
        if (multiplicity.isBlank()) {
            state.addError(start, state.previousOr(start), "SYNTAX_ERROR", "Association End hat keine Multiplizitaet.");
        } else {
            ends.add(new ModelTextAssociationEnd(className.text(), multiplicity, roleName, ordered, unique,
                    derived, deriveExpression, union, List.copyOf(subsettedRoleNames),
                    List.copyOf(redefinedRoleNames), List.copyOf(qualifiers)));
        }
    }

    private void parseContext(ParserState state) {
        Token contextToken = state.advance();
        Token first = state.consumeIdentifier("Context-Classifier oder Context-Variable erwartet.");
        if (first == null) {
            state.recoverStatement();
            return;
        }
        if (state.consumeSymbol("::")) {
            parseOperationContext(state, contextToken, first.text());
            return;
        }
        List<String> contextVariables = new ArrayList<>();
        String contextClass = first.text();
        if (state.current().is(",") || state.current().is(":")) {
            contextVariables.add(first.text());
            while (state.consumeSymbol(",")) {
                Token variable = state.consumeIdentifier("Context-Variablenname erwartet.");
                if (variable != null) contextVariables.add(variable.text());
            }
            if (!state.consumeSymbol(":")) {
                state.addError(contextToken, state.previousOr(first), "SYNTAX_ERROR",
                        "Context-Variablen benoetigen einen Classifier nach ':'.");
                state.recoverStatement();
                return;
            }
            Token classifier = state.consumeIdentifier("Context-Classifier erwartet.");
            if (classifier == null) {
                state.recoverStatement();
                return;
            }
            contextClass = classifier.text();
        }
        if (state.matchKeyword("inv")) {
            parseInvariant(state, contextClass, contextVariables, false);
            return;
        }
        if (state.matchKeyword("existential")) {
            parseClassifierInvariant(state, contextClass, contextVariables);
            return;
        }
        state.recoverToLineBoundary();
        boolean parsedInvariant = false;
        while (!state.atEnd()) {
            state.skipSeparators();
            if (state.matchKeyword("inv") || state.matchKeyword("existential")) {
                parseClassifierInvariant(state, contextClass, contextVariables);
                parsedInvariant = true;
            } else if (state.isTopLevelStart()) {
                break;
            } else {
                Token unsupported = state.current();
                state.recoverStatement();
                state.addUnsupported(unsupported, state.previousOr(unsupported), "UNSUPPORTED_CONTEXT_SYNTAX",
                        "Diese Context-Deklaration wird nicht unterstuetzt.");
            }
        }
        if (!parsedInvariant) {
            state.addError(contextToken, state.previousOr(first), "SYNTAX_ERROR",
                    "Context '" + contextClass + "' enthaelt keine Invariante.");
        }
    }

    private void parseClassifierInvariant(ParserState state, String contextClass) {
        parseClassifierInvariant(state, contextClass, List.of());
    }

    private void parseClassifierInvariant(ParserState state, String contextClass, List<String> contextVariables) {
        boolean existential = false;
        if (state.matchKeyword("existential")) {
            existential = true;
            state.advance();
            if (!state.matchKeyword("inv")) {
                state.addError(state.current(), state.current(), "SYNTAX_ERROR", "Nach 'existential' wird 'inv' erwartet.");
                state.recoverStatement();
                return;
            }
        }
        parseInvariant(state, contextClass, contextVariables, existential);
    }

    private void parseInvariant(ParserState state, String contextClass, List<String> contextVariables,
            boolean existential) {
        Token invariantToken = state.advance();
        Token name = null;
        if (state.current().type() == TokenType.IDENTIFIER) {
            name = state.advance();
        }
        if (!state.consumeSymbol(":")) {
            state.addError(invariantToken, state.previousOr(invariantToken), "SYNTAX_ERROR", "Invariante benoetigt ':'.");
            state.recoverStatement();
            return;
        }
        List<Token> expressionTokens = state.collectInvariantExpression();
        String expression = expression(expressionTokens);
        String invariantName = name == null ? "invariantLine" + invariantToken.range().startLine() : name.text();
        if (expression.isBlank()) {
            state.addError(invariantToken, state.previousOr(invariantToken), "SYNTAX_ERROR",
                    "Invariante '" + invariantName + "' hat keinen OCL-Ausdruck.");
        } else {
            state.invariants.add(new ModelTextInvariant(contextClass, invariantName, expression,
                    List.copyOf(contextVariables), existential));
        }
    }

    private void parseOperationContext(ParserState state, Token contextToken, String contextClass) {
        Token operationName = state.consumeIdentifier("Operationsname im Context erwartet.");
        if (operationName == null || !state.consumeSymbol("(")) {
            state.addError(contextToken, state.previousOr(contextToken), "SYNTAX_ERROR",
                    "Operation-Context benoetigt eine Operationssignatur.");
            state.recoverStatement();
            return;
        }
        List<ModelTextParameter> parameters = new ArrayList<>();
        if (!parseParameters(state, parameters)) {
            state.addError(contextToken, state.previousOr(contextToken), "UNBALANCED_TYPE_DELIMITER",
                    "Parameterliste des Operation-Contexts ist nicht abgeschlossen.");
            state.recoverStatement();
            return;
        }
        String returnType = "Void";
        if (state.consumeSymbol(":")) {
            returnType = compact(state.collectType(Set.of(";"), Set.of("pre", "post")));
            if (returnType.isBlank()) returnType = "Void";
        }
        state.recoverToLineBoundary();
        List<ModelTextOperationContract> contracts = new ArrayList<>();
        parseOperationContracts(state, contracts, contextToken.range().startColumn());
        if (contracts.isEmpty()) {
            state.addError(contextToken, state.previousOr(contextToken), "SYNTAX_ERROR",
                    "Operation-Context enthaelt keine Pre- oder Postcondition.");
        } else {
            state.operationContexts.add(new ModelTextOperationContext(contextClass, operationName.text(),
                    List.copyOf(parameters), returnType, List.copyOf(contracts)));
        }
    }

    private void parseOperationContracts(ParserState state, List<ModelTextOperationContract> contracts,
            int declarationColumn) {
        while (!state.atEnd()) {
            int savedIndex = state.index;
            state.skipNewlines();
            if (!state.matchKeyword("pre") && !state.matchKeyword("post")) {
                state.index = savedIndex;
                return;
            }
            Token kind = state.advance();
            Token name = null;
            if (state.current().type() == TokenType.IDENTIFIER && state.peekNonNewline(1).is(":")) {
                name = state.advance();
            }
            if (!state.consumeSymbol(":")) {
                state.addError(kind, state.previousOr(kind), "SYNTAX_ERROR", "Pre-/Postcondition benoetigt ':'.");
                state.recoverStatement();
                continue;
            }
            String expression = expression(state.collectContractExpression(declarationColumn));
            String contractName = name == null
                    ? kind.text().toLowerCase() + "Line" + kind.range().startLine()
                    : name.text();
            if (expression.isBlank()) {
                state.addError(kind, state.previousOr(kind), "SYNTAX_ERROR",
                        "Operationsvertrag '" + contractName + "' hat keinen OCL-Ausdruck.");
            } else {
                contracts.add(new ModelTextOperationContract(kind.text().toUpperCase(), contractName, expression));
            }
        }
    }

    private static String compact(List<Token> tokens) {
        StringBuilder result = new StringBuilder();
        for (Token token : tokens) {
            if (token.type() == TokenType.NEWLINE) {
                continue;
            }
            if (token.is(",") && !result.isEmpty()) {
                result.append(", ");
            } else {
                result.append(token.text());
            }
        }
        return result.toString().trim();
    }

    private static String expression(List<Token> tokens) {
        StringBuilder result = new StringBuilder();
        Token previous = null;
        for (Token token : tokens) {
            if (token.type() == TokenType.NEWLINE) {
                if (!result.isEmpty() && result.charAt(result.length() - 1) != ' ') {
                    result.append(' ');
                }
                continue;
            }
            if (previous != null && needsSpace(previous, token, result)) {
                result.append(' ');
            }
            result.append(token.text());
            previous = token;
        }
        return result.toString().trim().replaceAll("\\s+", " ");
    }

    private static boolean needsSpace(Token previous, Token current, StringBuilder result) {
        if (result.isEmpty() || result.charAt(result.length() - 1) == ' ') {
            return false;
        }
        if (Set.of(")", "]", "}", ",", ";", ".", "::", "->").contains(current.text())) {
            return false;
        }
        if (Set.of("(", "[", "{", ".", "::", "->", "#", "@").contains(previous.text())) {
            return false;
        }
        if (current.is("(") && previous.type() == TokenType.IDENTIFIER) {
            return false;
        }
        return true;
    }

    private static final class ParserState {
        private final List<Token> tokens;
        private int index;
        private String modelName;
        private final List<ModelTextClass> classes = new ArrayList<>();
        private final List<ModelTextImport> imports = new ArrayList<>();
        private final List<ModelTextEnumeration> enumerations = new ArrayList<>();
        private final List<ModelTextDataType> dataTypes = new ArrayList<>();
        private final List<ModelTextAssociation> associations = new ArrayList<>();
        private final List<ModelTextInvariant> invariants = new ArrayList<>();
        private final List<ModelTextOperationContext> operationContexts = new ArrayList<>();
        private final List<OclDiagnosticDto> diagnostics = new ArrayList<>();

        private ParserState(List<Token> tokens) {
            this.tokens = tokens;
        }

        private Token current() {
            return tokens.get(index);
        }

        private Token previousOr(Token fallback) {
            return index == 0 ? fallback : tokens.get(Math.min(index - 1, tokens.size() - 1));
        }

        private boolean atEnd() {
            return current().type() == TokenType.EOF;
        }

        private Token advance() {
            Token token = current();
            if (!atEnd()) {
                index++;
            }
            return token;
        }

        private boolean matchKeyword(String keyword) {
            return current().isKeyword(keyword);
        }

        private boolean consumeSymbol(String symbol) {
            if (current().is(symbol)) {
                advance();
                return true;
            }
            return false;
        }

        private Token consumeIdentifier(String message) {
            if (current().type() == TokenType.IDENTIFIER) {
                return advance();
            }
            addError(current(), current(), "SYNTAX_ERROR", message);
            return null;
        }

        private void skipSeparators() {
            while (!atEnd() && (current().type() == TokenType.NEWLINE || current().is(";"))) {
                advance();
            }
        }

        private void skipNewlines() {
            while (!atEnd() && current().type() == TokenType.NEWLINE) {
                advance();
            }
        }

        private boolean isTopLevelStart() {
            if (current().type() != TokenType.IDENTIFIER) {
                return false;
            }
            String keyword = current().text().toLowerCase();
            if (!TOP_LEVEL_KEYWORDS.contains(keyword)) {
                return false;
            }
            if (Set.of("import", "model", "abstract", "class", "enum", "datatype", "association",
                    "aggregation", "composition", "associationclass").contains(keyword)) {
                return peekNonNewline(1).type() == TokenType.IDENTIFIER;
            }
            return true;
        }

        private List<Token> collectType(Set<String> stopSymbols, Set<String> stopKeywords) {
            List<Token> result = new ArrayList<>();
            Deque<String> delimiters = new ArrayDeque<>();
            while (!atEnd()) {
                Token token = current();
                if (token.type() == TokenType.NEWLINE && !delimiters.isEmpty() && looksLikeMemberAfterNewline()) {
                    break;
                }
                if (token.type() == TokenType.NEWLINE && delimiters.isEmpty()) {
                    break;
                }
                if (delimiters.isEmpty() && (stopSymbols.contains(token.text())
                        || token.type() == TokenType.IDENTIFIER && stopKeywords.contains(token.text().toLowerCase()))) {
                    break;
                }
                if (token.type() == TokenType.NEWLINE) {
                    advance();
                    continue;
                }
                if (isOpening(token.text())) {
                    delimiters.push(matchingClose(token.text()));
                } else if (isClosing(token.text())) {
                    if (delimiters.isEmpty() || !delimiters.peek().equals(token.text())) {
                        break;
                    }
                    delimiters.pop();
                }
                result.add(advance());
            }
            if (!delimiters.isEmpty()) {
                Token start = result.isEmpty() ? current() : result.getFirst();
                addError(start, previousOr(start), "UNBALANCED_TYPE_DELIMITER",
                        "Der Typausdruck enthaelt nicht balancierte Klammern; erwartet wird '" + delimiters.peek() + "'.");
            }
            return result;
        }

        private List<Token> collectBalancedContent(String opening, String closing) {
            List<Token> result = new ArrayList<>();
            Deque<String> delimiters = new ArrayDeque<>();
            while (!atEnd()) {
                Token token = current();
                if (token.is(closing) && delimiters.isEmpty()) {
                    break;
                }
                if (token.is(opening) || isOpening(token.text())) {
                    delimiters.push(matchingClose(token.text()));
                } else if (isClosing(token.text())) {
                    if (delimiters.isEmpty() || !delimiters.peek().equals(token.text())) {
                        break;
                    }
                    delimiters.pop();
                }
                if (token.type() != TokenType.NEWLINE) {
                    result.add(token);
                }
                advance();
            }
            return result;
        }

        private List<Token> collectInvariantExpression() {
            List<Token> result = new ArrayList<>();
            Deque<String> delimiters = new ArrayDeque<>();
            while (!atEnd()) {
                Token token = current();
                if (token.is(";") && delimiters.isEmpty()) {
                    advance();
                    break;
                }
                if (atDeclarationStartAfterNewline(result)
                        && (isTopLevelStart() || matchKeyword("inv") || matchKeyword("existential")
                                || matchKeyword("end"))) {
                    break;
                }
                if (isOpening(token.text())) {
                    delimiters.push(matchingClose(token.text()));
                } else if (isClosing(token.text()) && !delimiters.isEmpty() && delimiters.peek().equals(token.text())) {
                    delimiters.pop();
                }
                result.add(advance());
            }
            return result;
        }

        private List<Token> collectContractExpression(int declarationColumn) {
            List<Token> result = new ArrayList<>();
            Deque<String> delimiters = new ArrayDeque<>();
            boolean afterNewline = false;
            while (!atEnd()) {
                Token token = current();
                if (token.is(";") && delimiters.isEmpty()) {
                    advance();
                    break;
                }
                if (afterNewline && delimiters.isEmpty()
                        && (token.isKeyword("pre") || token.isKeyword("post") || token.isKeyword("context")
                                || CLASS_SECTIONS.contains(token.text().toLowerCase())
                                || token.range().startColumn() <= declarationColumn
                                        && (looksLikeOperationStart() || looksLikeAttributeStart()))) {
                    break;
                }
                if (token.type() == TokenType.NEWLINE) {
                    afterNewline = true;
                    result.add(advance());
                    continue;
                }
                afterNewline = false;
                if (isOpening(token.text())) delimiters.push(matchingClose(token.text()));
                else if (isClosing(token.text()) && !delimiters.isEmpty() && delimiters.peek().equals(token.text())) delimiters.pop();
                result.add(advance());
            }
            return result;
        }

        private boolean atDeclarationStartAfterNewline(List<Token> collected) {
            return !collected.isEmpty() && collected.getLast().type() == TokenType.NEWLINE;
        }

        private List<Token> collectMemberExpression(int declarationColumn) {
            return collectBody(false, declarationColumn);
        }

        private List<Token> collectAssociationEndExpression(int declarationColumn) {
            List<Token> result = new ArrayList<>();
            Deque<String> delimiters = new ArrayDeque<>();
            boolean afterNewline = false;
            while (!atEnd()) {
                Token token = current();
                if (token.is(";") && delimiters.isEmpty()) {
                    advance();
                    break;
                }
                if (afterNewline && delimiters.isEmpty()
                        && (token.isKeyword("end") || token.isKeyword("attributes") || token.isKeyword("operations")
                                || token.range().startColumn() <= declarationColumn && looksLikeAssociationEndStart())) {
                    break;
                }
                if (token.type() == TokenType.NEWLINE) {
                    afterNewline = true;
                    result.add(advance());
                    continue;
                }
                afterNewline = false;
                if (isOpening(token.text())) {
                    delimiters.push(matchingClose(token.text()));
                } else if (isClosing(token.text()) && !delimiters.isEmpty() && delimiters.peek().equals(token.text())) {
                    delimiters.pop();
                }
                result.add(advance());
            }
            return result;
        }

        private boolean looksLikeAssociationEndStart() {
            return current().type() == TokenType.IDENTIFIER && peekNonNewline(1).is("[");
        }

        private List<Token> collectOperationBody(boolean blockBody, int declarationColumn) {
            return collectBody(blockBody, declarationColumn);
        }

        private List<Token> collectBody(boolean blockBody, int declarationColumn) {
            List<Token> result = new ArrayList<>();
            Deque<String> delimiters = new ArrayDeque<>();
            boolean afterNewline = false;
            int blockDepth = 0;
            while (!atEnd()) {
                Token token = current();
                if (blockBody && token.type() == TokenType.IDENTIFIER) {
                    if (token.isKeyword("begin") || token.isKeyword("if") || token.isKeyword("for")
                            || token.isKeyword("while")) {
                        blockDepth++;
                    } else if (token.isKeyword("end")) {
                        blockDepth--;
                        result.add(advance());
                        if (blockDepth <= 0) {
                            consumeSymbol(";");
                            return result;
                        }
                        continue;
                    }
                }
                if (!blockBody && token.is(";") && delimiters.isEmpty()) {
                    advance();
                    return result;
                }
                if (!blockBody && afterNewline && delimiters.isEmpty()
                        && (CLASS_SECTIONS.contains(token.text().toLowerCase())
                                || token.isKeyword("pre") || token.isKeyword("post")
                                || token.range().startColumn() <= declarationColumn
                                        && (looksLikeOperationStart() || looksLikeAttributeStart()))) {
                    return result;
                }
                if (token.type() == TokenType.NEWLINE) {
                    afterNewline = true;
                    result.add(advance());
                    continue;
                }
                afterNewline = false;
                if (isOpening(token.text())) {
                    delimiters.push(matchingClose(token.text()));
                } else if (isClosing(token.text()) && !delimiters.isEmpty() && delimiters.peek().equals(token.text())) {
                    delimiters.pop();
                }
                result.add(advance());
            }
            return result;
        }

        private boolean looksLikeOperationStart() {
            return current().type() == TokenType.IDENTIFIER && peekNonNewline(1).is("(");
        }

        private boolean looksLikeAttributeStart() {
            return current().type() == TokenType.IDENTIFIER && peekNonNewline(1).is(":");
        }

        private boolean looksLikeMemberAfterNewline() {
            int nextIndex = index + 1;
            while (nextIndex < tokens.size() && tokens.get(nextIndex).type() == TokenType.NEWLINE) {
                nextIndex++;
            }
            if (nextIndex >= tokens.size()) {
                return false;
            }
            Token next = tokens.get(nextIndex);
            if (next.type() != TokenType.IDENTIFIER) {
                return false;
            }
            if (CLASS_SECTIONS.contains(next.text().toLowerCase())) {
                return true;
            }
            int followingIndex = nextIndex + 1;
            while (followingIndex < tokens.size() && tokens.get(followingIndex).type() == TokenType.NEWLINE) {
                followingIndex++;
            }
            if (followingIndex >= tokens.size()) {
                return false;
            }
            String following = tokens.get(followingIndex).text();
            return ":".equals(following) || "(".equals(following);
        }

        private Token peekNonNewline(int distance) {
            int target = index + distance;
            while (target < tokens.size() && tokens.get(target).type() == TokenType.NEWLINE) {
                target++;
            }
            return tokens.get(Math.min(target, tokens.size() - 1));
        }

        private Token peekAfterNewlines() {
            int target = index;
            while (target < tokens.size() && tokens.get(target).type() == TokenType.NEWLINE) {
                target++;
            }
            return tokens.get(Math.min(target, tokens.size() - 1));
        }

        private void recoverMember() {
            while (!atEnd()) {
                if (current().type() == TokenType.NEWLINE || current().is(";")) {
                    advance();
                    return;
                }
                if (CLASS_SECTIONS.contains(current().text().toLowerCase())) {
                    return;
                }
                advance();
            }
        }

        private void recoverStatement() {
            recoverToLineBoundary();
            if (!atEnd() && current().type() == TokenType.NEWLINE) {
                advance();
            }
        }

        private void recoverToLineBoundary() {
            while (!atEnd() && current().type() != TokenType.NEWLINE && !current().is(";")) {
                advance();
            }
            if (!atEnd() && current().is(";")) {
                advance();
            }
        }

        private void recoverToAny(Set<String> symbols) {
            while (!atEnd() && !symbols.contains(current().text())) {
                advance();
            }
        }

        private void addLexerProblem(LexerProblem problem) {
            diagnostics.add(diagnostic(problem.code(), "ERROR", problem.message(), problem.range(), problem.actual()));
        }

        private void addUnsupported(Token start, Token end, String code, String message) {
            diagnostics.add(diagnostic(code, "WARNING", message, combine(start.range(), end.range()), start.text()));
        }

        private void addError(Token start, Token end, String code, String message) {
            diagnostics.add(diagnostic(code, "ERROR", message, combine(start.range(), end.range()), start.text()));
        }

        private OclDiagnosticDto diagnostic(String code, String severity, String message, SourceRangeDto range, String actual) {
            return new OclDiagnosticDto(
                    null,
                    "VALIDATION_ERROR",
                    code,
                    severity,
                    message,
                    message,
                    message,
                    range,
                    List.of(),
                    actual,
                    List.of(),
                    Map.of("line", range.startLine(), "column", range.startColumn()),
                    "Korrigiere den Modelltext an der markierten Stelle.");
        }

        private SourceRangeDto combine(SourceRangeDto start, SourceRangeDto end) {
            return new SourceRangeDto(
                    start.startLine(),
                    start.startColumn(),
                    start.startOffset(),
                    end.endLine(),
                    end.endColumn(),
                    end.endOffset());
        }

        private static boolean isOpening(String symbol) {
            return "(".equals(symbol) || "[".equals(symbol) || "{".equals(symbol);
        }

        private static boolean isClosing(String symbol) {
            return ")".equals(symbol) || "]".equals(symbol) || "}".equals(symbol);
        }

        private static String matchingClose(String opening) {
            return switch (opening) {
                case "(" -> ")";
                case "[" -> "]";
                case "{" -> "}";
                default -> opening;
            };
        }
    }

    public record ModelTextParseResult(
            String modelName,
            List<ModelTextImport> imports,
            List<ModelTextClass> classes,
            List<ModelTextEnumeration> enumerations,
            List<ModelTextDataType> dataTypes,
            List<ModelTextAssociation> associations,
            List<ModelTextInvariant> invariants,
            List<ModelTextOperationContext> operationContexts,
            List<OclDiagnosticDto> diagnostics) {

        public boolean hasSupportedModelParts() {
            return !classes.isEmpty() || !enumerations.isEmpty() || !dataTypes.isEmpty()
                    || !associations.isEmpty() || !invariants.isEmpty();
        }
    }

    public record ModelTextImport(String sourcePath, boolean wildcard, List<String> selectedNames,
            SourceRangeDto sourceRange) {

        public ModelTextImport {
            sourcePath = sourcePath == null ? "" : sourcePath;
            selectedNames = List.copyOf(selectedNames == null ? List.of() : selectedNames);
        }
    }

    public record ModelTextClass(String name, boolean abstractClass, List<String> superClassNames,
            List<ModelTextAttribute> attributes, List<ModelTextOperation> operations) {
    }

    public record ModelTextEnumeration(String name, List<String> literals) {
    }

    public record ModelTextDataType(String name, List<ModelTextAttribute> properties,
            List<ModelTextOperation> operations) {
    }

    public record ModelTextAttribute(String name, String type, boolean derived,
            String deriveExpression, String initExpression) {
    }

    public record ModelTextOperation(String name, String returnType, List<ModelTextParameter> parameters,
            String bodyExpression, List<ModelTextOperationContract> contracts) {
    }

    public record ModelTextOperationContract(String kind, String name, String expression) {
    }

    public record ModelTextOperationContext(String contextClass, String operationName,
            List<ModelTextParameter> parameters, String returnType, List<ModelTextOperationContract> contracts) {
    }

    public record ModelTextParameter(String name, String type) {
    }

    public record ModelTextAssociation(String name, String kind, List<ModelTextAssociationEnd> ends,
            String associationClassName) {
    }

    public record ModelTextAssociationEnd(String className, String multiplicity, String roleName,
            boolean ordered, boolean unique, boolean derived, String deriveExpression, boolean union,
            List<String> subsettedRoleNames, List<String> redefinedRoleNames, List<ModelTextParameter> qualifiers) {
    }

    public record ModelTextInvariant(String contextClass, String name, String expression,
            List<String> contextVariableNames, boolean existential) {
    }
}
