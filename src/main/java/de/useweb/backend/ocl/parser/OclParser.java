package de.useweb.backend.ocl.parser;

import java.util.ArrayList;
import java.util.List;

import de.useweb.backend.ocl.ast.BinaryExpression;
import de.useweb.backend.ocl.ast.AllInstancesExpression;
import de.useweb.backend.ocl.ast.AtPreExpression;
import de.useweb.backend.ocl.ast.BinaryOperator;
import de.useweb.backend.ocl.ast.CollectionItem;
import de.useweb.backend.ocl.ast.CollectionLiteralExpression;
import de.useweb.backend.ocl.ast.CollectionLiteralPart;
import de.useweb.backend.ocl.ast.CollectionRangeItem;
import de.useweb.backend.ocl.ast.CallNavigationOperator;
import de.useweb.backend.ocl.ast.EnumLiteralExpression;
import de.useweb.backend.ocl.ast.LiteralExpression;
import de.useweb.backend.ocl.ast.LiteralType;
import de.useweb.backend.ocl.ast.IteratorExpression;
import de.useweb.backend.ocl.ast.IterateExpression;
import de.useweb.backend.ocl.ast.IfExpression;
import de.useweb.backend.ocl.ast.LetExpression;
import de.useweb.backend.ocl.ast.IteratorKind;
import de.useweb.backend.ocl.ast.OclAstNode;
import de.useweb.backend.ocl.ast.OclAstMetrics;
import de.useweb.backend.ocl.ast.OperationCallExpression;
import de.useweb.backend.ocl.ast.TupleExpression;
import de.useweb.backend.ocl.ast.TuplePart;
import de.useweb.backend.ocl.ast.TypeArgumentCallExpression;
import de.useweb.backend.ocl.ast.ParenthesizedExpression;
import de.useweb.backend.ocl.ast.PropertyAccessExpression;
import de.useweb.backend.ocl.ast.QualifiedPropertyAccessExpression;
import de.useweb.backend.ocl.ast.ResultExpression;
import de.useweb.backend.ocl.ast.SelfExpression;
import de.useweb.backend.ocl.ast.UnaryExpression;
import de.useweb.backend.ocl.ast.UnaryOperator;
import de.useweb.backend.ocl.ast.VariableDeclaration;
import de.useweb.backend.ocl.ast.VariableExpression;
import de.useweb.backend.ocl.diagnostics.OclDiagnostic;
import de.useweb.backend.ocl.diagnostics.SourcePosition;
import de.useweb.backend.ocl.diagnostics.SourceRange;
import de.useweb.backend.ocl.collection.CollectionKind;
import de.useweb.backend.ocl.lexer.OclLexResult;
import de.useweb.backend.ocl.lexer.OclLexer;
import de.useweb.backend.ocl.lexer.OclToken;
import de.useweb.backend.ocl.lexer.OclTokenType;
import de.useweb.backend.ocl.profile.OclComplianceProfile;

public class OclParser {

    private final OclLexer lexer;
    private List<OclToken> tokens;
    private int current;
    private int expressionDepth;
    private final List<OclDiagnostic> diagnostics = new ArrayList<>();

    public OclParser() {
        this(new OclLexer());
    }

    public OclParser(OclLexer lexer) {
        this.lexer = lexer;
    }

