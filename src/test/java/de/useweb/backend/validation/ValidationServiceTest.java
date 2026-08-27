package de.useweb.backend.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.useweb.backend.domain.layout.LayoutInformation;
import de.useweb.backend.domain.ocl.OclExpression;
import de.useweb.backend.domain.ocl.OclExpressionId;
import de.useweb.backend.domain.project.Project;
import de.useweb.backend.domain.project.ProjectId;
import de.useweb.backend.domain.project.ProjectMetadata;
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
import de.useweb.backend.domain.uml.UmlInvariant;
import de.useweb.backend.domain.uml.UmlInvariantId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlModelId;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.domain.validation.ValidationErrorCode;
import de.useweb.backend.domain.validation.ValidationStatus;
import de.useweb.backend.api.mapper.ProjectDtoMapper;
import de.useweb.backend.validation.service.ValidationService;

class ValidationServiceTest {

    private static final UmlClassId USER_CLASS_ID = new UmlClassId("class-user");
    private static final UmlClassId BOOK_CLASS_ID = new UmlClassId("class-book");
    private static final UmlAttributeId USER_NAME_ATTRIBUTE_ID = new UmlAttributeId("attr-user-name");
    private static final UmlAttributeId USER_BOOKS_ATTRIBUTE_ID = new UmlAttributeId("attr-user-books");
    private static final UmlAttributeId BOOK_TITLE_ATTRIBUTE_ID = new UmlAttributeId("attr-book-title");
    private static final UmlAssociationId BORROWS_ASSOCIATION_ID = new UmlAssociationId("assoc-borrows");
    private static final UmlAssociationEndId BORROWS_USER_END_ID = new UmlAssociationEndId("assoc-borrows-user");
    private static final UmlAssociationEndId BORROWS_BOOK_END_ID = new UmlAssociationEndId("assoc-borrows-book");
    private static final UmlInvariantId MAX_BOOKS_INVARIANT_ID = new UmlInvariantId("inv-max-books");

    private final ValidationService validationService = new ValidationService();

    @Test
    void validLibrarySnapshotReturnsValid() {
        var result = validationService.validate(project("self.books <= 5", librarySnapshot(3, 3)));

        assertThat(result.status()).isEqualTo(ValidationStatus.VALID);
        assertThat(result.findings()).isEmpty();
        assertThat(result.summary().errorCount()).isZero();
    }

    @Test
    void invariantViolationReferencesContextObjectClassAndInvariant() {
        var result = validationService.validate(project("self.books <= 5", librarySnapshot(6, 3)));

        assertThat(result.status()).isEqualTo(ValidationStatus.INVALID);
        assertThat(result.findings())
                .anySatisfy(error -> {
                    assertThat(error.code()).isEqualTo(ValidationErrorCode.INVARIANT_VIOLATION);
                    assertThat(error.details()).containsEntry("contextObjectId", "obj-alice");
                    assertThat(error.details()).containsEntry("contextClassId", USER_CLASS_ID.value());
                    assertThat(error.details()).containsEntry("invariantId", MAX_BOOKS_INVARIANT_ID.value());
                    assertThat(error.targets()).extracting(target -> target.elementId())
                            .contains("obj-alice", MAX_BOOKS_INVARIANT_ID.value(), USER_CLASS_ID.value());
                });
        var dto = ProjectDtoMapper.toDto(result);
        assertThat(dto.findings())
                .anySatisfy(error -> {
                    assertThat(error.kind()).isEqualTo("VALIDATION_ERROR");
                    assertThat(error.code()).isEqualTo("INVARIANT_VIOLATION");
                    assertThat(error.userMessage()).contains("alice").contains("maxBooks");
                    assertThat(error.elementType()).isEqualTo("OBJECT");
                    assertThat(error.elementId()).isEqualTo("obj-alice");
                    assertThat(error.contextObjectId()).isEqualTo("obj-alice");
                    assertThat(error.contextClassId()).isEqualTo(USER_CLASS_ID.value());
                    assertThat(error.invariantId()).isEqualTo(MAX_BOOKS_INVARIANT_ID.value());
                    assertThat(error.expression()).isEqualTo("self.books <= 5");
                    assertThat(error.relatedElementIds()).contains("obj-alice", MAX_BOOKS_INVARIANT_ID.value());
                });
    }

    @Test
    void invalidSlotValueIsReported() {
        ObjectModel snapshot = librarySnapshot(3, 3);
        ObjectInstance invalidAlice = new ObjectInstance(
                new ObjectInstanceId("obj-alice"),
                "alice",
                USER_CLASS_ID,
                List.of(
                        new Slot(new SlotId("slot-alice-name"), USER_NAME_ATTRIBUTE_ID, SlotValue.ofString("Alice")),
                        new Slot(new SlotId("slot-alice-books"), USER_BOOKS_ATTRIBUTE_ID, new SlotValue("six", UmlType.INTEGER))));
        snapshot = replaceObject(snapshot, invalidAlice);

        var result = validationService.validate(project("self.books <= 5", snapshot));

        assertThat(result.findings())
                .anySatisfy(error -> assertThat(error.code()).isEqualTo(ValidationErrorCode.INVALID_SLOT_VALUE));
    }

