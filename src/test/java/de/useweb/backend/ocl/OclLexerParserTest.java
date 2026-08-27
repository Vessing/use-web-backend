package de.useweb.backend.ocl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.useweb.backend.ocl.lexer.OclLexResult;
import de.useweb.backend.ocl.lexer.OclLexer;
import de.useweb.backend.ocl.lexer.OclTokenType;
import de.useweb.backend.ocl.parser.OclParseResult;
import de.useweb.backend.ocl.parser.OclParser;
import de.useweb.backend.ocl.profile.OclComplianceProfile;

class OclLexerParserTest {

    private final OclLexer lexer = new OclLexer();
    private final OclParser parser = new OclParser();

    @Test
    void tokenizesSimpleAttributeComparison() {
        OclLexResult result = lexer.tokenize("self.books <= 5");

        assertThat(result.success()).isTrue();
        assertThat(result.tokens())
                .extracting(token -> token.type())
                .containsExactly(
                        OclTokenType.SELF,
                        OclTokenType.DOT,
                        OclTokenType.IDENTIFIER,
                        OclTokenType.LESS_EQUAL,
                        OclTokenType.INTEGER_LITERAL,
                        OclTokenType.EOF);
        assertThat(result.tokens().get(2).text()).isEqualTo("books");
        assertThat(result.tokens().get(2).sourceRange().start().column()).isEqualTo(6);
    }

    @Test
    void tokenizesEmptyStringLiteral() {
        OclLexResult result = lexer.tokenize("self.name <> ''");

        assertThat(result.success()).isTrue();
        assertThat(result.tokens())
                .extracting(token -> token.type())
                .contains(OclTokenType.STRING_LITERAL);
        assertThat(result.tokens().stream().filter(token -> token.type() == OclTokenType.STRING_LITERAL).findFirst().orElseThrow().text())
                .isEqualTo("''");
    }

    @Test
    void tokenizesCollectionSizeCall() {
        OclLexResult result = lexer.tokenize("self.borrowedBooks->size() <= 5");

        assertThat(result.success()).isTrue();
        assertThat(result.tokens())
                .extracting(token -> token.type())
                .containsSubsequence(
                        OclTokenType.ARROW,
                        OclTokenType.IDENTIFIER,
                        OclTokenType.LEFT_PAREN,
                        OclTokenType.RIGHT_PAREN);
    }

    @Test
    void parsesNullAndInvalidLiterals() {
        OclLexResult lexResult = lexer.tokenize("null = null or invalid");

        assertThat(lexResult.success()).isTrue();
        assertThat(lexResult.tokens()).extracting(token -> token.type())
                .contains(OclTokenType.NULL_LITERAL, OclTokenType.INVALID_LITERAL);
        assertThat(parser.parse("null = null").success()).isTrue();
        assertThat(parser.parse("invalid").success()).isTrue();
    }