    public synchronized OclParseResult parse(String expression) {
        SourceRange inputStart = new SourceRange(new SourcePosition(1, 1, 0), new SourcePosition(1, 1, 0));
        if (expression == null) {
            return OclParseResult.failure(List.of(OclDiagnostic.parserError("INVALID_OCL_INPUT",
                    "OCL input must not be null.", inputStart, List.of("OCL expression"), "null")));
        }
        if (expression.length() > OclComplianceProfile.MAX_SOURCE_CHARACTERS) {
            return OclParseResult.failure(List.of(OclDiagnostic.parserError("SOURCE_LIMIT_EXCEEDED",
                    "OCL input exceeds the source character limit of "
                            + OclComplianceProfile.MAX_SOURCE_CHARACTERS + ".",
                    inputStart, List.of("at most " + OclComplianceProfile.MAX_SOURCE_CHARACTERS + " characters"),
                    Integer.toString(expression.length()))));
        }
        OclLexResult lexResult = lexer.tokenize(expression);
        this.tokens = lexResult.tokens();
        this.current = 0;
        this.expressionDepth = 0;
        this.diagnostics.clear();
        addBoundedDiagnostics(lexResult.diagnostics());
        if (!lexResult.success()) {
            return OclParseResult.failure(diagnostics);
        }
        long tokenCount = Math.max(0, lexResult.tokens().size() - 1L);
        if (tokenCount > OclComplianceProfile.MAX_TOKENS) {
            diagnostics.add(OclDiagnostic.parserError("TOKEN_LIMIT_EXCEEDED",
                    "OCL input exceeds the token limit of " + OclComplianceProfile.MAX_TOKENS + ".",
                    lexResult.tokens().getLast().sourceRange(),
                    List.of("at most " + OclComplianceProfile.MAX_TOKENS + " tokens"),
                    Long.toString(tokenCount)));
            return OclParseResult.failure(diagnostics);
        }

        OclAstNode ast = expression();
        if (diagnostics.isEmpty() && OclAstMetrics.maxDepth(ast) > OclComplianceProfile.MAX_AST_DEPTH) {
            diagnostics.add(OclDiagnostic.parserError("AST_DEPTH_LIMIT_EXCEEDED",
                    "OCL expression exceeds the AST depth limit of " + OclComplianceProfile.MAX_AST_DEPTH + ".",
                    ast.sourceRange(), List.of("AST depth at most " + OclComplianceProfile.MAX_AST_DEPTH),
                    Integer.toString(OclAstMetrics.maxDepth(ast))));
        }
        if (diagnostics.isEmpty()) {
            consume(OclTokenType.EOF, "Expected end of expression.", List.of("EOF"));
        }
        return diagnostics.isEmpty() ? OclParseResult.ok(ast) : OclParseResult.failure(diagnostics);
    }

    private void addBoundedDiagnostics(List<OclDiagnostic> source) {
        int limit = Math.toIntExact(OclComplianceProfile.MAX_DIAGNOSTICS);
        if (source.size() <= limit) {
            diagnostics.addAll(source);
        } else {
            diagnostics.addAll(source.stream().limit(limit - 1L).toList());
            diagnostics.add(OclDiagnostic.parserError("DIAGNOSTIC_LIMIT_EXCEEDED",
                    "OCL parsing stopped reporting errors after " + limit + " diagnostics.",
                    source.get(limit - 1).sourceRange(), List.of("at most " + limit + " diagnostics"),
                    Integer.toString(source.size())));
        }
    }

    private OclAstNode expression() {
        expressionDepth++;
        try {
            if (expressionDepth > OclComplianceProfile.MAX_AST_DEPTH) {
                diagnostics.add(OclDiagnostic.parserError("AST_DEPTH_LIMIT_EXCEEDED",
                        "OCL expression exceeds the nesting limit of " + OclComplianceProfile.MAX_AST_DEPTH + ".",
                        peek().sourceRange(), List.of("nesting depth at most " + OclComplianceProfile.MAX_AST_DEPTH),
                        Integer.toString(expressionDepth)));
                return null;
            }
            return impliesExpression();
        } finally {
            expressionDepth--;
        }
    }

    private OclAstNode impliesExpression() {
        OclAstNode expression = xorExpression();
        if (match(OclTokenType.IMPLIES)) {
            OclToken operator = previous();
            OclAstNode right = nestedImpliesExpression();
            if (right == null) return null;
            expression = binary(expression, operator, right, BinaryOperator.IMPLIES);
        }
        return expression;
    }

    private OclAstNode nestedImpliesExpression() {
        expressionDepth++;
        try {
            if (nestingLimitExceeded()) return null;
            return impliesExpression();
        } finally {
            expressionDepth--;
        }
    }

    private OclAstNode xorExpression() {
        OclAstNode expression = orExpression();
        while (match(OclTokenType.XOR)) {
            OclToken operator = previous();
            expression = binary(expression, operator, orExpression(), BinaryOperator.XOR);
        }
        return expression;
    }

    private OclAstNode orExpression() {
        OclAstNode expression = andExpression();
        while (match(OclTokenType.OR)) {
            OclToken operator = previous();
            OclAstNode right = andExpression();
            expression = binary(expression, operator, right, BinaryOperator.OR);
        }
        return expression;
    }

    private OclAstNode andExpression() {
        OclAstNode expression = equalityExpression();
        while (match(OclTokenType.AND)) {
            OclToken operator = previous();
            OclAstNode right = equalityExpression();
            expression = binary(expression, operator, right, BinaryOperator.AND);
        }
        return expression;
    }

    private OclAstNode equalityExpression() {
        OclAstNode expression = relationalExpression();
        while (match(OclTokenType.EQUAL, OclTokenType.NOT_EQUAL)) {
            OclToken operator = previous();
            OclAstNode right = relationalExpression();
            expression = binary(expression, operator, right, binaryOperator(operator));
        }
        return expression;
    }