    @Test
    void unsetSlotValueInInvariantIsReportedAsEvaluationError() {
        ObjectModel snapshot = librarySnapshot(3, 3);
        ObjectInstance aliceWithUnsetBooks = new ObjectInstance(
                new ObjectInstanceId("obj-alice"),
                "alice",
                USER_CLASS_ID,
                List.of(
                        new Slot(new SlotId("slot-alice-name"), USER_NAME_ATTRIBUTE_ID, SlotValue.ofString("Alice")),
                        new Slot(new SlotId("slot-alice-books"), USER_BOOKS_ATTRIBUTE_ID, new SlotValue(null, UmlType.INTEGER))));
        snapshot = replaceObject(snapshot, aliceWithUnsetBooks);

        var result = validationService.validate(project("self.books <= 5", snapshot));

        assertThat(result.status()).isEqualTo(ValidationStatus.INVALID);
        assertThat(result.findings())
                .anySatisfy(error -> {
                    assertThat(error.code()).isEqualTo(ValidationErrorCode.EVALUATION_ERROR);
                    assertThat(error.message()).contains("maxBooks").contains("invalid");
                    assertThat(error.details()).containsEntry("contextObjectId", "obj-alice");
                    assertThat(error.details()).containsEntry("invariantName", "maxBooks");
                    assertThat(error.details()).containsEntry("valueKind", "INVALID");
                    assertThat(error.targets()).extracting(target -> target.elementId())
                            .contains("obj-alice", MAX_BOOKS_INVARIANT_ID.value());
                });
    }

    @Test
    void invalidObjectLinkIsReported() {
        ObjectModel snapshot = librarySnapshot(3, 3);
        ObjectInstance alice = object(snapshot, "obj-alice");
        ObjectInstance firstBook = object(snapshot, "obj-book-1");
        ObjectLink invalidLink = new ObjectLink(
                new ObjectLinkId("link-invalid"),
                BORROWS_ASSOCIATION_ID,
                List.of(
                        new ObjectLinkEnd(BORROWS_USER_END_ID, firstBook.id()),
                        new ObjectLinkEnd(BORROWS_BOOK_END_ID, alice.id())));
        snapshot = new ObjectModel(snapshot.id(), snapshot.name(), snapshot.objects(), List.of(invalidLink));

        var result = validationService.validate(project("self.books <= 5", snapshot));

        assertThat(result.findings())
                .anySatisfy(error -> assertThat(error.code()).isEqualTo(ValidationErrorCode.INVALID_LINK));
    }

    @Test
    void multiplicityViolationIsReportedForTooManyBorrowedBooks() {
        var result = validationService.validate(project("self.books <= 10", librarySnapshot(6, 6)));

        assertThat(result.findings())
                .anySatisfy(error -> {
                    assertThat(error.code()).isEqualTo(ValidationErrorCode.MULTIPLICITY_VIOLATION);
                    assertThat(error.details()).containsEntry("roleName", "borrowedBooks");
                    assertThat(error.details()).containsEntry("actualCount", 6);
                });
        var dto = ProjectDtoMapper.toDto(result);
        assertThat(dto.findings())
                .anySatisfy(error -> {
                    assertThat(error.code()).isEqualTo("MULTIPLICITY_VIOLATION");
                    assertThat(error.elementType()).isEqualTo("OBJECT");
                    assertThat(error.elementId()).isEqualTo("obj-alice");
                    assertThat(error.relatedElementIds()).contains("obj-alice", BORROWS_ASSOCIATION_ID.value(), BORROWS_BOOK_END_ID.value());
                    assertThat(error.suggestedFix()).contains("Multiplizitaet");
                });
    }

    @Test
    void oclSyntaxErrorDoesNotBlockSnapshotChecks() {
        ObjectModel snapshot = librarySnapshot(3, 6);

        var result = validationService.validate(project("self.books <=", snapshot));

        assertThat(result.findings()).extracting(error -> error.code())
                .contains(ValidationErrorCode.SYNTAX_ERROR, ValidationErrorCode.MULTIPLICITY_VIOLATION);
        var dto = ProjectDtoMapper.toDto(result);
        assertThat(dto.findings())
                .anySatisfy(error -> {
                    assertThat(error.code()).isEqualTo("SYNTAX_ERROR");
                    assertThat(error.location()).isNotNull();
                    assertThat(error.location().startLine()).isEqualTo(1);
                    assertThat(error.location().startColumn()).isGreaterThan(0);
                    assertThat(error.elementType()).isEqualTo("INVARIANT");
                    assertThat(error.targets()).extracting(target -> target.elementType())
                            .contains("OCL_EXPRESSION");
                });
    }

