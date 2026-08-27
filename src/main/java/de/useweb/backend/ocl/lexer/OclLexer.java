package de.useweb.backend.ocl.lexer;

import java.util.ArrayList;
import java.util.List;

import de.useweb.backend.ocl.diagnostics.OclDiagnostic;
import de.useweb.backend.ocl.diagnostics.SourcePosition;
import de.useweb.backend.ocl.diagnostics.SourceRange;

public class OclLexer {

    private String source;
    private int index;
    private int line;
    private int column;
    private final List<OclToken> tokens = new ArrayList<>();
    private final List<OclDiagnostic> diagnostics = new ArrayList<>();

    public OclLexResult tokenize(String text) {
        this.source = text == null ? "" : text;
        this.index = 0;
        this.line = 1;
        this.column = 1;
        this.tokens.clear();
        this.diagnostics.clear();

        while (!isAtEnd()) {
            char current = peek();
            if (Character.isWhitespace(current)) {
                advance();
            } else if (isIdentifierStart(current)) {
                readIdentifierOrKeyword();
            } else if (Character.isDigit(current)) {
                readNumber();
            } else if (current == '\'') {
                readString();
            } else {
                readSymbol();
            }
        }

        SourcePosition eof = position();
        tokens.add(new OclToken(OclTokenType.EOF, "", new SourceRange(eof, eof)));
        return new OclLexResult(tokens, diagnostics);
    }

    private void readIdentifierOrKeyword() {
        SourcePosition start = position();
        int startIndex = index;
        while (!isAtEnd() && isIdentifierPart(peek())) {
            advance();
        }
        String text = source.substring(startIndex, index);
        OclTokenType type = switch (text) {
            case "self" -> OclTokenType.SELF;
            case "true", "false" -> OclTokenType.BOOLEAN_LITERAL;
            case "null" -> OclTokenType.NULL_LITERAL;
            case "invalid" -> OclTokenType.INVALID_LITERAL;
            case "if" -> OclTokenType.IF;
            case "then" -> OclTokenType.THEN;
            case "else" -> OclTokenType.ELSE;
            case "endif" -> OclTokenType.ENDIF;
            case "let" -> OclTokenType.LET;
            case "in" -> OclTokenType.IN;
            case "and" -> OclTokenType.AND;
            case "or" -> OclTokenType.OR;
            case "xor" -> OclTokenType.XOR;
            case "implies" -> OclTokenType.IMPLIES;
            case "not" -> OclTokenType.NOT;
            case "div" -> OclTokenType.DIV;
            case "mod" -> OclTokenType.MOD;
            default -> OclTokenType.IDENTIFIER;
        };
        addToken(type, text, start);
    }

    private void readNumber() {
        SourcePosition start = position();
        int startIndex = index;
        while (!isAtEnd() && Character.isDigit(peek())) {
            advance();
        }
        boolean real = false;
        if (!isAtEnd() && peek() == '.' && hasNextDigit()) {
            real = true;
            advance();
            while (!isAtEnd() && Character.isDigit(peek())) {
                advance();
            }
        }
        if (!isAtEnd() && (peek() == 'e' || peek() == 'E') && hasExponentDigits()) {
            real = true;
            advance();
            if (!isAtEnd() && (peek() == '+' || peek() == '-')) {
                advance();
            }
            while (!isAtEnd() && Character.isDigit(peek())) {
                advance();
            }
        }
        String text = source.substring(startIndex, index);
        if (!real) {
            try {
                Integer.parseInt(text);
            } catch (NumberFormatException exception) {
                diagnostics.add(OclDiagnostic.lexerError("INTEGER_LITERAL_OUT_OF_RANGE",
                        "Integer literal exceeds the supported runtime range.",
                        new SourceRange(start, position()), List.of("Integer"), text));
                return;
            }
        }
        addToken(real ? OclTokenType.REAL_LITERAL : OclTokenType.INTEGER_LITERAL, text, start);
    }

    private void readString() {
        SourcePosition start = position();
        StringBuilder tokenText = new StringBuilder();
        boolean adjacent;
        do {
            int fragmentStart = index;
            advance();
            while (!isAtEnd() && peek() != '\'') {
                if (peek() == '\\') readEscape();
                else advance();
            }
            if (isAtEnd()) {
                diagnostics.add(OclDiagnostic.lexerError("UNTERMINATED_STRING", "Unterminated string literal.",
                        new SourceRange(start, position()), List.of("STRING_LITERAL"), "EOF"));
                return;
            }
            advance();
            tokenText.append(source, fragmentStart, index);
            int lookahead = index;
            while (lookahead < source.length() && Character.isWhitespace(source.charAt(lookahead))) lookahead++;
            adjacent = lookahead < source.length() && source.charAt(lookahead) == '\'';
            if (adjacent) while (index < lookahead) advance();
        } while (adjacent);
        addToken(OclTokenType.STRING_LITERAL, tokenText.toString(), start);
    }