    private OclAstNode relationalExpression() {
        OclAstNode expression = additiveExpression();
        if (match(OclTokenType.LESS, OclTokenType.LESS_EQUAL, OclTokenType.GREATER, OclTokenType.GREATER_EQUAL)) {
            OclToken operator = previous();
            OclAstNode right = additiveExpression();
            expression = binary(expression, operator, right, binaryOperator(operator));
        }
        return expression;
    }

    private OclAstNode additiveExpression() {
        OclAstNode expression = multiplicativeExpression();
        while (match(OclTokenType.PLUS, OclTokenType.MINUS)) {
            OclToken operator = previous();
            expression = binary(expression, operator, multiplicativeExpression(), binaryOperator(operator));
        }
        return expression;
    }

    private OclAstNode multiplicativeExpression() {
        OclAstNode expression = unaryExpression();
        while (match(OclTokenType.STAR, OclTokenType.SLASH, OclTokenType.DIV, OclTokenType.MOD)) {
            OclToken operator = previous();
            expression = binary(expression, operator, unaryExpression(), binaryOperator(operator));
        }
        return expression;
    }

    private OclAstNode unaryExpression() {
        if (match(OclTokenType.NOT, OclTokenType.MINUS)) {
            OclToken operator = previous();
            expressionDepth++;
            OclAstNode expression;
            try {
                if (nestingLimitExceeded()) return null;
                expression = unaryExpression();
            } finally {
                expressionDepth--;
            }
            if (expression == null) {
                return null;
            }
            UnaryOperator unaryOperator = operator.type() == OclTokenType.NOT ? UnaryOperator.NOT : UnaryOperator.NEGATE;
            return new UnaryExpression(unaryOperator, expression, range(operator.sourceRange(), expression.sourceRange()));
        }
        return postfixExpression();
    }

    private boolean nestingLimitExceeded() {
        if (expressionDepth <= OclComplianceProfile.MAX_AST_DEPTH) return false;
        diagnostics.add(OclDiagnostic.parserError("AST_DEPTH_LIMIT_EXCEEDED",
                "OCL expression exceeds the nesting limit of " + OclComplianceProfile.MAX_AST_DEPTH + ".",
                peek().sourceRange(), List.of("nesting depth at most " + OclComplianceProfile.MAX_AST_DEPTH),
                Integer.toString(expressionDepth)));
        return true;
    }

    private OclAstNode postfixExpression() {
        OclAstNode expression = primaryExpression();
        while (diagnostics.isEmpty()) {
            if (match(OclTokenType.AT)) {
                OclToken at = previous();
                OclToken pre = consume(OclTokenType.IDENTIFIER, "Expected 'pre' after '@'.", List.of("pre"));
                if (!pre.text().equals("pre")) {
                    diagnostics.add(OclDiagnostic.parserError("EXPECTED_PRE", "Expected 'pre' after '@'.",
                            pre.sourceRange(), List.of("pre"), pre.text()));
                    return null;
                }
                expression = new AtPreExpression(expression, range(at.sourceRange(), pre.sourceRange()),
                        range(expression.sourceRange(), pre.sourceRange()));
                continue;
            }
            if (!(match(OclTokenType.DOT) || match(OclTokenType.ARROW))) {
                break;
            }
            OclToken operator = previous();
            OclToken feature = consume(OclTokenType.IDENTIFIER, "Expected feature name after navigation operator.", List.of("IDENTIFIER"));
            if (expression == null || !diagnostics.isEmpty()) {
                continue;
            }
            if (match(OclTokenType.LEFT_PAREN)) {
                var iteratorKind = IteratorKind.fromOclName(feature.text());
                expression = isTypeArgumentOperation(feature.text())
                        ? typeArgumentCall(expression, feature, operator.type() == OclTokenType.DOT
                                ? CallNavigationOperator.DOT : CallNavigationOperator.ARROW)
                        : feature.text().equals("iterate")
                        ? iterateCall(expression, feature)
                        : iteratorKind.isPresent()
                        ? (hasPipeBeforeClosingParenthesis()
                        ? iteratorCall(expression, feature, iteratorKind.get())
                        : implicitIteratorCall(expression, feature, iteratorKind.get()))
                        : operationCall(expression, feature, operator.type() == OclTokenType.DOT
                                ? CallNavigationOperator.DOT : CallNavigationOperator.ARROW);
            } else if (match(OclTokenType.LEFT_BRACKET)) {
                List<OclAstNode> qualifierArguments = new ArrayList<>();
                do {
                    qualifierArguments.add(expression());
                } while (match(OclTokenType.COMMA));
                OclToken rightBracket = consume(OclTokenType.RIGHT_BRACKET,
                        "Expected ']' after qualifier arguments.", List.of("]"));
                expression = new QualifiedPropertyAccessExpression(expression, feature.text(), qualifierArguments,
                        feature.sourceRange(), range(expression.sourceRange(), rightBracket.sourceRange()));
            } else if (operator.type() == OclTokenType.ARROW) {
                expression = new OperationCallExpression(expression, feature.text(), List.of(),
                        CallNavigationOperator.ARROW, feature.sourceRange(),
                        range(expression.sourceRange(), feature.sourceRange()));
            } else {
                expression = new PropertyAccessExpression(expression, feature.text(), feature.sourceRange(),
                        range(expression.sourceRange(), feature.sourceRange()));
            }
        }
        return expression;
    }

