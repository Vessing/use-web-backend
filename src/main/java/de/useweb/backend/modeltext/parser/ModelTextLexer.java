package de.useweb.backend.modeltext.parser;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import de.useweb.backend.api.dto.ocl.SourceRangeDto;

final class ModelTextLexer {

    enum TokenType {
        IDENTIFIER,
        NUMBER,
        STRING,
        SYMBOL,
        NEWLINE,
        EOF
    }

    record Token(TokenType type, String text, SourceRangeDto range) {
        boolean is(String value) {
            return text.equals(value);
        }

        boolean isKeyword(String value) {
            return type == TokenType.IDENTIFIER && text.equalsIgnoreCase(value);
        }
    }

    record LexResult(String source, List<Token> tokens, List<LexerProblem> problems) {
    }

    record LexerProblem(String code, String message, SourceRangeDto range, String actual) {
    }

    LexResult lex(String input) {
        String source = normalize(input);
        List<Token> tokens = new ArrayList<>();
        List<LexerProblem> problems = new ArrayList<>();
        Cursor cursor = new Cursor(source);

        while (!cursor.atEnd()) {
            char current = cursor.current();
            if (current == ' ' || current == '\t' || current == '\f') {
                cursor.advance();
                continue;
            }
            if (current == '\n') {
                Position start = cursor.position();
                cursor.advance();
                tokens.add(token(TokenType.NEWLINE, "\n", start, cursor.position()));
                continue;
            }
            if (current == '-' && cursor.peek(1) == '-') {
                skipLineComment(cursor);
                continue;
            }
            if (current == '/' && cursor.peek(1) == '/') {
                skipLineComment(cursor);
                continue;
            }
            if (current == '/' && cursor.peek(1) == '*') {
                lexBlockComment(cursor, tokens, problems);
                continue;
            }
            if (current == '\'' || current == '"') {
                lexString(cursor, tokens, problems);
                continue;
            }
            if (Character.isJavaIdentifierStart(current)) {
                lexIdentifier(cursor, tokens);
                continue;
            }
            if (Character.isDigit(current)) {
                lexNumber(cursor, tokens);
                continue;
            }
            lexSymbol(cursor, tokens);
        }

        Position eof = cursor.position();
        tokens.add(token(TokenType.EOF, "", eof, eof));
        return new LexResult(source, List.copyOf(tokens), List.copyOf(problems));
    }

    String decode(byte[] sourceBytes) {
        if (sourceBytes == null || sourceBytes.length == 0) {
            return "";
        }
        if (startsWith(sourceBytes, 0xEF, 0xBB, 0xBF)) {
            return new String(sourceBytes, 3, sourceBytes.length - 3, StandardCharsets.UTF_8);
        }
        if (startsWith(sourceBytes, 0xFE, 0xFF)) {
            return new String(sourceBytes, 2, sourceBytes.length - 2, StandardCharsets.UTF_16BE);
        }
        if (startsWith(sourceBytes, 0xFF, 0xFE)) {
            return new String(sourceBytes, 2, sourceBytes.length - 2, StandardCharsets.UTF_16LE);
        }
        try {
            return decodeStrict(sourceBytes, StandardCharsets.UTF_8);
        } catch (CharacterCodingException ignored) {
            return new String(sourceBytes, Charset.forName("windows-1252"));
        }
    }

