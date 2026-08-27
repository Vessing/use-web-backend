package de.useweb.backend.ocl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import org.junit.jupiter.api.Test;

import de.useweb.backend.ocl.ast.BinaryExpression;
import de.useweb.backend.ocl.ast.AllInstancesExpression;
import de.useweb.backend.ocl.ast.BinaryOperator;
import de.useweb.backend.ocl.ast.CallNavigationOperator;
import de.useweb.backend.ocl.ast.CollectionLiteralExpression;
import de.useweb.backend.ocl.ast.CollectionRangeItem;
import de.useweb.backend.ocl.ast.LiteralExpression;
import de.useweb.backend.ocl.ast.IteratorExpression;
import de.useweb.backend.ocl.ast.IfExpression;
import de.useweb.backend.ocl.ast.LetExpression;
import de.useweb.backend.ocl.ast.IterateExpression;
import de.useweb.backend.ocl.ast.VariableDeclaration;
import de.useweb.backend.ocl.ast.IteratorKind;
import de.useweb.backend.ocl.ast.ParenthesizedExpression;
import de.useweb.backend.ocl.ast.OperationCallExpression;
import de.useweb.backend.ocl.ast.PropertyAccessExpression;
import de.useweb.backend.ocl.ast.SelfExpression;
import de.useweb.backend.ocl.ast.UnaryExpression;
import de.useweb.backend.ocl.ast.TupleExpression;
import de.useweb.backend.ocl.ast.TypeArgumentCallExpression;
import de.useweb.backend.ocl.ast.UnaryOperator;
import de.useweb.backend.ocl.parser.OclParser;
import de.useweb.backend.ocl.collection.CollectionKind;

class OclAstParserTest {

    @Test
    void preservesTheNavigationOperatorOfOperationCalls() {
        assertThat(new OclParser().parse("Sequence{'a'}.toUpperCase()").ast())
                .asInstanceOf(type(OperationCallExpression.class))
                .extracting(OperationCallExpression::navigationOperator)
                .isEqualTo(CallNavigationOperator.DOT);
        assertThat(new OclParser().parse("Sequence{'a'}->toUpperCase()").ast())
                .asInstanceOf(type(OperationCallExpression.class))
                .extracting(OperationCallExpression::navigationOperator)
                .isEqualTo(CallNavigationOperator.ARROW);
    }

    @Test
    void parsesAllInstancesWithAndWithoutParenthesesAsStaticTypeExpression() {
        assertThat(new OclParser().parse("Book.allInstances()").ast())
                .asInstanceOf(type(AllInstancesExpression.class))
                .satisfies(expression -> {
                    assertThat(expression.typeName()).isEqualTo("Book");
                    assertThat(expression.typeRange().start().offset()).isZero();
                    assertThat(expression.operationRange().start().offset()).isEqualTo(5);
                    assertThat(expression.sourceRange().end().offset()).isEqualTo(19);
                });
        assertThat(new OclParser().parse("Book.allInstances").ast())
                .isInstanceOf(AllInstancesExpression.class);
        assertThat(new OclParser().parse("Book.allInstances(").diagnostics())
                .anyMatch(diagnostic -> diagnostic.code().equals("MISSING_TOKEN"));
    }

    @Test
    void parsesNestedLetExpressionsWithOptionalTypeAndLocalRanges() {
        String expression = "let limit : Integer = 5 in let limit = 3 in limit";

        var result = new OclParser().parse(expression);

        assertThat(result.success()).isTrue();
        assertThat(result.ast()).asInstanceOf(type(LetExpression.class)).satisfies(let -> {
            assertThat(let.variable().name()).isEqualTo("limit");
            assertThat(let.variable().declaredTypeName()).isEqualTo("Integer");
            assertThat(let.initializerRange().start().offset()).isEqualTo(22);
            assertThat(let.body()).isInstanceOf(LetExpression.class);
            assertThat(let.sourceRange().end().offset()).isEqualTo(expression.length());
        });
    }