    private boolean isTypeArgumentOperation(String name) {
        return name.equals("oclIsTypeOf") || name.equals("oclIsKindOf") || name.equals("oclAsType")
                || name.equals("selectByKind") || name.equals("selectByType");
    }

    private OclAstNode typeArgumentCall(OclAstNode receiver, OclToken operation,
            CallNavigationOperator navigationOperator) {
        ParsedType type = qualifiedTypeName("Expected type name as argument of '" + operation.text() + "'.");
        String typeName = type.name();
        SourceRange typeRange = type.range();
        if (match(OclTokenType.LEFT_PAREN)) {
            ParsedType elementType = qualifiedTypeName("Expected collection element type.");
            OclToken typeRightParen = consume(OclTokenType.RIGHT_PAREN,
                    "Expected ')' after collection element type.", List.of(")"));
            typeName = typeName + "(" + elementType.name() + ")";
            typeRange = range(type.range(), typeRightParen.sourceRange());
        }
        OclToken rightParen = consume(OclTokenType.RIGHT_PAREN,
                "Expected ')' after type argument.", List.of(")"));
        return new TypeArgumentCallExpression(receiver, operation.text(), typeName, navigationOperator, operation.sourceRange(),
                typeRange, range(receiver.sourceRange(), rightParen.sourceRange()));
    }

    private OclAstNode operationCall(OclAstNode receiver, OclToken operation, CallNavigationOperator navigationOperator) {
        List<OclAstNode> arguments = new ArrayList<>();
        if (!check(OclTokenType.RIGHT_PAREN)) {
            do {
                OclAstNode argument = expression();
                if (argument == null || !diagnostics.isEmpty()) {
                    return null;
                }
                arguments.add(argument);
            } while (match(OclTokenType.COMMA));
        }
        OclToken rightParen = consume(OclTokenType.RIGHT_PAREN, "Expected ')' after operation arguments.", List.of(")"));
        return new OperationCallExpression(receiver, operation.text(), arguments, navigationOperator, operation.sourceRange(),
                range(receiver.sourceRange(), rightParen.sourceRange()));
    }

    private OclAstNode iteratorCall(OclAstNode source, OclToken operation, IteratorKind kind) {
        List<VariableDeclaration> variables = new ArrayList<>();
        do {
            variables.add(variableDeclaration("iterator", false));
            if (!diagnostics.isEmpty()) {
                return null;
            }
        } while (match(OclTokenType.COMMA));

        consume(OclTokenType.PIPE, "Expected '|' after iterator declarations.", List.of("|"));
        if (!diagnostics.isEmpty()) {
            return null;
        }
        OclAstNode body = expression();
        if (body == null || !diagnostics.isEmpty()) {
            return null;
        }
        OclToken rightParen = consume(OclTokenType.RIGHT_PAREN,
                "Expected ')' after iterator body.", List.of(")"));
        return new IteratorExpression(source, kind, variables, body, operation.sourceRange(), body.sourceRange(),
                range(source.sourceRange(), rightParen.sourceRange()));
    }

    private OclAstNode implicitIteratorCall(OclAstNode source, OclToken operation, IteratorKind kind) {
        OclAstNode body = expression();
        OclToken rightParen = consume(OclTokenType.RIGHT_PAREN,
                "Expected ')' after iterator body.", List.of(")"));
        if (body == null || !diagnostics.isEmpty()) return null;
        return new IteratorExpression(source, kind, List.of(), body, operation.sourceRange(), body.sourceRange(),
                range(source.sourceRange(), rightParen.sourceRange()));
    }