    private String decodeStrict(byte[] sourceBytes, Charset charset) throws CharacterCodingException {
        return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(sourceBytes))
                .toString();
    }

    private boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (Byte.toUnsignedInt(bytes[index]) != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private String normalize(String input) {
        String source = input == null ? "" : input;
        if (!source.isEmpty() && (source.charAt(0) == '\uFEFF' || source.charAt(0) == '\uFFFE')) {
            source = source.substring(1);
        }
        return source.replace("\r\n", "\n").replace('\r', '\n');
    }

    private void skipLineComment(Cursor cursor) {
        while (!cursor.atEnd() && cursor.current() != '\n') {
            cursor.advance();
        }
    }

    private void lexBlockComment(Cursor cursor, List<Token> tokens, List<LexerProblem> problems) {
        Position start = cursor.position();
        cursor.advance(2);
        while (!cursor.atEnd()) {
            if (cursor.current() == '*' && cursor.peek(1) == '/') {
                cursor.advance(2);
                return;
            }
            if (cursor.current() == '\n') {
                Position newlineStart = cursor.position();
                cursor.advance();
                tokens.add(token(TokenType.NEWLINE, "\n", newlineStart, cursor.position()));
            } else {
                cursor.advance();
            }
        }
        problems.add(new LexerProblem(
                "UNTERMINATED_BLOCK_COMMENT",
                "Der Blockkommentar wurde nicht mit '*/' abgeschlossen.",
                range(start, cursor.position()),
                "EOF"));
    }

    private void lexString(Cursor cursor, List<Token> tokens, List<LexerProblem> problems) {
        Position start = cursor.position();
        int startOffset = start.offset();
        char quote = cursor.current();
        cursor.advance();
        boolean escaped = false;
        while (!cursor.atEnd()) {
            char current = cursor.current();
            if (!escaped && current == quote) {
                cursor.advance();
                tokens.add(token(TokenType.STRING, cursor.slice(startOffset), start, cursor.position()));
                return;
            }
            if (!escaped && current == '\n') {
                break;
            }
            escaped = !escaped && current == '\\';
            if (current != '\\') {
                escaped = false;
            }
            cursor.advance();
        }
        problems.add(new LexerProblem(
                "UNTERMINATED_STRING",
                "Das String-Literal wurde nicht abgeschlossen.",
                range(start, cursor.position()),
                cursor.slice(startOffset)));
        tokens.add(token(TokenType.STRING, cursor.slice(startOffset), start, cursor.position()));
    }

    private void lexIdentifier(Cursor cursor, List<Token> tokens) {
        Position start = cursor.position();
        int startOffset = start.offset();
        cursor.advance();
        while (!cursor.atEnd() && Character.isJavaIdentifierPart(cursor.current())) {
            cursor.advance();
        }
        tokens.add(token(TokenType.IDENTIFIER, cursor.slice(startOffset), start, cursor.position()));
    }

    private void lexNumber(Cursor cursor, List<Token> tokens) {
        Position start = cursor.position();
        int startOffset = start.offset();
        cursor.advance();
        while (!cursor.atEnd() && Character.isDigit(cursor.current())) {
            cursor.advance();
        }
        tokens.add(token(TokenType.NUMBER, cursor.slice(startOffset), start, cursor.position()));
    }

    private void lexSymbol(Cursor cursor, List<Token> tokens) {
        Position start = cursor.position();
        int startOffset = start.offset();
        String pair = "" + cursor.current() + cursor.peek(1);
        if (List.of("::", "->", "<=", ">=", "<>", ":=", "..", "=>").contains(pair)) {
            cursor.advance(2);
        } else {
            cursor.advance();
        }
        tokens.add(token(TokenType.SYMBOL, cursor.slice(startOffset), start, cursor.position()));
    }

    private Token token(TokenType type, String text, Position start, Position end) {
        return new Token(type, text, range(start, end));
    }

    private SourceRangeDto range(Position start, Position end) {
        return new SourceRangeDto(start.line(), start.column(), start.offset(), end.line(), end.column(), end.offset());
    }

    private record Position(int line, int column, int offset) {
    }

    private static final class Cursor {
        private final String source;
        private int index;
        private int line = 1;
        private int column = 1;

        private Cursor(String source) {
            this.source = source;
        }

        private boolean atEnd() {
            return index >= source.length();
        }

        private char current() {
            return source.charAt(index);
        }

        private char peek(int distance) {
            int target = index + distance;
            return target >= source.length() ? '\0' : source.charAt(target);
        }

        private Position position() {
            return new Position(line, column, index);
        }

        private void advance() {
            if (atEnd()) {
                return;
            }
            char current = source.charAt(index++);
            if (current == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }

        private void advance(int count) {
            for (int index = 0; index < count; index++) {
                advance();
            }
        }

        private String slice(int startOffset) {
            return source.substring(startOffset, index);
        }
    }
}
