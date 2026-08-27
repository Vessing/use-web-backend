package de.useweb.backend.ocl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import de.useweb.backend.domain.snapshot.ObjectInstance;
import de.useweb.backend.domain.snapshot.ObjectInstanceId;
import de.useweb.backend.domain.snapshot.ObjectLink;
import de.useweb.backend.domain.snapshot.ObjectLinkEnd;
import de.useweb.backend.domain.snapshot.ObjectLinkId;
import de.useweb.backend.domain.snapshot.ObjectModel;
import de.useweb.backend.domain.snapshot.ObjectModelId;
import de.useweb.backend.domain.snapshot.Slot;
import de.useweb.backend.domain.snapshot.SlotId;
import de.useweb.backend.domain.snapshot.SlotValue;
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
import de.useweb.backend.ocl.evaluation.EvaluationContext;
import de.useweb.backend.ocl.evaluation.OclEvaluator;
import de.useweb.backend.ocl.parser.OclParser;
import de.useweb.backend.ocl.typecheck.OclTypeChecker;
import de.useweb.backend.ocl.value.BooleanValue;
import de.useweb.backend.ocl.collection.CollectionKind;
import de.useweb.backend.ocl.value.BagValue;
import de.useweb.backend.ocl.value.IntegerValue;
import de.useweb.backend.ocl.value.OclInvalidValue;
import de.useweb.backend.ocl.value.OclValue;
import de.useweb.backend.ocl.value.OclVoidValue;
import de.useweb.backend.ocl.value.OrderedSetValue;
import de.useweb.backend.ocl.value.SequenceValue;
import de.useweb.backend.ocl.value.SetValue;
import de.useweb.backend.ocl.typecheck.TypeEnvironment;

class OclEvaluatorTest {

    private static final UmlClassId USER_CLASS_ID = new UmlClassId("class-user");
    private static final UmlClassId BOOK_CLASS_ID = new UmlClassId("class-book");
    private static final UmlAttributeId USER_NAME_ATTRIBUTE_ID = new UmlAttributeId("attr-user-name");
    private static final UmlAttributeId USER_BOOKS_ATTRIBUTE_ID = new UmlAttributeId("attr-user-books");
    private static final UmlAttributeId USER_FAVORITE_BOOK_ATTRIBUTE_ID = new UmlAttributeId("attr-user-favorite-book");
    private static final UmlAttributeId BOOK_AVAILABLE_ATTRIBUTE_ID = new UmlAttributeId("attr-book-available");
    private static final UmlAssociationId BORROWS_ASSOCIATION_ID = new UmlAssociationId("assoc-borrows");
    private static final UmlAssociationEndId BORROWS_USER_END_ID = new UmlAssociationEndId("assoc-borrows-user");
    private static final UmlAssociationEndId BORROWS_BOOK_END_ID = new UmlAssociationEndId("assoc-borrows-book");

    private final OclParser parser = new OclParser();
    private final OclTypeChecker typeChecker = new OclTypeChecker();
    private final OclEvaluator evaluator = new OclEvaluator();
    private final UmlModel umlModel = libraryModel();

    @Test
    void preservesPromotedReferenceCollectionIsEmptySemantics() {
        // Reference: USE-SHELL-E33829A9C98C-L000613, shell/t001.in:613.
        ObjectModel snapshot = librarySnapshot(1, 0, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("Set{1,2,3}->isEmpty()", snapshot, alice).value())
                .isEqualTo(new BooleanValue(false));
    }