    private OclAstNode iterateCall(OclAstNode source, OclToken operation) {
        List<VariableDeclaration> iterators = new ArrayList<>();
        do {
            iterators.add(variableDeclaration("iterator", false));
            if (!diagnostics.isEmpty()) {
                return null;
            }
        } while (match(OclTokenType.COMMA));
        consume(OclTokenType.SEMICOLON, "Expected ';' after iterate variables.", List.of(";"));
        VariableDeclaration accumulator = variableDeclaration("accumulator", true);
        consume(OclTokenType.EQUAL, "Expected '=' before iterate initializer.", List.of("="));
        if (!diagnostics.isEmpty()) {
            return null;
        }
        OclAstNode initializer = expression();
        consume(OclTokenType.PIPE, "Expected '|' after iterate initializer.", List.of("|"));
        if (initializer == null || !diagnostics.isEmpty()) {
            return null;
        }
        OclAstNode body = expression();
        OclToken rightParen = consume(OclTokenType.RIGHT_PAREN,
                "Expected ')' after iterate body.", List.of(")"));
        if (body == null || !diagnostics.isEmpty()) {
            return null;
        }
        return new IterateExpression(source, iterators, accumulator, initializer, body, operation.sourceRange(),
                initializer.sourceRange(), body.sourceRange(), range(source.sourceRange(), rightParen.sourceRange()));
    }

    private VariableDeclaration variableDeclaration(String role, boolean typeRequired) {
        OclToken name = consume(OclTokenType.IDENTIFIER,
                "Expected " + role + " variable name.", List.of("IDENTIFIER"));
        if (!diagnostics.isEmpty()) {
            return null;
        }
        String declaredTypeName = null;
        SourceRange typeRange = null;
        SourceRange declarationRange = name.sourceRange();
        if (match(OclTokenType.COLON)) {
            ParsedType type = declaredType();
            if (!diagnostics.isEmpty()) {
                return null;
            }
            declaredTypeName = type.name();
            typeRange = type.range();
            declarationRange = range(name.sourceRange(), type.range());
        } else if (typeRequired) {
            consume(OclTokenType.COLON, "Expected ':' and a type for the iterate accumulator.", List.of(":"));
            return null;
        }
        return new VariableDeclaration(name.text(), declaredTypeName, name.sourceRange(), typeRange, declarationRange);
    }

    private ParsedType declaredType() {
        ParsedType name = qualifiedTypeName("Expected variable type after ':'.");
        if (!diagnostics.isEmpty()) {
            return null;
        }
        if (!match(OclTokenType.LEFT_PAREN)) {
            return name;
        }
        ParsedType elementType = declaredType();
        OclToken rightParen = consume(OclTokenType.RIGHT_PAREN,
                "Expected ')' after collection element type.", List.of(")"));
        if (elementType == null || !diagnostics.isEmpty()) {
            return null;
        }
        return new ParsedType(name.name() + "(" + elementType.name() + ")",
                range(name.range(), rightParen.sourceRange()));
    }

    private ParsedType qualifiedTypeName(String message) {
        OclToken first = consume(OclTokenType.IDENTIFIER, message, List.of("TYPE_NAME"));
        List<OclToken> parts = new ArrayList<>();
        parts.add(first);
        while (match(OclTokenType.DOUBLE_COLON)) {
            parts.add(consume(OclTokenType.IDENTIFIER, "Expected type-name segment after '::'.",
                    List.of("TYPE_NAME")));
        }
        return new ParsedType(parts.stream().map(OclToken::text)
                .reduce((left, right) -> left + "::" + right).orElse(first.text()),
                range(first.sourceRange(), parts.getLast().sourceRange()));
    }

    private record ParsedType(String name, SourceRange range) {
    }

    private record LetBinding(VariableDeclaration variable, OclAstNode initializer) {
    }

    private boolean hasPipeBeforeClosingParenthesis() {
        int nesting = 0;
        for (int index = current; index < tokens.size(); index++) {
            OclTokenType type = tokens.get(index).type();
            if (type == OclTokenType.LEFT_PAREN) {
                nesting++;
            } else if (type == OclTokenType.RIGHT_PAREN) {
                if (nesting == 0) {
                    return false;
                }
                nesting--;
            } else if (type == OclTokenType.PIPE && nesting == 0) {
                return true;
            }
        }
        return false;
    }