    @Test
    void quantifierViolationKeepsInvariantAndContextObjectMapping() {
        String expression = "self.borrowedBooks->forAll(book | book.title = 'Expected')";

        var result = validationService.validate(project(expression, librarySnapshot(3, 3)));

        assertThat(result.status()).isEqualTo(ValidationStatus.INVALID);
        assertThat(result.findings()).anySatisfy(error -> {
            assertThat(error.code()).isEqualTo(ValidationErrorCode.INVARIANT_VIOLATION);
            assertThat(error.details()).containsEntry("contextObjectId", "obj-alice");
            assertThat(error.details()).containsEntry("invariantId", MAX_BOOKS_INVARIANT_ID.value());
        });
        assertThat(ProjectDtoMapper.toDto(result).findings()).anySatisfy(error -> {
            assertThat(error.code()).isEqualTo("INVARIANT_VIOLATION");
            assertThat(error.contextObjectId()).isEqualTo("obj-alice");
            assertThat(error.invariantId()).isEqualTo(MAX_BOOKS_INVARIANT_ID.value());
            assertThat(error.expression()).isEqualTo(expression);
        });
    }

    private static Project project(String invariantExpression, ObjectModel objectModel) {
        return new Project(
                new ProjectId("project-library"),
                new ProjectMetadata("Library", "Library validation test", "0.1", Instant.EPOCH, Instant.EPOCH),
                libraryModel(invariantExpression),
                objectModel,
                LayoutInformation.empty());
    }

    private static UmlModel libraryModel(String invariantExpression) {
        UmlClass user = new UmlClass(
                USER_CLASS_ID,
                "User",
                List.of(
                        new UmlAttribute(USER_NAME_ATTRIBUTE_ID, "name", UmlType.STRING),
                        new UmlAttribute(USER_BOOKS_ATTRIBUTE_ID, "books", UmlType.INTEGER)),
                List.of());
        UmlClass book = new UmlClass(
                BOOK_CLASS_ID,
                "Book",
                List.of(new UmlAttribute(BOOK_TITLE_ATTRIBUTE_ID, "title", UmlType.STRING)),
                List.of());
        UmlAssociation borrows = new UmlAssociation(
                BORROWS_ASSOCIATION_ID,
                "Borrows",
                List.of(
                        new UmlAssociationEnd(BORROWS_USER_END_ID, USER_CLASS_ID, "borrower", Multiplicity.exactlyOne(), true),
                        new UmlAssociationEnd(BORROWS_BOOK_END_ID, BOOK_CLASS_ID, "borrowedBooks", new Multiplicity(0, 5, false, "0..5"), true)));
        UmlInvariant maxBooks = new UmlInvariant(
                MAX_BOOKS_INVARIANT_ID,
                "maxBooks",
                USER_CLASS_ID,
                new OclExpression(new OclExpressionId("expr-max-books"), invariantExpression, "mvp-ocl"),
                true);
        return new UmlModel(new UmlModelId("model-library"), "Library", List.of(user, book), List.of(borrows), List.of(maxBooks));
    }

    private static ObjectModel librarySnapshot(int booksValue, int linkedBooks) {
        ObjectInstance alice = new ObjectInstance(
                new ObjectInstanceId("obj-alice"),
                "alice",
                USER_CLASS_ID,
                List.of(
                        new Slot(new SlotId("slot-alice-name"), USER_NAME_ATTRIBUTE_ID, SlotValue.ofString("Alice")),
                        new Slot(new SlotId("slot-alice-books"), USER_BOOKS_ATTRIBUTE_ID, SlotValue.ofInteger(booksValue))));
        List<ObjectInstance> objects = new ArrayList<>();
        objects.add(alice);
        List<ObjectLink> links = new ArrayList<>();
        for (int index = 1; index <= linkedBooks; index++) {
            ObjectInstance book = new ObjectInstance(
                    new ObjectInstanceId("obj-book-" + index),
                    "book" + index,
                    BOOK_CLASS_ID,
                    List.of(new Slot(
                            new SlotId("slot-book-" + index + "-title"),
                            BOOK_TITLE_ATTRIBUTE_ID,
                            SlotValue.ofString("Book " + index))));
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

    private static ObjectInstance object(ObjectModel objectModel, String objectId) {
        return objectModel.findObject(new ObjectInstanceId(objectId)).orElseThrow();
    }

    private static ObjectModel replaceObject(ObjectModel snapshot, ObjectInstance replacement) {
        return new ObjectModel(
                snapshot.id(),
                snapshot.name(),
                snapshot.objects().stream()
                        .map(object -> object.id().equals(replacement.id()) ? replacement : object)
                        .toList(),
                snapshot.links());
    }
}
