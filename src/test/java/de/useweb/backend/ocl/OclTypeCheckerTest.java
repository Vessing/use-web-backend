package de.useweb.backend.ocl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.useweb.backend.domain.uml.Multiplicity;
import de.useweb.backend.domain.uml.UmlAssociation;
import de.useweb.backend.domain.uml.UmlAssociationEnd;
import de.useweb.backend.domain.uml.UmlAssociationEndId;
import de.useweb.backend.domain.uml.UmlAssociationId;
import de.useweb.backend.domain.uml.UmlAttribute;
import de.useweb.backend.domain.uml.UmlAttributeId;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlModelId;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.ocl.parser.OclParser;
import de.useweb.backend.ocl.collection.CollectionKind;
import de.useweb.backend.ocl.typecheck.OclType;
import de.useweb.backend.ocl.typecheck.OclTypeChecker;
import de.useweb.backend.ocl.typecheck.TypeEnvironment;

class OclTypeCheckerTest {

    private static final UmlClassId USER_CLASS_ID = new UmlClassId("class-user");
    private static final UmlClassId BOOK_CLASS_ID = new UmlClassId("class-book");

    private final OclParser parser = new OclParser();
    private final OclTypeChecker typeChecker = new OclTypeChecker();
    private final UmlModel libraryModel = libraryModel();