    private OclAstNode primaryExpression() {
        if (match(OclTokenType.LET)) {
            return letExpression(previous());
        }
        if (match(OclTokenType.IF)) {
            return ifExpression(previous());
        }
        if (match(OclTokenType.SELF)) {
            return new SelfExpression(previous().sourceRange());
        }
        if (match(OclTokenType.STRING_LITERAL, OclTokenType.INTEGER_LITERAL, OclTokenType.REAL_LITERAL,
                OclTokenType.BOOLEAN_LITERAL, OclTokenType.NULL_LITERAL, OclTokenType.INVALID_LITERAL)) {
            return literal(previous());
        }
        if (match(OclTokenType.STAR)) {
            return new LiteralExpression(LiteralType.UNLIMITED_NATURAL, "*", previous().sourceRange());
        }
        if (match(OclTokenType.IDENTIFIER)) {
            OclToken identifier = previous();
            if (identifier.text().equals("result")) {
                return new ResultExpression(identifier.sourceRange());
            }
            if (check(OclTokenType.DOUBLE_COLON)) {
                List<OclToken> qualifiedParts = new ArrayList<>();
                qualifiedParts.add(identifier);
                while (match(OclTokenType.DOUBLE_COLON)) {
                    qualifiedParts.add(consume(OclTokenType.IDENTIFIER,
                            "Expected name segment after '::'.", List.of("IDENTIFIER")));
                }
                String qualifiedName = qualifiedParts.stream().map(OclToken::text)
                        .reduce((left, right) -> left + "::" + right).orElse(identifier.text());
                SourceRange qualifiedRange = range(identifier.sourceRange(), qualifiedParts.getLast().sourceRange());
                if (check(OclTokenType.DOT) && checkNextIdentifier("allInstances")) {
                    return allInstancesExpression(qualifiedName, qualifiedRange);
                }
                OclToken literal = qualifiedParts.getLast();
                String enumerationName = qualifiedParts.subList(0, qualifiedParts.size() - 1).stream()
                        .map(OclToken::text).reduce((left, right) -> left + "::" + right).orElse("");
                return new EnumLiteralExpression(enumerationName, literal.text(),
                        range(identifier.sourceRange(), qualifiedParts.get(qualifiedParts.size() - 2).sourceRange()),
                        literal.sourceRange(), qualifiedRange);
            }
            if (check(OclTokenType.DOT) && checkNextIdentifier("allInstances")) {
                return allInstancesExpression(identifier.text(), identifier.sourceRange());
            }
            var collectionKind = CollectionKind.fromOclName(identifier.text());
            if (collectionKind.isPresent() && match(OclTokenType.LEFT_BRACE)) {
                return collectionLiteral(identifier, collectionKind.get());
            }
            if (identifier.text().equals("Tuple") && match(OclTokenType.LEFT_BRACE)) {
                return tupleLiteral(identifier);
            }
            if (match(OclTokenType.LEFT_PAREN)) {
                return operationCall(new SelfExpression(identifier.sourceRange()), identifier,
                        CallNavigationOperator.NONE);
            }
            return new VariableExpression(identifier.text(), identifier.sourceRange());
        }
        if (match(OclTokenType.LEFT_PAREN)) {
            OclToken leftParen = previous();
            OclAstNode expression = expression();
            OclToken rightParen = consume(OclTokenType.RIGHT_PAREN, "Expected ')' after expression.", List.of(")"));
            if (expression == null || !diagnostics.isEmpty()) {
                return expression;
            }
            return new ParenthesizedExpression(expression, range(leftParen.sourceRange(), rightParen.sourceRange()));
        }
        OclToken token = peek();
        diagnostics.add(OclDiagnostic.parserError("UNEXPECTED_TOKEN",
                "Expected expression.",
                token.sourceRange(),
                List.of("self", "STRING_LITERAL", "INTEGER_LITERAL", "REAL_LITERAL", "BOOLEAN_LITERAL", "null", "invalid", "("),
                token.type().name()));
        return null;
    }

    private OclAstNode tupleLiteral(OclToken tupleToken) {
        List<TuplePart> parts = new ArrayList<>();
        if (!check(OclTokenType.RIGHT_BRACE)) {
            do {
                OclToken name = consume(OclTokenType.IDENTIFIER,
                        "Expected tuple part name.", List.of("IDENTIFIER"));
                consume(OclTokenType.EQUAL, "Expected '=' after tuple part name.", List.of("="));
                OclAstNode value = expression();
                if (value == null || !diagnostics.isEmpty()) {
                    return null;
                }
                parts.add(new TuplePart(name.text(), value, name.sourceRange(),
                        range(name.sourceRange(), value.sourceRange())));
            } while (match(OclTokenType.COMMA));
        }
        OclToken rightBrace = consume(OclTokenType.RIGHT_BRACE,
                "Expected '}' after tuple literal.", List.of("}"));
        if (parts.isEmpty()) {
            diagnostics.add(OclDiagnostic.parserError("EMPTY_TUPLE",
                    "A tuple literal must contain at least one part.", range(tupleToken.sourceRange(), rightBrace.sourceRange()),
                    List.of("IDENTIFIER = expression"), "}"));
            return null;
        }
        return new TupleExpression(parts, range(tupleToken.sourceRange(), rightBrace.sourceRange()));
    }