    @Test
    void reportsMissingLetSyntaxAtTheLocalToken() {
        assertThat(new OclParser().parse("let = 5 in true").diagnostics().getFirst().message())
                .contains("let variable name");
        assertThat(new OclParser().parse("let limit 5 in true").diagnostics().getFirst().message())
                .contains("let initializer");
        assertThat(new OclParser().parse("let limit = 5 true").diagnostics().getFirst().message())
                .contains("'in'");
    }

    @Test
    void desugarsCommaSeparatedLetDeclarationsIntoNestedBindings() {
        var result = new OclParser().parse("let a = 2, b = a + 1 in a + b");

        assertThat(result.success()).isTrue();
        assertThat(result.ast()).asInstanceOf(type(LetExpression.class)).satisfies(outer -> {
            assertThat(outer.variable().name()).isEqualTo("a");
            assertThat(outer.body()).asInstanceOf(type(LetExpression.class))
                    .satisfies(inner -> assertThat(inner.variable().name()).isEqualTo("b"));
        });
    }

    private final OclParser parser = new OclParser();

    @Test
    void parsesNestedIfExpressionsWithBranchRanges() {
        String expression = "if true then if false then 1 else 2 endif else 3 endif";
        var result = parser.parse(expression);

        assertThat(result.success()).isTrue();
        assertThat(result.ast()).asInstanceOf(type(IfExpression.class)).satisfies(ifExpression -> {
            assertThat(ifExpression.thenExpression()).isInstanceOf(IfExpression.class);
            assertThat(ifExpression.conditionRange().start().offset()).isEqualTo(3);
            assertThat(ifExpression.thenRange().start().offset()).isEqualTo(13);
            assertThat(ifExpression.elseRange().start().offset()).isGreaterThan(ifExpression.thenRange().end().offset());
            assertThat(ifExpression.sourceRange().end().offset()).isEqualTo(expression.length());
        });
    }

    @Test
    void reportsMissingIfKeywordsLocally() {
        assertThat(parser.parse("if true 1 else 2 endif").diagnostics())
                .anySatisfy(diagnostic -> {
                    assertThat(diagnostic.code()).isEqualTo("MISSING_TOKEN");
                    assertThat(diagnostic.message()).contains("then");
                });
        assertThat(parser.parse("if true then 1 endif").diagnostics())
                .anySatisfy(diagnostic -> assertThat(diagnostic.message()).contains("else"));
        assertThat(parser.parse("if true then 1 else 2").diagnostics())
                .anySatisfy(diagnostic -> assertThat(diagnostic.message()).contains("endif"));
    }

    @Test
    void parsesIterateAccumulatorAndNestedCollectionType() {
        var result = parser.parse("Sequence{1, 2}->iterate(i : Integer; acc : Sequence(Integer) = Sequence{} | acc->including(i))");

        assertThat(result.success()).isTrue();
        assertThat(result.ast()).asInstanceOf(type(IterateExpression.class)).satisfies(iterate -> {
            assertThat(iterate.iterators()).hasSize(1);
            assertThat(iterate.iterators().getFirst().name()).isEqualTo("i");
            assertThat(iterate.iterators().getFirst().declaredTypeName()).isEqualTo("Integer");
            assertThat(iterate.accumulator().name()).isEqualTo("acc");
            assertThat(iterate.accumulator().declaredTypeName()).isEqualTo("Sequence(Integer)");
            assertThat(iterate.initializer()).isNotNull();
            assertThat(iterate.body()).isNotNull();
        });
    }

    @Test
    void parsesMultipleIterateVariablesWithStableRanges() {
        String source = "Sequence{1, 2}->iterate(left, right : Integer; acc : Integer = 0 | acc + left * right)";

        var result = parser.parse(source);

        assertThat(result.success()).isTrue();
        assertThat(result.ast()).asInstanceOf(type(IterateExpression.class)).satisfies(iterate -> {
            assertThat(iterate.iterators()).extracting(VariableDeclaration::name)
                    .containsExactly("left", "right");
            assertThat(iterate.iterators().getFirst().nameRange().start().offset()).isEqualTo(24);
            assertThat(iterate.iterators().get(1).declaredTypeName()).isEqualTo("Integer");
            assertThat(iterate.sourceRange().end().offset()).isEqualTo(source.length());
        });
    }