    @Test
    void typesAllInstancesAsSetOfTheResolvedExactClass() {
        assertThat(expressionType("Book.allInstances()"))
                .isEqualTo(OclType.collectionOf(CollectionKind.SET,
                        OclType.classType(BOOK_CLASS_ID, "Book")));
        assertThat(expressionType("Book.allInstances()->exists(book | book.title = 'Moby Dick')"))
                .isEqualTo(OclType.BOOLEAN);

        var unknown = expressionTypecheck("Missing.allInstances()");
        assertThat(unknown.diagnostics()).anySatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("UNKNOWN_CLASS");
            assertThat(diagnostic.sourceRange().start().offset()).isZero();
        });
    }

    @Test
    void typechecksLetBindingsAnnotationsShadowingAndScopeBoundaries() {
        assertThat(expressionType("let limit = 5 in limit + 1")).isEqualTo(OclType.INTEGER);
        assertThat(expressionType("let limit : Real = 5 in limit")).isEqualTo(OclType.REAL);
        assertThat(expressionType("let limit = 5 in let limit = 2.5 in limit")).isEqualTo(OclType.REAL);
        assertThat(expressionType("let user = self in user.books <= 5")).isEqualTo(OclType.BOOLEAN);
        assertThat(expressionType("let a = 2, b = a + 1 in a + b")).isEqualTo(OclType.INTEGER);

        var mismatch = expressionTypecheck("let limit : Integer = '5' in limit");
        assertThat(mismatch.diagnostics()).anySatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("LET_TYPE_MISMATCH");
            assertThat(diagnostic.actual()).isEqualTo("String");
        });

        var leaked = expressionTypecheck("(let limit = 5 in limit) + limit");
        assertThat(leaked.diagnostics()).anyMatch(diagnostic -> diagnostic.code().equals("UNKNOWN_VARIABLE"));

        var selfReference = expressionTypecheck("let limit = limit in limit");
        assertThat(selfReference.diagnostics()).anyMatch(diagnostic -> diagnostic.code().equals("UNKNOWN_VARIABLE"));
    }

    @Test
    void typechecksIfConditionsAndComputesBranchLeastUpperBounds() {
        assertThat(expressionType("if true then 1 else 2 endif")).isEqualTo(OclType.INTEGER);
        assertThat(expressionType("if true then 1 else 2.5 endif")).isEqualTo(OclType.REAL);
        assertThat(expressionType("if true then Set{1} else Sequence{2} endif")).isEqualTo(
                OclType.collectionOf(CollectionKind.COLLECTION, OclType.INTEGER));
        assertThat(expressionType("if true then if false then 1 else 2 endif else 3 endif"))
                .isEqualTo(OclType.INTEGER);

        var invalidCondition = expressionTypecheck("if 'yes' then 1 else 2 endif");
        assertThat(invalidCondition.diagnostics()).anySatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("INVALID_IF_CONDITION_TYPE");
            assertThat(diagnostic.sourceRange().start().offset()).isEqualTo(3);
            assertThat(diagnostic.actual()).isEqualTo("String");
        });
    }

    @Test
    void acceptsIntegerAttributeComparisonAsBooleanInvariant() {
        var result = typecheck("self.books <= 5");

        assertThat(result.success()).isTrue();
        assertThat(result.resultType()).isEqualTo(OclType.BOOLEAN);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void rejectsInvalidComparisonBetweenStringAndInteger() {
        var result = typecheck("self.name <= 5");

        assertThat(result.success()).isFalse();
        assertThat(result.diagnostics())
                .anySatisfy(diagnostic -> {
                    assertThat(diagnostic.code()).isEqualTo("TYPE_ERROR");
                    assertThat(diagnostic.actual()).isEqualTo("String and Integer");
                });
    }

    @Test
    void reportsUnknownAttributeOrRole() {
        var result = typecheck("self.unknown");

        assertThat(result.success()).isFalse();
        assertThat(result.diagnostics())
                .anySatisfy(diagnostic -> {
                    assertThat(diagnostic.code()).isEqualTo("UNKNOWN_ATTRIBUTE");
                    assertThat(diagnostic.actual()).isEqualTo("unknown");
                    assertThat(diagnostic.phase().name()).isEqualTo("TYPECHECK");
                    assertThat(diagnostic.sourceRange().start().offset()).isEqualTo(5);
                    assertThat(diagnostic.sourceRange().end().offset()).isEqualTo(12);
                });
    }

    @Test
    void acceptsCollectionNavigationSizeComparison() {
        var result = typecheck("self.borrowedBooks->size() <= 5");

        assertThat(result.success()).isTrue();
        assertThat(result.resultType()).isEqualTo(OclType.BOOLEAN);
    }

    @Test
    void rejectsInvariantExpressionThatDoesNotReturnBoolean() {
        var result = typecheck("self.books");

        assertThat(result.success()).isFalse();
        assertThat(result.diagnostics())
                .anySatisfy(diagnostic -> {
                    assertThat(diagnostic.code()).isEqualTo("TYPE_ERROR");
                    assertThat(diagnostic.message()).isEqualTo("An invariant expression must result in Boolean.");
                    assertThat(diagnostic.actual()).isEqualTo("Integer");
                });
    }

    @Test
    void modelsVoidAndInvalidAsBottomTypes() {
        assertThat(OclType.VOID.conformsTo(OclType.STRING)).isTrue();
        assertThat(OclType.OCL_INVALID.conformsTo(OclType.BOOLEAN)).isTrue();
        assertThat(OclType.INTEGER.conformsTo(OclType.REAL)).isTrue();
        assertThat(OclType.VOID.leastUpperBound(OclType.INTEGER)).isEqualTo(OclType.INTEGER);
        assertThat(OclType.INTEGER.leastUpperBound(OclType.REAL)).isEqualTo(OclType.REAL);
    }

    @Test
    void resolvesOperatorsCallsAndNavigationChains() {
        assertThat(typecheck("self.books + 1 <= 5 implies self.name.concat('!').size() > 0").success()).isTrue();
        assertThat(typecheck("self.borrowedBooks->size <= 5").success()).isTrue();
    }

    @Test
    void rejectsIncompatibleOperationArguments() {
        var result = typecheck("self.name.concat(1) = 'x'");

        assertThat(result.success()).isFalse();
        assertThat(result.diagnostics()).anySatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("INVALID_OPERATION");
            assertThat(diagnostic.message()).contains("Unknown operation").contains("concat");
        });
    }

    @Test
    void infersConcreteCollectionKindsAndElementLeastUpperBounds() {
        assertThat(expressionType("Set{}")).isEqualTo(OclType.collectionOf(CollectionKind.SET, OclType.VOID));
        assertThat(expressionType("Set{1, 2.0}")).isEqualTo(OclType.collectionOf(CollectionKind.SET, OclType.REAL));
        assertThat(expressionType("Bag{1, 'x'}")).isEqualTo(OclType.collectionOf(CollectionKind.BAG, OclType.OCL_ANY));
        assertThat(expressionType("Sequence{Set{1}, Set{2}}")).isEqualTo(
                OclType.collectionOf(CollectionKind.SEQUENCE, OclType.collectionOf(CollectionKind.SET, OclType.INTEGER)));
    }

    @Test
    void checksRangeBoundsAndCollectionConformance() {
        assertThat(expressionType("OrderedSet{1..3}")).isEqualTo(OclType.collectionOf(CollectionKind.ORDERED_SET, OclType.INTEGER));
        assertThat(OclType.collectionOf(CollectionKind.SET, OclType.INTEGER)
                .conformsTo(OclType.collectionOf(OclType.REAL))).isTrue();

        var parseResult = parser.parse("Set{'a'..3}");
        var result = typeChecker.checkExpression(new TypeEnvironment(libraryModel, libraryModel.findClass(USER_CLASS_ID).orElseThrow()), parseResult.ast());
        assertThat(result.success()).isFalse();
        assertThat(result.diagnostics()).anySatisfy(diagnostic -> assertThat(diagnostic.message()).contains("Integer bounds"));
    }

    @Test
    void resolvesCollectionBasisOperationsForEveryCollectionKindAndBottomValue() {
        for (String literal : List.of("Set{}", "Bag{}", "Sequence{}", "OrderedSet{}")) {
            assertThat(expressionType(literal + "->size()")).isEqualTo(OclType.INTEGER);
            assertThat(expressionType(literal + "->size")).isEqualTo(OclType.INTEGER);
            assertThat(expressionType(literal + "->isEmpty()")).isEqualTo(OclType.BOOLEAN);
            assertThat(expressionType(literal + "->notEmpty")).isEqualTo(OclType.BOOLEAN);
        }

        assertThat(expressionType("null->size()")).isEqualTo(OclType.INTEGER);
        assertThat(expressionType("null->isEmpty()")).isEqualTo(OclType.BOOLEAN);
        assertThat(expressionType("invalid->notEmpty()")).isEqualTo(OclType.BOOLEAN);
    }

    @Test
    void rejectsArgumentsAndNonCollectionReceiversForCollectionBasisOperations() {
        assertThat(expressionTypecheck("Set{}->size(1)").success()).isFalse();
        assertThat(expressionTypecheck("1->isEmpty()").success()).isFalse();
    }

    @Test
    void resolvesCollectionMembershipOperationsWithCompatibleElementTypes() {
        for (String literal : List.of("Set{1}", "Bag{1}", "Sequence{1}", "OrderedSet{1}")) {
            assertThat(expressionType(literal + "->includes(1)")).isEqualTo(OclType.BOOLEAN);
            assertThat(expressionType(literal + "->excludes(2)")).isEqualTo(OclType.BOOLEAN);
            assertThat(expressionType(literal + "->includesAll(Bag{1, 1})")).isEqualTo(OclType.BOOLEAN);
            assertThat(expressionType(literal + "->excludesAll(Set{2})")).isEqualTo(OclType.BOOLEAN);
        }
        assertThat(expressionType("Set{}->includes(1)")).isEqualTo(OclType.BOOLEAN);
        assertThat(expressionType("Set{1}->includes(1.0)")).isEqualTo(OclType.BOOLEAN);
    }

    @Test
    void rejectsInvalidCollectionMembershipSignatures() {
        assertThat(expressionTypecheck("Set{1}->includes('x')").success()).isFalse();
        assertThat(expressionTypecheck("Set{1}->includesAll(1)").success()).isFalse();
        assertThat(expressionTypecheck("Set{1}->includes()").success()).isFalse();
        assertThat(expressionTypecheck("Set{1}->excludesAll(Set{'x'})").success()).isFalse();
    }

    @Test
    void resolvesIncludingExcludingAndCountResultTypes() {
        for (String literal : List.of("Set{1}", "Bag{1}", "Sequence{1}", "OrderedSet{1}")) {
            OclType sourceType = expressionType(literal);
            assertThat(expressionType(literal + "->including(2)")).isEqualTo(sourceType);
            assertThat(expressionType(literal + "->excluding(2)")).isEqualTo(sourceType);
            assertThat(expressionType(literal + "->count(1)")).isEqualTo(OclType.INTEGER);
        }
        assertThat(expressionType("Set{}->including(1)")).isEqualTo(
                OclType.collectionOf(CollectionKind.SET, OclType.INTEGER));
        assertThat(expressionType("Sequence{1}->including(2.0)")).isEqualTo(
                OclType.collectionOf(CollectionKind.SEQUENCE, OclType.REAL));
    }

    @Test
    void rejectsInvalidProducingCollectionOperationSignatures() {
        assertThat(expressionType("Set{1}->including('x')")).isEqualTo(
                OclType.collectionOf(CollectionKind.SET, OclType.OCL_ANY));
        assertThat(expressionTypecheck("Set{1}->excluding()").success()).isFalse();
        assertThat(expressionTypecheck("Set{1}->count(1, 2)").success()).isFalse();
        assertThat(expressionTypecheck("1->including(2)").success()).isFalse();
    }

    @Test
    void resolvesCollectionCombinationOverloadMatrix() {
        assertThat(expressionType("Set{1}->union(Set{2.0})")).isEqualTo(
                OclType.collectionOf(CollectionKind.SET, OclType.REAL));
        assertThat(expressionType("Set{1}->union(Bag{2})")).isEqualTo(
                OclType.collectionOf(CollectionKind.BAG, OclType.INTEGER));
        assertThat(expressionType("Bag{1}->union(Set{2})")).isEqualTo(
                OclType.collectionOf(CollectionKind.BAG, OclType.INTEGER));
        assertThat(expressionType("Sequence{1}->union(Sequence{2})")).isEqualTo(
                OclType.collectionOf(CollectionKind.SEQUENCE, OclType.INTEGER));
        assertThat(expressionType("OrderedSet{1}->union(OrderedSet{2})")).isEqualTo(
                OclType.collectionOf(CollectionKind.ORDERED_SET, OclType.INTEGER));
        assertThat(expressionType("Bag{1}->intersection(Set{2})")).isEqualTo(
                OclType.collectionOf(CollectionKind.SET, OclType.INTEGER));
        assertThat(expressionType("Set{1}->intersection(Set{2})")).isEqualTo(
                OclType.collectionOf(CollectionKind.SET, OclType.INTEGER));
        assertThat(expressionType("Set{1}->intersection(Bag{2})")).isEqualTo(
                OclType.collectionOf(CollectionKind.SET, OclType.INTEGER));
        assertThat(expressionType("Bag{1}->intersection(Bag{2})")).isEqualTo(
                OclType.collectionOf(CollectionKind.BAG, OclType.INTEGER));
        assertThat(expressionType("OrderedSet{1}->intersection(OrderedSet{2})")).isEqualTo(
                OclType.collectionOf(CollectionKind.ORDERED_SET, OclType.INTEGER));

        assertThat(expressionTypecheck("Sequence{1}->intersection(Set{1})").success()).isFalse();
        assertThat(expressionTypecheck("Set{1}->union(Sequence{1})").success()).isFalse();
    }

    @Test
    void resolvesFlattenAndCollectionConversions() {
        assertThat(expressionType("Set{Sequence{1}, Sequence{2}}->flatten()")).isEqualTo(
                OclType.collectionOf(CollectionKind.SET, OclType.INTEGER));
        assertThat(expressionType("Sequence{Set{Sequence{1}}}->flatten()")).isEqualTo(
                OclType.collectionOf(CollectionKind.SEQUENCE, OclType.INTEGER));
        assertThat(expressionType("Bag{1}->asSet()")).isEqualTo(
                OclType.collectionOf(CollectionKind.SET, OclType.INTEGER));
        assertThat(expressionType("Set{1}->asBag()")).isEqualTo(
                OclType.collectionOf(CollectionKind.BAG, OclType.INTEGER));
        assertThat(expressionType("Set{1}->asSequence()")).isEqualTo(
                OclType.collectionOf(CollectionKind.SEQUENCE, OclType.INTEGER));
        assertThat(expressionType("Sequence{1}->asOrderedSet()")).isEqualTo(
                OclType.collectionOf(CollectionKind.ORDERED_SET, OclType.INTEGER));
        assertThat(expressionType("Set{}->asBag()")).isEqualTo(
                OclType.collectionOf(CollectionKind.BAG, OclType.VOID));
    }

    @Test
    void bindsIteratorVariablesInImmutableNestedScopes() {
        var simple = expressionTypecheck("Set{1}->forAll(i | i > 0)");
        var shadowed = expressionTypecheck("Set{1}->forAll(i | Set{2}->exists(i | i > 0))");

        assertThat(simple.success()).isTrue();
        assertThat(simple.resultType()).isEqualTo(OclType.BOOLEAN);
        assertThat(shadowed.success()).isTrue();
        assertThat(shadowed.resultType()).isEqualTo(OclType.BOOLEAN);
    }

    @Test
    void enforcesBooleanBodiesAndCollectionSourcesForQuantifiers() {
        assertThat(expressionType("Set{1}->forAll(i | i > 0)")).isEqualTo(OclType.BOOLEAN);
        assertThat(expressionType("Set{1}->exists(i | i = 1)")).isEqualTo(OclType.BOOLEAN);
        assertThat(expressionType("Set{1}->forAll(left, right | left = right)")).isEqualTo(OclType.BOOLEAN);
        assertThat(expressionType("null->exists(i | true)")).isEqualTo(OclType.BOOLEAN);

        var wrongBody = expressionTypecheck("Set{1}->forAll(i | i)");
        var wrongSource = expressionTypecheck("1->exists(i | true)");
        assertThat(wrongBody.diagnostics()).anySatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("INVALID_ITERATOR_BODY_TYPE");
            assertThat(diagnostic.sourceRange().start().offset()).isEqualTo(19);
            assertThat(diagnostic.sourceRange().end().offset()).isEqualTo(20);
        });
        assertThat(wrongSource.diagnostics()).anySatisfy(diagnostic ->
                assertThat(diagnostic.message()).contains("source must be a Collection"));
    }

    @Test
    void preservesSourceTypeAndChecksPredicateIteratorContracts() {
        assertThat(expressionType("Set{1}->select(true)")).isEqualTo(
                OclType.collectionOf(CollectionKind.SET, OclType.INTEGER));
        assertThat(expressionType("Set{1}->select(i | i > 0)")).isEqualTo(
                OclType.collectionOf(CollectionKind.SET, OclType.INTEGER));
        assertThat(expressionType("Bag{1}->reject(i | false)")).isEqualTo(
                OclType.collectionOf(CollectionKind.BAG, OclType.INTEGER));
        assertThat(expressionType("Sequence{1}->select(i | true)")).isEqualTo(
                OclType.collectionOf(CollectionKind.SEQUENCE, OclType.INTEGER));
        assertThat(expressionType("OrderedSet{1}->reject(i | false)")).isEqualTo(
                OclType.collectionOf(CollectionKind.ORDERED_SET, OclType.INTEGER));

        var wrongBody = expressionTypecheck("Set{1}->select(i | i)");
        var wrongArity = expressionTypecheck("Set{1}->reject(left, right | left = right)");
        assertThat(wrongBody.diagnostics()).anySatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("INVALID_ITERATOR_BODY_TYPE");
            assertThat(diagnostic.sourceRange().start().offset()).isEqualTo(19);
        });
        assertThat(wrongArity.diagnostics()).anySatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("INVALID_ITERATOR_ARITY");
            assertThat(diagnostic.actual()).isEqualTo("2");
        });
    }

    @Test
    void derivesCollectKindsAndFlattenedBodyTypesIncludingNavigationShorthand() {
        assertThat(expressionType("Set{1}->collect(1)")).isEqualTo(
                OclType.collectionOf(CollectionKind.BAG, OclType.INTEGER));
        assertThat(expressionType("Set{1}->collect(i | i + 1)")).isEqualTo(
                OclType.collectionOf(CollectionKind.BAG, OclType.INTEGER));
        assertThat(expressionType("Bag{1}->collectNested(i | Sequence{i})")).isEqualTo(
                OclType.collectionOf(CollectionKind.BAG,
                        OclType.collectionOf(CollectionKind.SEQUENCE, OclType.INTEGER)));
        assertThat(expressionType("Sequence{1}->collect(i | Set{i})")).isEqualTo(
                OclType.collectionOf(CollectionKind.SEQUENCE, OclType.INTEGER));
        assertThat(expressionType("OrderedSet{1}->collectNested(i | i)")).isEqualTo(
                OclType.collectionOf(CollectionKind.SEQUENCE, OclType.INTEGER));
        assertThat(expressionType("self.borrowedBooks.title")).isEqualTo(
                OclType.collectionOf(CollectionKind.BAG, OclType.STRING));
        assertThat(expressionType("Set{'a'}.toUpperCase()")).isEqualTo(
                OclType.collectionOf(CollectionKind.BAG, OclType.STRING));
        assertThat(expressionType("Sequence{'a'}.toUpperCase()")).isEqualTo(
                OclType.collectionOf(CollectionKind.SEQUENCE, OclType.STRING));
        assertThat(expressionType("Sequence{'a', 'b'}.size()")).isEqualTo(
                OclType.collectionOf(CollectionKind.SEQUENCE, OclType.INTEGER));
        assertThat(expressionTypecheck("Sequence{'a'}->toUpperCase()").success()).isFalse();

        var wrongArity = expressionTypecheck("Set{1}->collect(left, right | left + right)");
        assertThat(wrongArity.diagnostics()).anySatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("INVALID_ITERATOR_ARITY");
            assertThat(diagnostic.actual()).isEqualTo("2");
        });
    }

    @Test
    void typechecksAdvancedQueryIteratorsAndSortedResultKinds() {
        assertThat(expressionType("Set{1}->any(i | i > 0)")).isEqualTo(OclType.INTEGER);
        assertThat(expressionType("Set{1}->one(i | i > 0)")).isEqualTo(OclType.BOOLEAN);
        assertThat(expressionType("Set{1}->isUnique(i | i)")).isEqualTo(OclType.BOOLEAN);
        assertThat(expressionType("Set{Tuple{one=1, two='a'}}->isUnique(Tuple{key=one, value=two})"))
                .isEqualTo(OclType.BOOLEAN);
        assertThat(expressionType("Set{1}->sortedBy(i | i)")).isEqualTo(
                OclType.collectionOf(CollectionKind.ORDERED_SET, OclType.INTEGER));
        assertThat(expressionType("Bag{1}->sortedBy(i | i)")).isEqualTo(
                OclType.collectionOf(CollectionKind.SEQUENCE, OclType.INTEGER));
        assertThat(expressionType("Sequence{1}->sortedBy(i | i)")).isEqualTo(
                OclType.collectionOf(CollectionKind.SEQUENCE, OclType.INTEGER));
        assertThat(expressionType("OrderedSet{1}->sortedBy(i | i)")).isEqualTo(
                OclType.collectionOf(CollectionKind.ORDERED_SET, OclType.INTEGER));
        assertThat(expressionType("Set{'b'}->sortedBy(s | s)")).isEqualTo(
                OclType.collectionOf(CollectionKind.ORDERED_SET, OclType.STRING));

        var wrongPredicate = expressionTypecheck("Set{1}->any(i | i)");
        var wrongSortKey = expressionTypecheck("Set{true}->sortedBy(i | i)");
        var cartesianOne = expressionTypecheck("Set{1}->one(left, right | left = right)");
        assertThat(wrongPredicate.diagnostics()).anyMatch(d -> d.code().equals("INVALID_ITERATOR_BODY_TYPE"));
        assertThat(wrongSortKey.diagnostics()).anySatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("NON_COMPARABLE_SORT_KEY");
            assertThat(diagnostic.actual()).isEqualTo("Boolean");
        });
        assertThat(cartesianOne.success()).isTrue();
        assertThat(cartesianOne.resultType()).isEqualTo(OclType.BOOLEAN);
    }

    @Test
    void reportsIteratorDeclarationAndScopeErrorsAtTheirRanges() {
        var unknown = expressionTypecheck("Set{1}->forAll(i | x > 0)");
        var incompatible = expressionTypecheck("Set{1}->forAll(i : String | i = 'x')");
        var duplicate = expressionTypecheck("Set{1}->forAll(i, i | i > 0)");

        assertThat(unknown.diagnostics()).anySatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("UNKNOWN_VARIABLE");
            assertThat(diagnostic.sourceRange().start().offset()).isEqualTo(19);
        });
        assertThat(incompatible.diagnostics()).anySatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("TYPE_ERROR");
            assertThat(diagnostic.sourceRange().start().offset()).isEqualTo(19);
        });
        assertThat(duplicate.diagnostics()).anySatisfy(diagnostic ->
                assertThat(diagnostic.code()).isEqualTo("DUPLICATE_ITERATOR_VARIABLE"));
    }

    @Test
    void typechecksCartesianIteratorBindingsAndNestedShadowing() {
        assertThat(expressionType("Set{1, 2}->forAll(left, right | left + right <= 4)"))
                .isEqualTo(OclType.BOOLEAN);
        assertThat(expressionType("Set{1, 2}->one(left, right | left = 1 and right = 2)"))
                .isEqualTo(OclType.BOOLEAN);
        assertThat(expressionType("Sequence{1, 2}->forAll(i | Sequence{2}->exists(i | i = 2))"))
                .isEqualTo(OclType.BOOLEAN);
    }

    @Test
    void typechecksIterateAccumulatorAndClosureResultKinds() {
        assertThat(expressionType("Sequence{1, 2}->iterate(i; acc : Integer = 0 | acc + i)"))
                .isEqualTo(OclType.INTEGER);
        assertThat(expressionType("Sequence{1}->iterate(i; acc : Sequence(Integer) = Sequence{} | acc->including(i))"))
                .isEqualTo(OclType.collectionOf(CollectionKind.SEQUENCE, OclType.INTEGER));
        assertThat(expressionType("Set{1}->closure(i | Set{i})"))
                .isEqualTo(OclType.collectionOf(CollectionKind.SET, OclType.INTEGER));
        assertThat(expressionType("Sequence{1}->closure(i | i)"))
                .isEqualTo(OclType.collectionOf(CollectionKind.ORDERED_SET, OclType.INTEGER));

        var invalidInitializer = expressionTypecheck("Set{1}->iterate(i; acc : Integer = 'x' | acc + i)");
        var invalidBody = expressionTypecheck("Set{1}->iterate(i; acc : Integer = 0 | true)");
        var invalidClosure = expressionTypecheck("Set{1}->closure(i | 'x')");
        assertThat(invalidInitializer.diagnostics()).anyMatch(d -> d.code().equals("INVALID_ACCUMULATOR_TYPE"));
        assertThat(invalidBody.diagnostics()).anyMatch(d -> d.code().equals("INVALID_ACCUMULATOR_TYPE"));
        assertThat(invalidClosure.diagnostics()).anyMatch(d -> d.code().equals("INVALID_ITERATOR_BODY_TYPE"));
    }

    @Test
    void typechecksTuplePartsTypeOperationsAndRemainingStringSignatures() {
        var tuple = expressionTypecheck("Tuple{answer = 42, title = 'USE'}.answer");
        var typeTest = expressionTypecheck("self.oclIsKindOf(User)");
        var cast = expressionTypecheck("self.oclAsType(User)");
        var strings = expressionTypecheck("'Use'.toUpperCase().indexOf('S')");

        assertThat(tuple.success()).isTrue();
        assertThat(tuple.resultType()).isEqualTo(OclType.INTEGER);
        assertThat(typeTest.resultType()).isEqualTo(OclType.BOOLEAN);
        assertThat(cast.resultType().displayName()).isEqualTo("User");
        assertThat(strings.resultType()).isEqualTo(OclType.INTEGER);
    }

    @Test
    void resolvesSpecialOclTypesAndBooleanStringConversion() {
        assertThat(expressionType("42.oclIsKindOf(OclVoid)")).isEqualTo(OclType.BOOLEAN);
        assertThat(expressionType("invalid.oclIsKindOf(OclInvalid)")).isEqualTo(OclType.BOOLEAN);
        assertThat(expressionType("true.toString()")).isEqualTo(OclType.STRING);
    }

    @Test
    void typesCollectionLiteralCommonTypesRangesAndAbstractQueries() {
        assertThat(expressionType("Set{}")).isEqualTo(OclType.collectionOf(CollectionKind.SET, OclType.VOID));
        assertThat(expressionType("Set{1, 2.5}")).isEqualTo(OclType.collectionOf(CollectionKind.SET, OclType.REAL));
        assertThat(expressionType("Sequence{1, 3..5}")).isEqualTo(
                OclType.collectionOf(CollectionKind.SEQUENCE, OclType.INTEGER));

        for (String literal : List.of("Set{1,2}", "Bag{1,2}", "Sequence{1,2}", "OrderedSet{1,2}")) {
            assertThat(expressionType(literal + "->max()")).isEqualTo(OclType.INTEGER);
            assertThat(expressionType(literal + "->min()")).isEqualTo(OclType.INTEGER);
            assertThat(expressionType(literal + "->sum()")).isEqualTo(OclType.INTEGER);
            OclType product = expressionType(literal + "->product(Set{'a'})");
            assertThat(product.collectionKind()).isEqualTo(CollectionKind.SET);
            assertThat(product.elementType().kind()).isEqualTo(OclType.Kind.TUPLE);
            assertThat(product.elementType().tupleParts()).containsEntry("first", OclType.INTEGER)
                    .containsEntry("second", OclType.STRING);
        }
        assertThat(expressionType("Set{}->min()")).isEqualTo(OclType.VOID);
        assertThat(expressionType("Sequence{}->max()")).isEqualTo(OclType.VOID);
        assertThat(expressionTypecheck("Set{1.5..2.5}").success()).isFalse();
        assertThat(expressionTypecheck("Set{'a'}->sum()").success()).isFalse();
        assertThat(expressionTypecheck("Set{1}->product(2)").success()).isFalse();
        assertThat(expressionType("Set{self}->selectByKind(User)")).isEqualTo(
                OclType.collectionOf(CollectionKind.SET, OclType.classType(
                        libraryModel.findClass(USER_CLASS_ID).orElseThrow(), libraryModel)));
        assertThat(expressionType("Sequence{self}->selectByType(User)").collectionKind())
                .isEqualTo(CollectionKind.SEQUENCE);
        assertThat(expressionTypecheck("1->selectByKind(User)").success()).isFalse();
    }

    @Test
    void resolvesCompleteConcreteCollectionSignatureMatrix() {
        assertThat(expressionType("Set{1,2} - Set{2.0}")).isEqualTo(
                OclType.collectionOf(CollectionKind.SET, OclType.REAL));
        assertThat(expressionType("Set{1}->symmetricDifference(Set{2.0})")).isEqualTo(
                OclType.collectionOf(CollectionKind.SET, OclType.REAL));

        for (String source : List.of("Sequence{1,2}", "OrderedSet{1,2}")) {
            OclType sourceType = expressionType(source);
            assertThat(expressionType(source + "->append(3)")).isEqualTo(sourceType);
            assertThat(expressionType(source + "->prepend(3)")).isEqualTo(sourceType);
            assertThat(expressionType(source + "->insertAt(2, 3)")).isEqualTo(sourceType);
            assertThat(expressionType(source + "->at(1)")).isEqualTo(OclType.INTEGER);
            assertThat(expressionType(source + "->indexOf(2)")).isEqualTo(OclType.INTEGER);
            assertThat(expressionType(source + "->first()")).isEqualTo(OclType.INTEGER);
            assertThat(expressionType(source + "->last()")).isEqualTo(OclType.INTEGER);
            assertThat(expressionType(source + "->reverse()")).isEqualTo(sourceType);
        }
        assertThat(expressionType("Sequence{1,2,3}->subSequence(1,2)")).isEqualTo(
                OclType.collectionOf(CollectionKind.SEQUENCE, OclType.INTEGER));
        assertThat(expressionType("OrderedSet{1,2,3}->subOrderedSet(1,2)")).isEqualTo(
                OclType.collectionOf(CollectionKind.ORDERED_SET, OclType.INTEGER));

        assertThat(expressionTypecheck("Set{1}->append(2)").success()).isFalse();
        assertThat(expressionTypecheck("Bag{1}->at(1)").success()).isFalse();
        assertThat(expressionTypecheck("Sequence{1}->subOrderedSet(1,1)").success()).isFalse();
        assertThat(expressionTypecheck("OrderedSet{1}->subSequence(1,1)").success()).isFalse();
        assertThat(expressionTypecheck("Bag{1}->symmetricDifference(Set{1})").success()).isFalse();
        assertThat(expressionTypecheck("Set{1}->symmetricDifference(Bag{1})").success()).isFalse();
        assertThat(expressionTypecheck("Set{1} - Bag{1}").success()).isFalse();
    }

    private OclType expressionType(String expression) {
        var result = expressionTypecheck(expression);
        assertThat(result.success()).isTrue();
        return result.resultType();
    }

    private de.useweb.backend.ocl.typecheck.OclTypecheckResult expressionTypecheck(String expression) {
        var parseResult = parser.parse(expression);
        assertThat(parseResult.success()).isTrue();
        return typeChecker.checkExpression(
                new TypeEnvironment(libraryModel, libraryModel.findClass(USER_CLASS_ID).orElseThrow()), parseResult.ast());
    }

    private de.useweb.backend.ocl.typecheck.OclTypecheckResult typecheck(String expression) {
        var parseResult = parser.parse(expression);
        assertThat(parseResult.success()).isTrue();
        return typeChecker.checkInvariant(libraryModel, USER_CLASS_ID, parseResult.ast());
    }

    private static UmlModel libraryModel() {
        UmlClass user = new UmlClass(
                USER_CLASS_ID,
                "User",
                List.of(
                        new UmlAttribute(new UmlAttributeId("attr-user-name"), "name", UmlType.STRING),
                        new UmlAttribute(new UmlAttributeId("attr-user-books"), "books", UmlType.INTEGER),
                        new UmlAttribute(new UmlAttributeId("attr-user-active"), "active", UmlType.BOOLEAN)),
                List.of());
        UmlClass book = new UmlClass(
                BOOK_CLASS_ID,
                "Book",
                List.of(new UmlAttribute(new UmlAttributeId("attr-book-title"), "title", UmlType.STRING)),
                List.of());
        UmlAssociation borrows = new UmlAssociation(
                new UmlAssociationId("assoc-borrows"),
                "Borrows",
                List.of(
                        new UmlAssociationEnd(
                                new UmlAssociationEndId("assoc-borrows-user"),
                                USER_CLASS_ID,
                                "borrower",
                                Multiplicity.exactlyOne(),
                                true),
                        new UmlAssociationEnd(
                                new UmlAssociationEndId("assoc-borrows-book"),
                                BOOK_CLASS_ID,
                                "borrowedBooks",
                                new Multiplicity(0, 5, false, "0..5"),
                                true)));
        return new UmlModel(
                new UmlModelId("model-library"),
                "Library",
                List.of(user, book),
                List.of(borrows),
                List.of());
    }
}