    private OclAstNode allInstancesExpression(String typeName, SourceRange typeRange) {
        consume(OclTokenType.DOT, "Expected '.' before allInstances.", List.of("."));
        OclToken operation = consume(OclTokenType.IDENTIFIER,
                "Expected 'allInstances' after type reference.", List.of("allInstances"));
        SourceRange end = operation.sourceRange();
        if (match(OclTokenType.LEFT_PAREN)) {
            OclToken rightParen = consume(OclTokenType.RIGHT_PAREN,
                    "Expected ')' after allInstances.", List.of(")"));
            end = rightParen.sourceRange();
        }
        return new AllInstancesExpression(typeName, typeRange, operation.sourceRange(), range(typeRange, end));
    }

    private boolean checkNextIdentifier(String text) {
        return current + 1 < tokens.size()
                && tokens.get(current + 1).type() == OclTokenType.IDENTIFIER
                && tokens.get(current + 1).text().equals(text);
    }

    private OclAstNode letExpression(OclToken letToken) {
        List<LetBinding> bindings = new ArrayList<>();
        do {
            VariableDeclaration variable = variableDeclaration("let", false);
            consume(OclTokenType.EQUAL, "Expected '=' before let initializer.", List.of("="));
            if (variable == null || !diagnostics.isEmpty()) {
                return null;
            }
            OclAstNode initializer = expression();
            if (initializer == null || !diagnostics.isEmpty()) {
                return null;
            }
            bindings.add(new LetBinding(variable, initializer));
        } while (match(OclTokenType.COMMA));
        consume(OclTokenType.IN, "Expected 'in' after let initializer.", List.of("in"));
        if (!diagnostics.isEmpty()) {
            return null;
        }
        OclAstNode body = expression();
        if (body == null || !diagnostics.isEmpty()) {
            return null;
        }
        OclAstNode result = body;
        for (int index = bindings.size() - 1; index >= 0; index--) {
            LetBinding binding = bindings.get(index);
            SourceRange start = index == 0 ? letToken.sourceRange() : binding.variable().sourceRange();
            result = new LetExpression(binding.variable(), binding.initializer(), result,
                    binding.initializer().sourceRange(), result.sourceRange(), range(start, result.sourceRange()));
        }
        return result;
    }

    private OclAstNode ifExpression(OclToken ifToken) {
        OclAstNode condition = expression();
        consume(OclTokenType.THEN, "Expected 'then' after if condition.", List.of("then"));
        if (condition == null || !diagnostics.isEmpty()) {
            return null;
        }
        OclAstNode thenExpression = expression();
        consume(OclTokenType.ELSE, "Expected 'else' after then branch.", List.of("else"));
        if (thenExpression == null || !diagnostics.isEmpty()) {
            return null;
        }
        OclAstNode elseExpression = expression();
        OclToken endif = consume(OclTokenType.ENDIF, "Expected 'endif' after else branch.", List.of("endif"));
        if (elseExpression == null || !diagnostics.isEmpty()) {
            return null;
        }
        return new IfExpression(condition, thenExpression, elseExpression,
                condition.sourceRange(), thenExpression.sourceRange(), elseExpression.sourceRange(),
                range(ifToken.sourceRange(), endif.sourceRange()));
    }

    private OclAstNode collectionLiteral(OclToken kindToken, CollectionKind collectionKind) {
        List<CollectionLiteralPart> parts = new ArrayList<>();
        if (!check(OclTokenType.RIGHT_BRACE)) {
            do {
                OclAstNode first = expression();
                if (first == null || !diagnostics.isEmpty()) {
                    return null;
                }
                if (match(OclTokenType.RANGE)) {
                    OclAstNode last = expression();
                    if (last == null || !diagnostics.isEmpty()) {
                        return null;
                    }
                    parts.add(new CollectionRangeItem(first, last, range(first.sourceRange(), last.sourceRange())));
                } else {
                    parts.add(new CollectionItem(first, first.sourceRange()));
                }
            } while (match(OclTokenType.COMMA));
        }
        OclToken rightBrace = consume(OclTokenType.RIGHT_BRACE, "Expected '}' after collection literal.", List.of("}"));
        return new CollectionLiteralExpression(collectionKind, parts, range(kindToken.sourceRange(), rightBrace.sourceRange()));
    }