    private void readEscape() {
        SourcePosition escapeStart = position();
        advance();
        if (isAtEnd()) return;
        char marker = advance();
        int digits = marker == 'x' ? 2 : marker == 'u' ? 4 : 0;
        if (digits == 0 && "btnfr\"'\\".indexOf(marker) < 0) {
            invalidEscape(escapeStart, Character.toString(marker));
            return;
        }
        for (int count = 0; count < digits; count++) {
            if (isAtEnd() || Character.digit(peek(), 16) < 0) {
                invalidEscape(escapeStart, source.substring(escapeStart.offset(), index));
                return;
            }
            advance();
        }
    }

    private void invalidEscape(SourcePosition start, String actual) {
        diagnostics.add(OclDiagnostic.lexerError("INVALID_ESCAPE_SEQUENCE", "Invalid OCL string escape sequence.",
                new SourceRange(start, position()), List.of("\\b", "\\t", "\\n", "\\f", "\\r", "\\\"", "\\'", "\\\\", "\\xhh", "\\uhhhh"), actual));
    }

    private void readSymbol() {
        SourcePosition start = position();
        char current = advance();
            switch (current) {
                case '@' -> addToken(OclTokenType.AT, "@", start);
            case '.' -> {
                if (match('.')) {
                    addToken(OclTokenType.RANGE, "..", start);
                } else {
                    addToken(OclTokenType.DOT, ".", start);
                }
            }
            case '(' -> addToken(OclTokenType.LEFT_PAREN, "(", start);
            case ')' -> addToken(OclTokenType.RIGHT_PAREN, ")", start);
            case ',' -> addToken(OclTokenType.COMMA, ",", start);
            case ';' -> addToken(OclTokenType.SEMICOLON, ";", start);
            case ':' -> {
                if (match(':')) {
                    addToken(OclTokenType.DOUBLE_COLON, "::", start);
                } else {
                    addToken(OclTokenType.COLON, ":", start);
                }
            }
            case '|' -> addToken(OclTokenType.PIPE, "|", start);
            case '{' -> addToken(OclTokenType.LEFT_BRACE, "{", start);
            case '}' -> addToken(OclTokenType.RIGHT_BRACE, "}", start);
            case '[' -> addToken(OclTokenType.LEFT_BRACKET, "[", start);
            case ']' -> addToken(OclTokenType.RIGHT_BRACKET, "]", start);
            case '+' -> addToken(OclTokenType.PLUS, "+", start);
            case '*' -> addToken(OclTokenType.STAR, "*", start);
            case '/' -> addToken(OclTokenType.SLASH, "/", start);
            case '=' -> addToken(OclTokenType.EQUAL, "=", start);
            case '<' -> {
                if (match('=')) {
                    addToken(OclTokenType.LESS_EQUAL, "<=", start);
                } else if (match('>')) {
                    addToken(OclTokenType.NOT_EQUAL, "<>", start);
                } else {
                    addToken(OclTokenType.LESS, "<", start);
                }
            }
            case '>' -> {
                if (match('=')) {
                    addToken(OclTokenType.GREATER_EQUAL, ">=", start);
                } else {
                    addToken(OclTokenType.GREATER, ">", start);
                }
            }
            case '-' -> {
                if (match('>')) {
                    addToken(OclTokenType.ARROW, "->", start);
                } else {
                    addToken(OclTokenType.MINUS, "-", start);
                }
            }
            default -> diagnostics.add(OclDiagnostic.lexerError("INVALID_CHARACTER",
                    "Unexpected character '" + current + "'.",
                    new SourceRange(start, position()),
                    List.of("self", "identifier", "literal", "("),
                    Character.toString(current)));
        }
    }

    private void addToken(OclTokenType type, String text, SourcePosition start) {
        tokens.add(new OclToken(type, text, new SourceRange(start, position())));
    }

    private boolean match(char expected) {
        if (isAtEnd() || peek() != expected) {
            return false;
        }
        advance();
        return true;
    }

    private char advance() {
        char current = source.charAt(index++);
        if (current == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        return current;
    }

    private char peek() {
        return source.charAt(index);
    }

    private boolean hasNextDigit() {
        return index + 1 < source.length() && Character.isDigit(source.charAt(index + 1));
    }

    private boolean hasExponentDigits() {
        int lookahead = index + 1;
        if (lookahead < source.length()
                && (source.charAt(lookahead) == '+' || source.charAt(lookahead) == '-')) {
            lookahead++;
        }
        return lookahead < source.length() && Character.isDigit(source.charAt(lookahead));
    }

    private boolean isAtEnd() {
        return index >= source.length();
    }

    private SourcePosition position() {
        return new SourcePosition(line, column, index);
    }

    private boolean isIdentifierStart(char current) {
        return Character.isLetter(current) || current == '_';
    }

    private boolean isIdentifierPart(char current) {
        return Character.isLetterOrDigit(current) || current == '_';
    }
}