    @Test
    void parsesMvpExpressionWithCollectionOperation() {
        OclParseResult result = parser.parse("self.borrowedBooks->notEmpty() and self.books <= 5");

        assertThat(result.success()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void parseErrorContainsSyntaxCodeAndLocation() {
        OclParseResult result = parser.parse("self.books <=");

        assertThat(result.success()).isFalse();
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.diagnostics().getFirst().code()).isEqualTo("UNEXPECTED_TOKEN");
        assertThat(result.diagnostics().getFirst().phase().name()).isEqualTo("PARSER");
        assertThat(result.diagnostics().getFirst().sourceRange().start().line()).isEqualTo(1);
        assertThat(result.diagnostics().getFirst().sourceRange().start().column()).isEqualTo(14);
        assertThat(result.diagnostics().getFirst().actual()).isEqualTo("EOF");
    }

    @Test
    void tracksUtf16OffsetsAcrossCrLfAndUnicode() {
        OclLexResult result = lexer.tokenize("'\uD83D\uDE00'\r\ntrue");

        assertThat(result.success()).isTrue();
        assertThat(result.tokens().get(0).sourceRange().end().offset()).isEqualTo(4);
        assertThat(result.tokens().get(1).sourceRange().start().line()).isEqualTo(2);
        assertThat(result.tokens().get(1).sourceRange().start().column()).isEqualTo(1);
        assertThat(result.tokens().get(1).sourceRange().start().offset()).isEqualTo(6);
    }

    @Test
    void decodesOcl24EscapesAndAdjacentStringFragments() {
        var result = parser.parse("'line\\n'  'quote\\\' and \\u0041'");

        assertThat(result.success()).isTrue();
        assertThat(result.ast()).extracting("value").isEqualTo("line\nquote' and A");
    }

    @Test
    void reportsInvalidEscapesAndOutOfRangeIntegerLiterals() {
        assertThat(parser.parse("'bad\\q'").diagnostics()).anySatisfy(diagnostic ->
                assertThat(diagnostic.code()).isEqualTo("INVALID_ESCAPE_SEQUENCE"));
        assertThat(parser.parse("2147483648").diagnostics()).anySatisfy(diagnostic ->
                assertThat(diagnostic.code()).isEqualTo("INTEGER_LITERAL_OUT_OF_RANGE"));
    }

    @Test
    void tokenizesAndParsesExponentialRealLiterals() {
        OclLexResult lexResult = lexer.tokenize("13e14 + 12.34E+15");

        assertThat(lexResult.success()).isTrue();
        assertThat(lexResult.tokens()).extracting(token -> token.type())
                .containsExactly(OclTokenType.REAL_LITERAL, OclTokenType.PLUS,
                        OclTokenType.REAL_LITERAL, OclTokenType.EOF);
        assertThat(lexResult.tokens().getFirst().text()).isEqualTo("13e14");
        assertThat(lexResult.tokens().getFirst().sourceRange().end().offset()).isEqualTo(5);
        assertThat(lexResult.tokens().get(2).text()).isEqualTo("12.34E+15");
        assertThat(parser.parse("13e14 + 12.34E+15").success()).isTrue();
    }

    @Test
    void reportsIncompleteExponentsAtTheExponentMarker() {
        for (String source : java.util.List.of("13e", "12.34E+")) {
            var result = parser.parse(source);

            assertThat(result.success()).isFalse();
            assertThat(result.diagnostics()).anySatisfy(diagnostic -> {
                assertThat(diagnostic.code()).isEqualTo("MISSING_TOKEN");
                assertThat(diagnostic.sourceRange().start().offset()).isEqualTo(source.indexOf('e') >= 0
                        ? source.indexOf('e') : source.indexOf('E'));
            });
        }
    }

    @Test
    void rejectsInputsBeyondTheTokenBudgetBeforeBuildingAnAst() {
        String expression = "true ".repeat(Math.toIntExact(OclComplianceProfile.MAX_TOKENS + 1));

        var result = parser.parse(expression);

        assertThat(result.success()).isFalse();
        assertThat(result.diagnostics()).anyMatch(diagnostic -> diagnostic.code().equals("TOKEN_LIMIT_EXCEEDED"));
    }

    @Test
    void rejectsDeepParenthesesUnaryChainsAndLeftDeepAstShapes() {
        int excessiveDepth = Math.toIntExact(OclComplianceProfile.MAX_AST_DEPTH + 1);
        for (String expression : java.util.List.of(
                "(".repeat(excessiveDepth) + "true" + ")".repeat(excessiveDepth),
                "not ".repeat(excessiveDepth) + "true",
                "true and ".repeat(excessiveDepth) + "true")) {
            var result = parser.parse(expression);
            assertThat(result.success()).isFalse();
            assertThat(result.diagnostics()).anyMatch(diagnostic ->
                    diagnostic.code().equals("AST_DEPTH_LIMIT_EXCEEDED"));
        }
    }
}