    private OclAstNode literal(OclToken token) {
        return switch (token.type()) {
            case STRING_LITERAL -> new LiteralExpression(LiteralType.STRING, decodeString(token.text()), token.sourceRange());
            case INTEGER_LITERAL -> new LiteralExpression(LiteralType.INTEGER, Integer.parseInt(token.text()), token.sourceRange());
            case REAL_LITERAL -> new LiteralExpression(LiteralType.REAL, Double.parseDouble(token.text()), token.sourceRange());
            case BOOLEAN_LITERAL -> new LiteralExpression(LiteralType.BOOLEAN, Boolean.parseBoolean(token.text()), token.sourceRange());
            case NULL_LITERAL -> new LiteralExpression(LiteralType.NULL, null, token.sourceRange());
            case INVALID_LITERAL -> new LiteralExpression(LiteralType.INVALID, null, token.sourceRange());
            default -> throw new IllegalArgumentException("Unsupported literal token: " + token.type());
        };
    }

    private String decodeString(String text) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < text.length();) {
            if (Character.isWhitespace(text.charAt(index))) {
                index++;
                continue;
            }
            if (text.charAt(index++) != '\'') throw new IllegalArgumentException("Invalid string token");
            while (index < text.length() && text.charAt(index) != '\'') {
                char current = text.charAt(index++);
                if (current != '\\') {
                    result.append(current);
                    continue;
                }
                char marker = text.charAt(index++);
                switch (marker) {
                    case 'b' -> result.append('\b');
                    case 't' -> result.append('\t');
                    case 'n' -> result.append('\n');
                    case 'f' -> result.append('\f');
                    case 'r' -> result.append('\r');
                    case '"' -> result.append('"');
                    case '\'' -> result.append('\'');
                    case '\\' -> result.append('\\');
                    case 'x' -> {
                        result.append((char) Integer.parseInt(text.substring(index, index + 2), 16));
                        index += 2;
                    }
                    case 'u' -> {
                        result.append((char) Integer.parseInt(text.substring(index, index + 4), 16));
                        index += 4;
                    }
                    default -> throw new IllegalArgumentException("Invalid string escape");
                }
            }
            index++;
        }
        return result.toString();
    }

    private OclAstNode binary(OclAstNode left, OclToken operator, OclAstNode right, BinaryOperator binaryOperator) {
        if (left == null || right == null) {
            return left;
        }
        return new BinaryExpression(left, binaryOperator, right, operator.sourceRange(), range(left.sourceRange(), right.sourceRange()));
    }

    private BinaryOperator binaryOperator(OclToken token) {
        return switch (token.type()) {
            case EQUAL -> BinaryOperator.EQUAL;
            case NOT_EQUAL -> BinaryOperator.NOT_EQUAL;
            case LESS -> BinaryOperator.LESS;
            case LESS_EQUAL -> BinaryOperator.LESS_EQUAL;
            case GREATER -> BinaryOperator.GREATER;
            case GREATER_EQUAL -> BinaryOperator.GREATER_EQUAL;
            case PLUS -> BinaryOperator.ADD;
            case MINUS -> BinaryOperator.SUBTRACT;
            case STAR -> BinaryOperator.MULTIPLY;
            case SLASH -> BinaryOperator.DIVIDE;
            case DIV -> BinaryOperator.INTEGER_DIVIDE;
            case MOD -> BinaryOperator.MODULO;
            default -> throw new IllegalArgumentException("Unsupported binary operator token: " + token.type());
        };
    }

    private SourceRange range(SourceRange startRange, SourceRange endRange) {
        return new SourceRange(startRange.start(), endRange.end());
    }

    private OclToken consume(OclTokenType type, String message, List<String> expected) {
        if (check(type)) {
            return advance();
        }
        OclToken token = peek();
        diagnostics.add(OclDiagnostic.parserError("MISSING_TOKEN", message, token.sourceRange(), expected, token.type().name()));
        return token;
    }

    private boolean match(OclTokenType... types) {
        for (OclTokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private boolean check(OclTokenType type) {
        return peek().type() == type;
    }

    private OclToken advance() {
        if (!isAtEnd()) {
            current++;
        }
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type() == OclTokenType.EOF;
    }

    private OclToken peek() {
        return tokens.get(current);
    }

    private OclToken previous() {
        return tokens.get(current - 1);
    }
}