    @Test
    void reportsIncompleteMultipleIterateDeclarationsLocally() {
        String source = "Sequence{1}->iterate(left, ; acc : Integer = 0 | acc)";

        var result = parser.parse(source);

        assertThat(result.success()).isFalse();
        assertThat(result.diagnostics()).anySatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("MISSING_TOKEN");
            assertThat(diagnostic.message()).contains("iterator variable name");
            assertThat(diagnostic.sourceRange().start().offset()).isEqualTo(source.indexOf(';'));
        });
    }

    @Test
    void simpleAttributeComparisonCreatesBinaryExpression() {
        var result = parser.parse("self.books <= 5");

        assertThat(result.success()).isTrue();
        assertThat(result.ast()).asInstanceOf(type(BinaryExpression.class))
                .satisfies(binary -> {
                    assertThat(binary.operator()).isEqualTo(BinaryOperator.LESS_EQUAL);
                    assertThat(binary.left()).isInstanceOf(PropertyAccessExpression.class);
                    assertThat(binary.right()).isInstanceOf(LiteralExpression.class);
                    assertThat(binary.sourceRange().start().column()).isEqualTo(1);
                });
    }

    @Test
    void collectionSizeComparisonCreatesGeneralOperationCallExpression() {
        var result = parser.parse("self.borrowedBooks->size() <= 5");

        assertThat(result.success()).isTrue();
        assertThat(result.ast()).asInstanceOf(type(BinaryExpression.class))
                .satisfies(binary -> {
                    assertThat(binary.left()).asInstanceOf(type(OperationCallExpression.class))
                            .satisfies(call -> {
                                assertThat(call.operationName()).isEqualTo("size");
                                assertThat(call.arguments()).isEmpty();
                                assertThat(call.receiver()).isInstanceOf(PropertyAccessExpression.class);
                            });
                    assertThat(binary.right()).isInstanceOf(LiteralExpression.class);
                });
    }

    @Test
    void respectsArithmeticAndBooleanPrecedence() {
        var result = parser.parse("1 + 2 * 3 = 7 implies false xor true");

        assertThat(result.success()).isTrue();
        assertThat(result.ast()).asInstanceOf(type(BinaryExpression.class))
                .satisfies(implies -> {
                    assertThat(implies.operator()).isEqualTo(BinaryOperator.IMPLIES);
                    assertThat(implies.left()).asInstanceOf(type(BinaryExpression.class))
                            .satisfies(equality -> assertThat(equality.operator()).isEqualTo(BinaryOperator.EQUAL));
                    assertThat(implies.right()).asInstanceOf(type(BinaryExpression.class))
                            .satisfies(xor -> assertThat(xor.operator()).isEqualTo(BinaryOperator.XOR));
                });
    }

    @Test
    void parsesCallArgumentsAndParameterlessArrowProfile() {
        assertThat(parser.parse("'a'.concat('b')").ast()).asInstanceOf(type(OperationCallExpression.class))
                .satisfies(call -> assertThat(call.arguments()).hasSize(1));
        assertThat(parser.parse("self.borrowedBooks->size").ast()).asInstanceOf(type(OperationCallExpression.class))
                .satisfies(call -> assertThat(call.arguments()).isEmpty());
    }

    @Test
    void parsesCollectionKindsItemsAndRanges() {
        var result = parser.parse("Sequence{1, 2..4, Set{5}}");

        assertThat(result.success()).isTrue();
        assertThat(result.ast()).asInstanceOf(type(CollectionLiteralExpression.class))
                .satisfies(literal -> {
                    assertThat(literal.collectionKind()).isEqualTo(CollectionKind.SEQUENCE);
                    assertThat(literal.parts()).hasSize(3);
                    assertThat(literal.parts().get(1)).isInstanceOf(CollectionRangeItem.class);
                    assertThat(literal.parts().get(2)).extracting("expression")
                            .isInstanceOf(CollectionLiteralExpression.class);
                });
    }

    @Test
    void parsesImplicitOperationsAndCollectionTypeArguments() {
        var undefinedItem = parser.parse("Bag{1}->including(oclUndefined(Integer))");
        var collectionTypeArgument = parser.parse("Set{1}->oclIsKindOf(Set(OclAny))");

        assertThat(undefinedItem.success()).isTrue();
        assertThat(undefinedItem.ast()).isInstanceOf(OperationCallExpression.class);
        assertThat(collectionTypeArgument.success()).isTrue();
        assertThat(collectionTypeArgument.ast()).asInstanceOf(type(TypeArgumentCallExpression.class))
                .extracting(TypeArgumentCallExpression::typeName).isEqualTo("Set(OclAny)");
    }

    @Test
    void notExpressionCreatesUnaryExpression() {
        var result = parser.parse("not self.available");

        assertThat(result.success()).isTrue();
        assertThat(result.ast()).asInstanceOf(type(UnaryExpression.class))
                .satisfies(unary -> {
                    assertThat(unary.operator()).isEqualTo(UnaryOperator.NOT);
                    assertThat(unary.expression()).isInstanceOf(PropertyAccessExpression.class);
                });
    }

    @Test
    void parenthesesChangeAstStructure() {
        var result = parser.parse("(self.books <= 5) and self.active");

        assertThat(result.success()).isTrue();
        assertThat(result.ast()).asInstanceOf(type(BinaryExpression.class))
                .satisfies(andExpression -> {
                    assertThat(andExpression.operator()).isEqualTo(BinaryOperator.AND);
                    assertThat(andExpression.left()).isInstanceOf(ParenthesizedExpression.class);
                    assertThat(andExpression.right()).asInstanceOf(type(PropertyAccessExpression.class))
                            .satisfies(property -> {
                                assertThat(property.receiver()).isInstanceOf(SelfExpression.class);
                                assertThat(property.propertyName()).isEqualTo("active");
                            });
                });
    }

    @Test
    void parsesTupleLiteralsAndTypeArgumentCallsAsDedicatedAstNodes() {
        var tuple = parser.parse("Tuple{answer = 42, title = 'USE'}.answer");
        var typeCall = parser.parse("self.oclIsKindOf(User)");

        assertThat(tuple.success()).isTrue();
        assertThat(tuple.ast()).asInstanceOf(type(PropertyAccessExpression.class))
                .extracting(PropertyAccessExpression::receiver)
                .asInstanceOf(type(TupleExpression.class))
                .satisfies(expression -> assertThat(expression.parts()).extracting("name")
                        .containsExactly("answer", "title"));
        assertThat(typeCall.success()).isTrue();
        assertThat(typeCall.ast()).asInstanceOf(type(TypeArgumentCallExpression.class))
                .satisfies(expression -> {
                    assertThat(expression.operationName()).isEqualTo("oclIsKindOf");
                    assertThat(expression.typeName()).isEqualTo("User");
                });
    }

    @Test
    void parsesIteratorDeclarationsBodyAndSourceRanges() {
        var result = parser.parse("Set{1}->forAll(left : Integer, right | left <> right)");

        assertThat(result.success()).isTrue();
        assertThat(result.ast()).asInstanceOf(type(IteratorExpression.class))
                .satisfies(iterator -> {
                    assertThat(iterator.kind()).isEqualTo(IteratorKind.FOR_ALL);
                    assertThat(iterator.variables()).hasSize(2);
                    assertThat(iterator.variables().get(0).name()).isEqualTo("left");
                    assertThat(iterator.variables().get(0).declaredTypeName()).isEqualTo("Integer");
                    assertThat(iterator.variables().get(0).nameRange().start().offset()).isEqualTo(15);
                    assertThat(iterator.variables().get(1).declaredTypeName()).isNull();
                    assertThat(iterator.body()).isInstanceOf(BinaryExpression.class);
                    assertThat(iterator.bodyRange()).isEqualTo(iterator.body().sourceRange());
                    assertThat(iterator.operationRange().start().offset()).isEqualTo(8);
                });
    }

    @Test
    void parsesNestedIteratorsAsDedicatedAstNodes() {
        var result = parser.parse("Set{1}->forAll(x | Set{2}->exists(x | x = 2))");

        assertThat(result.success()).isTrue();
        assertThat(result.ast()).asInstanceOf(type(IteratorExpression.class))
                .satisfies(outer -> assertThat(outer.body()).isInstanceOf(IteratorExpression.class));
    }
}