    @Test
    void preservesPromotedReferenceForAllSemantics() {
        // Reference: USE-SHELL-E33829A9C98C-L001121, shell/t001.in:1121.
        ObjectModel snapshot = librarySnapshot(1, 0, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("Set{1,2,3,4,5,6}->forAll(e | e > 0)", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
    }

    @Test
    void evaluatesAllInstancesFromOnlyTheCurrentSnapshotAndExactClass() {
        ObjectModel snapshot = librarySnapshot(1, 2, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("Book.allInstances()->size() = 2", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("User.allInstances()->size() = 1", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Book.allInstances()->exists(book | book.available)", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));

        ObjectModel emptyBooks = new ObjectModel(new ObjectModelId("snapshot-no-books"), "No books",
                List.of(alice), List.of());
        assertThat(evaluate("Book.allInstances()->isEmpty()", emptyBooks, alice).value())
                .isEqualTo(new BooleanValue(true));

        ObjectModel otherSnapshot = librarySnapshot(1, 4, true);
        assertThat(evaluate("Book.allInstances()->size() = 2", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(otherSnapshot.objects()).hasSize(5);
    }

    @Test
    void evaluatesLetInitializerOnceInOuterScopeAndBodyInChildScope() {
        ObjectModel snapshot = librarySnapshot(3, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluateWithoutTypecheck("let limit = 5 in limit + 1", snapshot, alice).value())
                .isEqualTo(new IntegerValue(6));
        assertThat(evaluateWithoutTypecheck("let limit = 5 in let limit = 3 in limit", snapshot, alice).value())
                .isEqualTo(new IntegerValue(3));
        assertThat(evaluateWithoutTypecheck("let a = 2, b = a + 1 in a + b", snapshot, alice).value())
                .isEqualTo(new IntegerValue(5));
        assertThat(evaluate("let user = self in user.books = 3", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("let missing = null in missing = null", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("let broken = invalid in true", snapshot, alice).value())
                .isEqualTo(OclInvalidValue.INSTANCE);
    }

    @Test
    void evaluatesOnlyTheSelectedIfBranchAndPropagatesUndefinedConditions() {
        ObjectModel snapshot = librarySnapshot(1, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("if true then true else 1 / 0 = 0 endif", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("if false then 1 / 0 = 0 else true endif", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("if true then if false then false else true endif else false endif", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("if null then true else false endif", snapshot, alice).value())
                .isEqualTo(OclInvalidValue.INSTANCE);
        assertThat(evaluate("if invalid then true else false endif", snapshot, alice).value())
                .isEqualTo(OclInvalidValue.INSTANCE);

        var selectedFailure = evaluate("if true then 1 / 0 = 0 else true endif", snapshot, alice);
        assertThat(selectedFailure.value()).isEqualTo(OclInvalidValue.INSTANCE);
    }

    @Test
    void evaluatesIntegerAttributeComparisonAgainstSnapshot() {
        ObjectModel snapshot = librarySnapshot(6, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        var result = evaluate("self.books <= 5", snapshot, alice);

        assertThat(result.success()).isTrue();
        assertThat(result.value()).isEqualTo(new BooleanValue(false));
    }

    @Test
    void resolvesObjectValuedSlotsByStableObjectIdentity() {
        ObjectModel snapshot = librarySnapshot(1, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("self.favoriteBook.available", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
    }

    @Test
    void evaluatesStringNotEqualsExpressionAgainstSnapshot() {
        ObjectModel snapshot = librarySnapshot(3, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        var result = evaluate("self.name <> ''", snapshot, alice);

        assertThat(result.success()).isTrue();
        assertThat(result.value()).isEqualTo(new BooleanValue(true));
    }

    @Test
    void evaluatesSpecialTypeTestsAndBooleanStringConversion() {
        ObjectModel snapshot = librarySnapshot(1, 0, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("42.oclIsKindOf(OclVoid) = false", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("true.toString() = 'true'", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
    }

    @Test
    void evaluatesBooleanAttributeEqualityAgainstSnapshot() {
        ObjectModel snapshot = librarySnapshot(3, 1, false);
        ObjectInstance book = object(snapshot, "obj-book-1");

        var result = evaluate("self.available = false", snapshot, book);

        assertThat(result.success()).isTrue();
        assertThat(result.value()).isEqualTo(new BooleanValue(true));
    }

    @Test
    void evaluatesCollectionNavigationOverObjectLinks() {
        ObjectModel snapshot = librarySnapshot(6, 6, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        var result = evaluate("self.borrowedBooks->size() <= 5", snapshot, alice);

        assertThat(result.success()).isTrue();
        assertThat(result.value()).isEqualTo(new BooleanValue(false));
    }

    @Test
    void derivesAssociationNavigationCollectionKindFromOrderedAndUniqueMetadata() {
        assertThat(navigationValue(false, true)).isInstanceOf(SetValue.class);
        assertThat(navigationValue(false, false)).isInstanceOf(BagValue.class);
        assertThat(navigationValue(true, false)).isInstanceOf(SequenceValue.class);
        assertThat(navigationValue(true, true)).isInstanceOf(OrderedSetValue.class);
    }

    private OclValue navigationValue(boolean ordered, boolean unique) {
        UmlClass sourceClass = new UmlClass(new UmlClassId("class-source"), "Source", List.of(), List.of());
        UmlClass targetClass = new UmlClass(new UmlClassId("class-target"), "Target", List.of(), List.of());
        UmlAssociationEndId sourceEndId = new UmlAssociationEndId("end-source");
        UmlAssociationEndId targetEndId = new UmlAssociationEndId("end-target");
        UmlAssociationId associationId = new UmlAssociationId("association-navigation-kind");
        UmlAssociation association = new UmlAssociation(associationId, "NavigationKind", List.of(
                new UmlAssociationEnd(sourceEndId, sourceClass.id(), "source", Multiplicity.exactlyOne(), true),
                new UmlAssociationEnd(targetEndId, targetClass.id(), "targets", Multiplicity.zeroToMany(),
                        true, ordered, unique, false, false, List.of(), List.of())));
        UmlModel model = new UmlModel(new UmlModelId("navigation-kind-model"), "NavigationKind",
                List.of(sourceClass, targetClass), List.of(association), List.of());
        ObjectInstance source = new ObjectInstance(new ObjectInstanceId("source"), "source", sourceClass.id(), List.of());
        ObjectInstance target = new ObjectInstance(new ObjectInstanceId("target"), "target", targetClass.id(), List.of());
        ObjectModel snapshot = new ObjectModel(new ObjectModelId("navigation-kind-snapshot"), "NavigationKind",
                List.of(source, target), List.of(new ObjectLink(new ObjectLinkId("link"), associationId, List.of(
                        new ObjectLinkEnd(sourceEndId, source.id()), new ObjectLinkEnd(targetEndId, target.id())))));
        var parsed = parser.parse("self.targets");
        var checked = typeChecker.checkExpression(new TypeEnvironment(model, sourceClass), parsed.ast());
        assertThat(checked.success()).isTrue();
        CollectionKind expectedKind = ordered
                ? (unique ? CollectionKind.ORDERED_SET : CollectionKind.SEQUENCE)
                : (unique ? CollectionKind.SET : CollectionKind.BAG);
        assertThat(checked.resultType().collectionKind()).isEqualTo(expectedKind);
        return evaluator.evaluate(parsed.ast(), new EvaluationContext(model, snapshot, source)).value();
    }

    @Test
    void reportsEvaluationErrorForMissingSlot() {
        ObjectInstance aliceWithoutBooks = new ObjectInstance(
                new ObjectInstanceId("obj-alice"),
                "alice",
                USER_CLASS_ID,
                List.of(new Slot(new SlotId("slot-alice-name"), USER_NAME_ATTRIBUTE_ID, SlotValue.ofString("Alice"))));
        ObjectModel snapshot = new ObjectModel(new ObjectModelId("snapshot-missing-slot"), "Missing Slot", List.of(aliceWithoutBooks), List.of());

        var result = evaluate("self.books <= 5", snapshot, aliceWithoutBooks);

        assertThat(result.success()).isFalse();
        assertThat(result.diagnostics())
                .anySatisfy(diagnostic -> {
                    assertThat(diagnostic.code()).isEqualTo("EVALUATION_ERROR");
                    assertThat(diagnostic.message()).contains("books").contains("alice");
                });
    }

    @Test
    void evaluatesNullEqualityWithoutJavaNull() {
        ObjectModel snapshot = librarySnapshot(3, 1, true);

        assertThat(evaluate("null = null", snapshot, object(snapshot, "obj-alice")).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("null = 5", snapshot, object(snapshot, "obj-alice")).value())
                .isEqualTo(new BooleanValue(false));
    }

    @Test
    void propagatesInvalidThroughBooleanTruthTables() {
        ObjectModel snapshot = librarySnapshot(3, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("false and invalid", snapshot, alice).value()).isEqualTo(new BooleanValue(false));
        assertThat(evaluate("invalid and false", snapshot, alice).value()).isEqualTo(new BooleanValue(false));
        assertThat(evaluate("true or invalid", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("invalid or true", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("true and invalid", snapshot, alice).value()).isEqualTo(OclInvalidValue.INSTANCE);
        assertThat(evaluate("invalid and true", snapshot, alice).value()).isEqualTo(OclInvalidValue.INSTANCE);
        assertThat(evaluate("false or invalid", snapshot, alice).value()).isEqualTo(OclInvalidValue.INSTANCE);
        assertThat(evaluate("invalid or false", snapshot, alice).value()).isEqualTo(OclInvalidValue.INSTANCE);
        assertThat(evaluate("invalid and invalid", snapshot, alice).value()).isEqualTo(OclInvalidValue.INSTANCE);
        assertThat(evaluate("invalid or invalid", snapshot, alice).value()).isEqualTo(OclInvalidValue.INSTANCE);
        assertThat(evaluate("not null", snapshot, alice).value()).isEqualTo(OclVoidValue.INSTANCE);
    }

    @Test
    void representsUnsetSlotAsOclVoidAndInvalidComparison() {
        ObjectInstance alice = new ObjectInstance(
                new ObjectInstanceId("obj-alice"),
                "alice",
                USER_CLASS_ID,
                List.of(
                        new Slot(new SlotId("slot-alice-name"), USER_NAME_ATTRIBUTE_ID, SlotValue.ofString("Alice")),
                        new Slot(new SlotId("slot-alice-books"), USER_BOOKS_ATTRIBUTE_ID, new SlotValue(null, UmlType.INTEGER))));
        ObjectModel snapshot = new ObjectModel(new ObjectModelId("snapshot-null-slot"), "Null Slot", List.of(alice), List.of());

        assertThat(evaluate("self.books = null", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("self.books <= 5", snapshot, alice).value()).isEqualTo(OclInvalidValue.INSTANCE);
        assertThat(evaluator.evaluate(parser.parse("null").ast(), new EvaluationContext(umlModel, snapshot, alice)).value())
                .isEqualTo(OclVoidValue.INSTANCE);
    }

    @Test
    void evaluatesArithmeticPrecedenceAndNumericPromotion() {
        ObjectModel snapshot = librarySnapshot(3, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("1 + 2 * 3 = 7", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("5 / 2 = 2.5", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("5 div 2 = 2", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("5 mod 2 = 1", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
    }

    @Test
    void evaluatesImpliesXorAndStandardCalls() {
        ObjectModel snapshot = librarySnapshot(3, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("false implies invalid", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("true xor false", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("'ab'.concat('cd') = 'abcd'", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("3.9.floor = 3", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("self.borrowedBooks->size = 1", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
    }

    @Test
    void preservesCollectionKindOrderAndDuplicateSemantics() {
        ObjectModel snapshot = librarySnapshot(3, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("Set{1, 1} = Set{1}", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Bag{1, 1} = Bag{1}", snapshot, alice).value()).isEqualTo(new BooleanValue(false));
        assertThat(evaluate("Sequence{1, 2} = Sequence{2, 1}", snapshot, alice).value()).isEqualTo(new BooleanValue(false));
        assertThat(evaluate("OrderedSet{1, 1, 2} = OrderedSet{1, 2}", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
    }

    @Test
    void evaluatesRangesMixedNumbersAndNestedCollections() {
        ObjectModel snapshot = librarySnapshot(3, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("Sequence{1..3} = Sequence{1, 2, 3}", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{1, 2.0} = Set{2, 1.0}", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{Set{1}, Set{2}} = Set{Set{2}, Set{1}}", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{} = Set{}", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
    }

    @Test
    void evaluatesCollectionBasisOperationsForAllCollectionKindsAndSyntaxProfiles() {
        ObjectModel snapshot = librarySnapshot(3, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("Set{1, 1}->size = 1", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Bag{1, 1}->size() = 2", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{1, 1}->size = 2", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("OrderedSet{1, 1}->size() = 1", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{}->isEmpty", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Bag{1}->isEmpty()", snapshot, alice).value()).isEqualTo(new BooleanValue(false));
        assertThat(evaluate("Sequence{}->notEmpty()", snapshot, alice).value()).isEqualTo(new BooleanValue(false));
        assertThat(evaluate("OrderedSet{1}->notEmpty", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
    }

    @Test
    void evaluatesIteratorBodiesWithTheImplicitIteratorSource() {
        ObjectModel snapshot = librarySnapshot(3, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("Set{1, 2, 3}->select(true) = Set{1, 2, 3}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{1, 2, 3}->collect(1) = Bag{1, 1, 1}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{1, 2, 3}->includes(Set{1, 2, 3}->any(true))", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{3, 1, 2}->sortedBy(1) = Sequence{3, 1, 2}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
    }

    @Test
    void appliesVoidAndInvalidSemanticsToCollectionBasisOperations() {
        ObjectModel snapshot = librarySnapshot(3, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("null->isEmpty()", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("null->notEmpty()", snapshot, alice).value()).isEqualTo(new BooleanValue(false));
        assertThat(evaluate("null->size() = 0", snapshot, alice).value()).isEqualTo(OclInvalidValue.INSTANCE);
        assertThat(evaluate("invalid->isEmpty()", snapshot, alice).value()).isEqualTo(OclInvalidValue.INSTANCE);
        assertThat(evaluate("invalid->notEmpty()", snapshot, alice).value()).isEqualTo(OclInvalidValue.INSTANCE);
        assertThat(evaluate("invalid->size() = 0", snapshot, alice).value()).isEqualTo(OclInvalidValue.INSTANCE);
    }

    @Test
    void evaluatesMembershipQueriesAcrossCollectionKinds() {
        ObjectModel snapshot = librarySnapshot(3, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("Set{1, 2, 3}->includes(2)", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Bag{1, 1}->excludes(2)", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{1, 2}->includesAll(OrderedSet{2, 1})", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("OrderedSet{1, 2}->excludesAll(Bag{3, 3})", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{1}->includesAll(Bag{1, 1})", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{1, 2}->excludesAll(Set{2, 3})", snapshot, alice).value()).isEqualTo(new BooleanValue(false));
    }

    @Test
    void usesOclEqualityAndEmptyCollectionSemanticsForMembershipQueries() {
        ObjectModel snapshot = librarySnapshot(3, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("Set{1}->includes(1.0)", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{Set{1}}->includes(Set{1})", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{null}->includes(null)", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{}->includes(1)", snapshot, alice).value()).isEqualTo(new BooleanValue(false));
        assertThat(evaluate("Set{}->excludes(1)", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{1}->includesAll(Set{})", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{}->excludesAll(Set{})", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
    }

    @Test
    void evaluatesIncludingWithoutChangingCollectionKindSemantics() {
        ObjectModel snapshot = librarySnapshot(3, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("Set{1}->including(1) = Set{1}", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Bag{1}->including(1) = Bag{1, 1}", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{1}->including(2) = Sequence{1, 2}", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("OrderedSet{1}->including(2) = OrderedSet{1, 2}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{}->including(null) = Set{null}", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
    }

    @Test
    void evaluatesExcludingAndCountWithOclEquality() {
        ObjectModel snapshot = librarySnapshot(3, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("Set{1, 2}->excluding(1) = Set{2}", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Bag{1, 1, 2}->excluding(1) = Bag{2}", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{1, 2, 1}->excluding(1) = Sequence{2}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("OrderedSet{1, 2}->excluding(3) = OrderedSet{1, 2}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Bag{1, 1.0, 2}->count(1) = 2", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{}->count(1) = 0", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
    }

    @Test
    void evaluatesUnionForEverySupportedCollectionCombination() {
        ObjectModel snapshot = librarySnapshot(3, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("Set{1, 2}->union(Set{2, 3}) = Set{1, 2, 3}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{1}->union(Bag{1, 2}) = Bag{1, 1, 2}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Bag{1, 1}->union(Set{1, 2}) = Bag{1, 1, 1, 2}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Bag{1}->union(Bag{1, 2}) = Bag{1, 1, 2}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{1, 2}->union(Sequence{2, 3}) = Sequence{1, 2, 2, 3}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("OrderedSet{2, 1}->union(OrderedSet{1, 3}) = OrderedSet{2, 1, 3}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
    }

    @Test
    void evaluatesIntersectionWithKindSpecificMultiplicity() {
        ObjectModel snapshot = librarySnapshot(3, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("Set{1, 2}->intersection(Bag{2, 2}) = Set{2}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{1, 2}->intersection(Set{2, 3}) = Set{2}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Bag{1, 2, 2, 3}->intersection(Bag{2, 2, 2, 3}) = Bag{2, 2, 3}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Bag{1, 2, 2}->intersection(Set{2}) = Set{2}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("OrderedSet{3, 1, 2}->intersection(OrderedSet{2, 3}) = OrderedSet{3, 2}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
    }

    @Test
    void evaluatesRecursiveFlattenAndCollectionConversions() {
        ObjectModel snapshot = librarySnapshot(3, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("Set{Sequence{1, 2}, Set{2, 3}}->flatten() = Set{1, 2, 3}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{Set{1, 2}, Bag{2, 3}}->flatten() = Sequence{1, 2, 2, 3}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        var flattened = evaluator.evaluate(
                parser.parse("OrderedSet{Set{OrderedSet{1}}, Set{2}}->flatten()").ast(),
                new EvaluationContext(umlModel, snapshot, alice));
        assertThat(flattened.value()).isEqualTo(
                new OrderedSetValue(List.of(new IntegerValue(1), new IntegerValue(2))));
        assertThat(evaluate("Bag{1, 1, 2}->asSet() = Set{1, 2}", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{1, 2}->asBag() = Bag{1, 2}", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Bag{1, 1}->asSequence() = Sequence{1, 1}", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{2, 1, 2}->asOrderedSet() = OrderedSet{2, 1}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{}->asBag() = Bag{}", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{1, 2}->flatten() = Sequence{1, 2}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
    }

    @Test
    void resolvesRuntimeVariablesFromImmutableChildContexts() {
        ObjectModel snapshot = librarySnapshot(1, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");
        EvaluationContext root = new EvaluationContext(umlModel, snapshot, alice);
        EvaluationContext child = root.child(Map.of("item", new IntegerValue(7)));
        EvaluationContext shadowed = child.child(Map.of("item", new IntegerValue(9)));

        assertThat(evaluator.evaluate(parser.parse("item").ast(), child).value()).isEqualTo(new IntegerValue(7));
        assertThat(evaluator.evaluate(parser.parse("item").ast(), shadowed).value()).isEqualTo(new IntegerValue(9));
        assertThat(root.findVariable("item")).isEmpty();
        assertThat(child.findVariable("item")).contains(new IntegerValue(7));
    }

    @Test
    void evaluatesForAllAndExistsIncludingEmptyAndMultipleBindings() {
        ObjectModel snapshot = librarySnapshot(1, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("Set{}->forAll(i | true)", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{}->exists(i | true)", snapshot, alice).value()).isEqualTo(new BooleanValue(false));
        assertThat(evaluate("Set{1, 2}->forAll(i | i > 0)", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{1, 2}->exists(i | i = 2)", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{1, 2}->forAll(a, b | a + b >= 2)", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{1, 2}->exists(a, b | a <> b)", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{1, 2}->forAll(i | Set{1, 2}->exists(j | j = i))", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("self.borrowedBooks->forAll(book | book.available)", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
    }

    @Test
    void appliesQuantifierDominanceForInvalidAndNullBodies() {
        ObjectModel snapshot = librarySnapshot(1, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("Sequence{invalid, false}->forAll(x | x)", snapshot, alice).value())
                .isEqualTo(new BooleanValue(false));
        assertThat(evaluate("Sequence{invalid, true}->forAll(x | x)", snapshot, alice).value())
                .isEqualTo(OclInvalidValue.INSTANCE);
        assertThat(evaluate("Sequence{null, true}->forAll(x | x)", snapshot, alice).value())
                .isEqualTo(de.useweb.backend.ocl.value.OclVoidValue.INSTANCE);
        assertThat(evaluate("Sequence{invalid, true}->exists(x | x)", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{invalid, false}->exists(x | x)", snapshot, alice).value())
                .isEqualTo(OclInvalidValue.INSTANCE);
        assertThat(evaluate("Sequence{null, false}->exists(x | x)", snapshot, alice).value())
                .isEqualTo(de.useweb.backend.ocl.value.OclVoidValue.INSTANCE);
        assertThat(evaluate("null->forAll(x | true)", snapshot, alice).value())
                .isEqualTo(OclInvalidValue.INSTANCE);
    }

    @Test
    void rejectsQuantifierEvaluationBeyondBindingBudget() {
        ObjectModel snapshot = librarySnapshot(1, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");
        String values = IntStream.rangeClosed(1, 47).mapToObj(Integer::toString).collect(Collectors.joining(","));
        String expression = "Sequence{" + values + "}->forAll(a, b, c | true)";
        var parseResult = parser.parse(expression);
        assertThat(parseResult.success()).isTrue();
        assertThat(typeChecker.checkInvariant(umlModel, alice.classId(), parseResult.ast()).success()).isTrue();

        var result = evaluator.evaluate(parseResult.ast(), new EvaluationContext(umlModel, snapshot, alice));

        assertThat(result.success()).isFalse();
        assertThat(result.diagnostics()).anySatisfy(diagnostic ->
                assertThat(diagnostic.code()).isEqualTo("ITERATION_LIMIT_EXCEEDED"));
    }

    @Test
    void filtersEveryConcreteCollectionKindWithoutLosingOrderOrDuplicates() {
        ObjectModel snapshot = librarySnapshot(1, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("Set{1, 2, 3}->select(i | i > 1) = Set{2, 3}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Bag{1, 1, 2}->reject(i | i = 2) = Bag{1, 1}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{3, 1, 3, 2}->select(i | i <> 1) = Sequence{3, 3, 2}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("OrderedSet{3, 1, 2}->reject(i | i = 1) = OrderedSet{3, 2}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{}->select(i | true) = Sequence{}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("self.borrowedBooks->select(book | book.available)->size() = 1", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
    }

    @Test
    void propagatesNullFilterPredicatesAndInvalidSourcesInsteadOfTreatingThemAsFalse() {
        ObjectModel snapshot = librarySnapshot(1, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluateWithoutTypecheck("Sequence{false, null}->reject(x | x)", snapshot, alice).value())
                .isEqualTo(OclInvalidValue.INSTANCE);
        assertThat(evaluateWithoutTypecheck("invalid->select(x | true)", snapshot, alice).value())
                .isEqualTo(OclInvalidValue.INSTANCE);
        assertThat(evaluateWithoutTypecheck("null->select(x | true)", snapshot, alice).value())
                .isEqualTo(OclInvalidValue.INSTANCE);
    }

    @Test
    void transformsCollectionsWithCollectAndCollectNestedAccordingToSourceKind() {
        ObjectModel snapshot = librarySnapshot(1, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("Set{1, 2}->collect(i | i * 2) = Bag{2, 4}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Bag{1, 1, 2}->collect(i | i + 1) = Bag{2, 2, 3}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{2, 1}->collect(i | i * 2) = Sequence{4, 2}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("OrderedSet{2, 1}->collect(i | i) = Sequence{2, 1}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{1, 2}->collect(i | Sequence{i, i}) = Bag{1, 1, 2, 2}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{1, 2}->collectNested(i | Sequence{i, i}) = "
                + "Bag{Sequence{1, 1}, Sequence{2, 2}}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{}->collect(i | i) = Sequence{}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
    }

    @Test
    void evaluatesPropertyChainsOnCollectionsAsImplicitCollect() {
        ObjectModel snapshot = librarySnapshot(2, 2, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("self.borrowedBooks.available->size() = 2", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("self.borrowedBooks.available = Bag{true, true}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{'use', 'ocl'}.toUpperCase() = Sequence{'USE', 'OCL'}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{'use'}.toUpperCase() = Bag{'USE'}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{'use', 'ocl'}.size() = Sequence{3, 3}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        var invalidArrowCall = evaluateWithoutTypecheck("Sequence{'use'}->toUpperCase()", snapshot, alice);
        assertThat(invalidArrowCall.success()).isFalse();
        assertThat(invalidArrowCall.diagnostics()).anyMatch(diagnostic ->
                diagnostic.code().equals("CALL_RESOLUTION_ERROR"));
    }

    @Test
    void evaluatesCartesianBindingsShadowingEmptyCollectionsAndFourValuedQuantifiers() {
        ObjectModel snapshot = librarySnapshot(1, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("Set{1, 2}->forAll(left, right | left + right <= 4)", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{1, 2}->exists(left, right | left + right = 4)", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{1, 2}->forAll(i | Sequence{2}->exists(i | i = 2))", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{}->forAll(i | false)", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{}->exists(i | true)", snapshot, alice).value())
                .isEqualTo(new BooleanValue(false));
        assertThat(evaluate("Set{1, 2}->forAll(i | if i = 1 then invalid else false endif)", snapshot, alice).value())
                .isEqualTo(new BooleanValue(false));
        assertThat(evaluate("Set{1, 2}->exists(i | if i = 1 then invalid else true endif)", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{1, 2, 3}->one(i | if i = 1 then invalid else true endif)", snapshot, alice).value())
                .isEqualTo(new BooleanValue(false));
    }

    @Test
    void evaluatesAnyOneAndIsUniqueIncludingEmptyAndUndefinedCases() {
        ObjectModel snapshot = librarySnapshot(1, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluateWithoutTypecheck("Sequence{3, 1, 2}->any(i | i < 3)", snapshot, alice).value())
                .isEqualTo(new IntegerValue(1));
        assertThat(evaluateWithoutTypecheck("Sequence{}->any(i | true)", snapshot, alice).value())
                .isEqualTo(OclInvalidValue.INSTANCE);
        assertThat(evaluate("Sequence{1, 2, 3}->one(i | i = 2)", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{1, 2, 2}->one(i | i = 2)", snapshot, alice).value())
                .isEqualTo(new BooleanValue(false));
        assertThat(evaluate("Sequence{}->one(i | true)", snapshot, alice).value())
                .isEqualTo(new BooleanValue(false));
        assertThat(evaluate("Set{1, 2}->one(left, right | left = 1 and right = 2)", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{1, 2}->one(left, right | left = right)", snapshot, alice).value())
                .isEqualTo(new BooleanValue(false));
        assertThat(evaluate("Sequence{1, 2, 3}->isUnique(i | i)", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{1, 2, 1}->isUnique(i | i)", snapshot, alice).value())
                .isEqualTo(new BooleanValue(false));
        assertThat(evaluate("Set{Tuple{one=10, two='ten', discriminator='a'}, "
                        + "Tuple{one=10, two='ten', discriminator='b'}}"
                        + "->isUnique(Tuple{key=one, value=two})", snapshot, alice).value())
                .isEqualTo(new BooleanValue(false));
        assertThat(evaluate("Sequence{null, null}->isUnique(i | i)", snapshot, alice).value())
                .isEqualTo(new BooleanValue(false));
        assertThat(evaluate("Sequence{1, invalid}->isUnique(i | i)", snapshot, alice).value())
                .isEqualTo(OclInvalidValue.INSTANCE);
    }

    @Test
    void sortsEveryConcreteCollectionKindByNumericAndStringKeys() {
        ObjectModel snapshot = librarySnapshot(1, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("Set{3, 1, 2}->sortedBy(i | i) = OrderedSet{1, 2, 3}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Bag{3, 1, 1, 2}->sortedBy(i | i) = Sequence{1, 1, 2, 3}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{3, 1, 2}->sortedBy(i | i) = Sequence{1, 2, 3}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("OrderedSet{3, 1, 2}->sortedBy(i | i) = OrderedSet{1, 2, 3}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{'c', 'a', 'b'}->sortedBy(s | s) = OrderedSet{'a', 'b', 'c'}", snapshot,
                alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("let c : Collection(Integer) = Set{3, 1, 2} in "
                + "c->sortedBy(i | i) = Sequence{1, 2, 3}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{3, 1, 2}->sortedBy(i | 1) = Sequence{3, 1, 2}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{4, 2, 3, 1}->sortedBy(1)->includesAll(Set{1, 2, 3, 4})", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
    }

    @Test
    void evaluatesIterateAndClosureWithEmptyNestedAndCyclicInputs() {
        ObjectModel snapshot = librarySnapshot(1, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("Sequence{1, 2, 3}->iterate(i; acc : Integer = 0 | acc + i) = 6", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{1, 2, 3}->iterate(i, j; acc : Integer = 0 | acc + i * j) = 36",
                snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluateWithoutTypecheck("Sequence{}->iterate(i; acc : Integer = 7 | acc + i)", snapshot, alice).value())
                .isEqualTo(new IntegerValue(7));
        assertThat(evaluate("Sequence{1, 2}->iterate(i; acc : Integer = 0 | acc + "
                + "Sequence{i}->iterate(j; inner : Integer = 0 | inner + j)) = 3", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{1, 1}->closure(i | Set{i}) = Set{1}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{2, 1}->closure(i | i) = OrderedSet{2, 1}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
    }

    @Test
    void rejectsClosureSourcesBeyondTheIterationBudget() {
        ObjectModel snapshot = librarySnapshot(1, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        var result = evaluateWithoutTypecheck("Sequence{1..100001}->closure(i | i)", snapshot, alice);

        assertThat(result.success()).isFalse();
        assertThat(result.diagnostics()).anyMatch(diagnostic -> diagnostic.code().equals("ITERATION_LIMIT_EXCEEDED"));
    }

    @Test
    void keepsClosureOrderDeterministicAcrossBranchesAndCycles() {
        ObjectModel snapshot = librarySnapshot(1, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("Sequence{1}->closure(i | if i = 1 then Sequence{2, 3} else Sequence{} endif) "
                + "= OrderedSet{1, 2, 3}", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{1}->closure(i | if i = 1 then Sequence{2} else Sequence{1} endif) "
                + "= OrderedSet{1, 2}", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
    }

    @Test
    void rejectsIterateSourcesBeyondTheBindingBudget() {
        ObjectModel snapshot = librarySnapshot(1, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        var result = evaluateWithoutTypecheck(
                "Sequence{1..100001}->iterate(i; acc : Integer = 0 | acc + i)", snapshot, alice);

        assertThat(result.success()).isFalse();
        assertThat(result.diagnostics()).anyMatch(diagnostic -> diagnostic.code().equals("ITERATION_LIMIT_EXCEEDED"));
    }

    @Test
    void reportsEvaluationDepthAndTimeBudgetsAsDiagnostics() {
        ObjectModel snapshot = librarySnapshot(1, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");
        EvaluationContext context = new EvaluationContext(umlModel, snapshot, alice);
        var nested = new OclParser().parse("not not true").ast();

        var depthResult = new OclEvaluator(2, Long.MAX_VALUE, () -> 0L).evaluate(nested, context);
        java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong();
        var timeResult = new OclEvaluator(256, 10L, () -> clock.getAndAdd(11L)).evaluate(nested, context);

        assertThat(depthResult.success()).isFalse();
        assertThat(depthResult.diagnostics()).anyMatch(diagnostic ->
                diagnostic.code().equals("EVALUATION_DEPTH_LIMIT_EXCEEDED"));
        assertThat(timeResult.success()).isFalse();
        assertThat(timeResult.diagnostics()).anyMatch(diagnostic ->
                diagnostic.code().equals("EVALUATION_TIME_LIMIT_EXCEEDED"));
    }

    @Test
    void rejectsCollectionRangesAndProductsBeyondTheResultBudget() {
        ObjectModel snapshot = librarySnapshot(1, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        var rangeResult = evaluateWithoutTypecheck("Sequence{1..1000001}", snapshot, alice);
        var productResult = evaluateWithoutTypecheck(
                "Sequence{1..1001}->product(Sequence{1..1000})", snapshot, alice);

        assertThat(rangeResult.success()).isFalse();
        assertThat(rangeResult.diagnostics()).anyMatch(diagnostic ->
                diagnostic.code().equals("RESULT_LIMIT_EXCEEDED"));
        assertThat(productResult.success()).isFalse();
        assertThat(productResult.diagnostics()).anyMatch(diagnostic ->
                diagnostic.code().equals("RESULT_LIMIT_EXCEEDED"));
    }

    @Test
    void evaluatesTuplesTypeOperationsAndLocaleIndependentStringOperations() {
        ObjectModel snapshot = librarySnapshot(2, 0, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("Tuple{answer = 42, title = 'USE'}.answer = 42", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("self.oclIsTypeOf(User) and self.oclIsKindOf(User)", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{1}->oclIsKindOf(Set(OclAny))", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{1}->oclIsTypeOf(Set(OclAny))", snapshot, alice).value())
                .isEqualTo(new BooleanValue(false));
        assertThat(evaluate("Set{1}->oclIsKindOf(Set(String))", snapshot, alice).value())
                .isEqualTo(new BooleanValue(false));
        assertThat(evaluate("let s : Collection(Integer) = Set{1} in s->oclAsType(Set(Integer)) = Set{1}",
                snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("self.oclAsType(User).name = 'Alice'", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("'Use'.toUpperCase() = 'USE' and 'USE'.toLowerCase() = 'use'", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("'USE'.indexOf('S') = 2 and '42'.toInteger() = 42 and '3.5'.toReal() = 3.5", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluateWithoutTypecheck("'not-a-number'.toInteger()", snapshot, alice).value())
                .isEqualTo(OclInvalidValue.INSTANCE);
    }

    @Test
    void appliesFourValuedBooleanSemanticsWithoutLeakingJavaNull() {
        ObjectModel snapshot = librarySnapshot(1, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("not null", snapshot, alice).value()).isEqualTo(OclVoidValue.INSTANCE);
        assertThat(evaluate("null and true", snapshot, alice).value()).isEqualTo(OclVoidValue.INSTANCE);
        assertThat(evaluate("null or false", snapshot, alice).value()).isEqualTo(OclVoidValue.INSTANCE);
        assertThat(evaluate("null xor true", snapshot, alice).value()).isEqualTo(OclVoidValue.INSTANCE);
        assertThat(evaluate("null implies false", snapshot, alice).value()).isEqualTo(OclVoidValue.INSTANCE);
        assertThat(evaluate("invalid and false", snapshot, alice).value()).isEqualTo(new BooleanValue(false));
        assertThat(evaluate("invalid or true", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("invalid implies true", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("null = null and null <> true", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("invalid = invalid", snapshot, alice).value()).isEqualTo(OclInvalidValue.INSTANCE);
        assertThat(evaluate("(let values : Sequence(Integer) = null in values->reverse()).oclIsInvalid()",
                snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("(let value : Integer = null in value.toString()).oclIsInvalid()",
                snapshot, alice).value()).isEqualTo(new BooleanValue(true));
    }

    @Test
    void suppressesAnInvalidOperandOnlyWhenTheTruthTableDeterminesAValidResult() {
        ObjectModel snapshot = librarySnapshot(1, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("false and (1 / 0 = 0)", snapshot, alice).value()).isEqualTo(new BooleanValue(false));
        assertThat(evaluate("true or (1 / 0 = 0)", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("false implies (1 / 0 = 0)", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("true and (1 / 0 = 0)", snapshot, alice).value()).isEqualTo(OclInvalidValue.INSTANCE);
    }

    @Test
    void evaluatesTheB11NumericAndStringLibraryThroughTheFullPipeline() {
        ObjectModel snapshot = librarySnapshot(1, 1, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("'Use' + 'Web' = 'UseWeb'", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("'alpha' < 'beta'", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("'Use'.equalsIgnoreCase('uSE')", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("'abc'.at(2) = 'b'", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("'abc'.characters() = Sequence{'a', 'b', 'c'}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("'true'.toBoolean()", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("42.toString() = '42'", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("(-1.5).round() = -1 and (-1.2).floor() = -2", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("5 div 2 = 2 and -5 mod 2 = -1", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("*.toString() = '*' and * > 5", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluateWithoutTypecheck("2147483647 + 1", snapshot, alice).value())
                .isEqualTo(OclInvalidValue.INSTANCE);
    }

    @Test
    void evaluatesCollectionLiteralRangesCommonKindsAndDuplicateRules() {
        ObjectModel snapshot = librarySnapshot(1, 0, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("Sequence{1, 3..5}->size() = 4", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{3..1}->isEmpty()", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{1, 1, 2}->size() = 2", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Bag{1, 1, 2}->size() = 3", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("OrderedSet{2, 1, 2}->size() = 2", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
    }

    @Test
    void evaluatesAllAbstractCollectionQueries() {
        ObjectModel snapshot = librarySnapshot(1, 0, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        for (String literal : List.of("Set{1, 2, 3}", "Bag{1, 2, 3}",
                "Sequence{1, 2, 3}", "OrderedSet{1, 2, 3}")) {
            assertThat(evaluate(literal + "->size() = 3", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
            assertThat(evaluate(literal + "->notEmpty()", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
            assertThat(evaluate(literal + "->includes(2)", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
            assertThat(evaluate(literal + "->excludes(4)", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
            assertThat(evaluate(literal + "->count(2) = 1", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
            assertThat(evaluate(literal + "->includesAll(Set{1, 3})", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
            assertThat(evaluate(literal + "->excludesAll(Set{4, 5})", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
            assertThat(evaluate(literal + "->max() = 3", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
            assertThat(evaluate(literal + "->min() = 1", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
            assertThat(evaluate(literal + "->sum() = 6", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
            assertThat(evaluate(literal + "->product(Set{'a', 'b'})->size() = 6", snapshot, alice).value())
                    .isEqualTo(new BooleanValue(true));
        }
        assertThat(evaluate("Set{self}->selectByKind(User)->size() = 1", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{self}->selectByType(Book)->isEmpty()", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
    }

    @Test
    void appliesInvalidAwareAbstractCollectionSemantics() {
        ObjectModel snapshot = librarySnapshot(1, 0, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluateWithoutTypecheck("Set{1, invalid}->includes(1)", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluateWithoutTypecheck("Set{1, invalid}->includes(2)", snapshot, alice).value())
                .isEqualTo(OclInvalidValue.INSTANCE);
        assertThat(evaluateWithoutTypecheck("Set{1, invalid}->count(1)", snapshot, alice).value())
                .isEqualTo(OclInvalidValue.INSTANCE);
        assertThat(evaluateWithoutTypecheck("Set{1, invalid}->sum()", snapshot, alice).value())
                .isEqualTo(OclInvalidValue.INSTANCE);
        assertThat(evaluateWithoutTypecheck("Sequence{1}->excluding(1)->max()", snapshot, alice).value())
                .isEqualTo(OclInvalidValue.INSTANCE);
    }

    @Test
    void evaluatesSetAndBagSpecificMultiplicityOperations() {
        ObjectModel snapshot = librarySnapshot(1, 0, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("Set{1,2,3} - Set{2,4} = Set{1,3}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Set{1,2}->symmetricDifference(Set{2,3}) = Set{1,3}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Bag{1,1}->union(Set{1,2}) = Bag{1,1,1,2}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Bag{1,1,2}->intersection(Bag{1,2,2}) = Bag{1,2}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
    }

    @Test
    void evaluatesSequenceOperationsWithOneBasedIndexes() {
        ObjectModel snapshot = librarySnapshot(1, 0, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("Sequence{1,2}->append(3) = Sequence{1,2,3}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{1,2}->prepend(0) = Sequence{0,1,2}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{1,3}->insertAt(2,2) = Sequence{1,2,3}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{1,2,3,4}->subSequence(2,3) = Sequence{2,3}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{3,1,2}->at(2) = 1", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{3,1,3}->indexOf(3) = 1", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{1,2,3}->first() = 1 and Sequence{1,2,3}->last() = 3", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{1,2,3}->reverse() = Sequence{3,2,1}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{1}->at(0).oclIsInvalid()", snapshot, alice).value()).isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Sequence{1}->subSequence(1,2).oclIsInvalid()", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
    }

    @Test
    void evaluatesOrderedSetOperationsWithoutLosingOrderOrUniqueness() {
        ObjectModel snapshot = librarySnapshot(1, 0, true);
        ObjectInstance alice = object(snapshot, "obj-alice");

        assertThat(evaluate("OrderedSet{1,2,3}->append(1) = OrderedSet{2,3,1}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("OrderedSet{1,2,3}->prepend(3) = OrderedSet{3,1,2}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("OrderedSet{1,3}->insertAt(2,2) = OrderedSet{1,2,3}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("OrderedSet{1,2,3,4}->subOrderedSet(2,3) = OrderedSet{2,3}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("OrderedSet{1,2,3}->reverse() = OrderedSet{3,2,1}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("OrderedSet{3,1,2}->at(2) = 1", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("OrderedSet{3,1,2}->indexOf(2) = 3", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("OrderedSet{1,2,3}->first() = 1 and OrderedSet{1,2,3}->last() = 3", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("OrderedSet{1,2}->insertAt(4,3).oclIsInvalid()", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
        assertThat(evaluate("Bag{Sequence{1,1},Set{2}}->flatten() = Bag{1,1,2}", snapshot, alice).value())
                .isEqualTo(new BooleanValue(true));
    }

    private de.useweb.backend.ocl.evaluation.OclEvaluationResult evaluateWithoutTypecheck(
            String expression,
            ObjectModel snapshot,
            ObjectInstance self) {
        var parseResult = parser.parse(expression);
        assertThat(parseResult.success()).isTrue();
        return evaluator.evaluate(parseResult.ast(), new EvaluationContext(umlModel, snapshot, self));
    }

    private de.useweb.backend.ocl.evaluation.OclEvaluationResult evaluate(
            String expression,
            ObjectModel snapshot,
            ObjectInstance self) {
        var parseResult = parser.parse(expression);
        assertThat(parseResult.success()).isTrue();
        var typecheckResult = typeChecker.checkInvariant(umlModel, self.classId(), parseResult.ast());
        assertThat(typecheckResult.success()).isTrue();
        return evaluator.evaluate(parseResult.ast(), new EvaluationContext(umlModel, snapshot, self));
    }

    private static ObjectInstance object(ObjectModel objectModel, String objectId) {
        return objectModel.findObject(new ObjectInstanceId(objectId)).orElseThrow();
    }

    private static UmlModel libraryModel() {
        UmlClass user = new UmlClass(
                USER_CLASS_ID,
                "User",
                List.of(
                        new UmlAttribute(USER_NAME_ATTRIBUTE_ID, "name", UmlType.STRING),
                        new UmlAttribute(USER_BOOKS_ATTRIBUTE_ID, "books", UmlType.INTEGER),
                        new UmlAttribute(USER_FAVORITE_BOOK_ATTRIBUTE_ID, "favoriteBook", UmlType.classType("Book"))),
                List.of());
        UmlClass book = new UmlClass(
                BOOK_CLASS_ID,
                "Book",
                List.of(new UmlAttribute(BOOK_AVAILABLE_ATTRIBUTE_ID, "available", UmlType.BOOLEAN)),
                List.of());
        UmlAssociation borrows = new UmlAssociation(
                BORROWS_ASSOCIATION_ID,
                "Borrows",
                List.of(
                        new UmlAssociationEnd(BORROWS_USER_END_ID, USER_CLASS_ID, "borrower", Multiplicity.exactlyOne(), true),
                        new UmlAssociationEnd(BORROWS_BOOK_END_ID, BOOK_CLASS_ID, "borrowedBooks", new Multiplicity(0, 5, false, "0..5"), true)));
        return new UmlModel(new UmlModelId("model-library"), "Library", List.of(user, book), List.of(borrows), List.of());
    }

    private static ObjectModel librarySnapshot(int booksValue, int linkedBooks, boolean firstBookAvailable) {
        ObjectInstance alice = new ObjectInstance(
                new ObjectInstanceId("obj-alice"),
                "alice",
                USER_CLASS_ID,
                List.of(
                        new Slot(new SlotId("slot-alice-name"), USER_NAME_ATTRIBUTE_ID, SlotValue.ofString("Alice")),
                        new Slot(new SlotId("slot-alice-books"), USER_BOOKS_ATTRIBUTE_ID, SlotValue.ofInteger(booksValue)),
                        new Slot(new SlotId("slot-alice-favorite-book"), USER_FAVORITE_BOOK_ATTRIBUTE_ID,
                                new SlotValue(linkedBooks > 0 ? "obj-book-1" : null, UmlType.classType("Book")))));
        List<ObjectInstance> objects = new ArrayList<>();
        objects.add(alice);
        List<ObjectLink> links = new ArrayList<>();
        for (int index = 1; index <= linkedBooks; index++) {
            ObjectInstance book = new ObjectInstance(
                    new ObjectInstanceId("obj-book-" + index),
                    "book" + index,
                    BOOK_CLASS_ID,
                    List.of(new Slot(
                            new SlotId("slot-book-" + index + "-available"),
                            BOOK_AVAILABLE_ATTRIBUTE_ID,
                            SlotValue.ofBoolean(index == 1 ? firstBookAvailable : true))));
            objects.add(book);
            links.add(new ObjectLink(
                    new ObjectLinkId("link-alice-book-" + index),
                    BORROWS_ASSOCIATION_ID,
                    List.of(
                            new ObjectLinkEnd(BORROWS_USER_END_ID, alice.id()),
                            new ObjectLinkEnd(BORROWS_BOOK_END_ID, book.id()))));
        }
        return new ObjectModel(new ObjectModelId("snapshot-library"), "Library Snapshot", objects, links);
    }
}
