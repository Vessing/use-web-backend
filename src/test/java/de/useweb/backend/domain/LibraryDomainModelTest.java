package de.useweb.backend.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
import de.useweb.backend.domain.uml.UmlOperation;
import de.useweb.backend.domain.uml.UmlOperationId;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.domain.validation.ElementTarget;
import de.useweb.backend.domain.validation.ElementType;
import de.useweb.backend.domain.validation.ValidationError;
import de.useweb.backend.domain.validation.ValidationErrorCode;
import de.useweb.backend.domain.validation.ValidationErrorId;
import de.useweb.backend.domain.validation.ValidationResult;
import de.useweb.backend.domain.validation.ValidationResultId;
import de.useweb.backend.domain.validation.ValidationSeverity;
import de.useweb.backend.domain.validation.ValidationStatus;

class LibraryDomainModelTest {

    @Test
    void projectCanRepresentLibraryModelWithUmlModelSnapshotAndInvariant() {
        Project project = libraryProject();

        assertThat(project.id()).isEqualTo(new ProjectId("project-library"));
        assertThat(project.umlModel().findClass(new UmlClassId("class-user"))).isPresent();
        assertThat(project.umlModel().findClass(new UmlClassId("class-book"))).isPresent();
        assertThat(project.umlModel().findAssociation(new UmlAssociationId("assoc-borrows")))
                .get()
                .satisfies(association -> assertThat(association.ends()).hasSize(2));
        assertThat(project.umlModel().findInvariant(new UmlInvariantId("inv-max-books")))
                .get()
                .satisfies(invariant -> {
                    assertThat(invariant.contextClassId()).isEqualTo(new UmlClassId("class-user"));
                    assertThat(invariant.expression().text()).isEqualTo("self.books <= 5");
                    assertThat(invariant.enabled()).isTrue();
                });

        assertThat(project.objectModel().findObject(new ObjectInstanceId("obj-alice")))
                .get()
                .satisfies(object -> {
                    assertThat(object.classId()).isEqualTo(new UmlClassId("class-user"));
                    assertThat(object.findSlot(new UmlAttributeId("attr-user-books")))
                            .get()
                            .extracting(Slot::value)
                            .isEqualTo(SlotValue.ofInteger(6));
                });
        assertThat(project.objectModel().findLink(new ObjectLinkId("link-alice-mobydick")))
                .get()
                .satisfies(link -> {
                    assertThat(link.associationId()).isEqualTo(new UmlAssociationId("assoc-borrows"));
                    assertThat(link.ends()).hasSize(2);
                });
    }

    @Test
    void associationAndObjectLinkRequireAtLeastTwoEnds() {
        UmlAssociationEnd onlyEnd = new UmlAssociationEnd(
                new UmlAssociationEndId("assocend-borrows-user"),
                new UmlClassId("class-user"),
                "borrower",
                Multiplicity.zeroToMany(),
                true);

        assertThatThrownBy(() -> new UmlAssociation(
                new UmlAssociationId("assoc-borrows"),
                "Borrows",
                List.of(onlyEnd)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least two ends");

        assertThatThrownBy(() -> new ObjectLink(
                new ObjectLinkId("link-invalid"),
                new UmlAssociationId("assoc-borrows"),
                List.of(new ObjectLinkEnd(onlyEnd.id(), new ObjectInstanceId("obj-alice")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least two ends");
    }

    @Test
    void validationResultCanReferenceUiMappableTargets() {
        ValidationError error = new ValidationError(
                new ValidationErrorId("err-max-books-alice"),
                ValidationErrorCode.INVARIANT_VIOLATION,
                ValidationSeverity.ERROR,
                "alice violates maxBooks",
                List.of(
                        new ElementTarget(ElementType.OBJECT, "obj-alice", "objectModel.objects[obj-alice]"),
                        new ElementTarget(ElementType.INVARIANT, "inv-max-books", "umlModel.invariants[inv-max-books]")),
                Map.of("expression", "self.books <= 5"));

        ValidationResult result = ValidationResult.invalid(
                new ValidationResultId("validation-1"),
                new ProjectId("project-library"),
                new ObjectModelId("snapshot-main"),
                List.of(error));

        assertThat(result.status()).isEqualTo(ValidationStatus.INVALID);
        assertThat(result.summary().errorCount()).isEqualTo(1);
        assertThat(result.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(ValidationErrorCode.INVARIANT_VIOLATION);
            assertThat(finding.targets())
                    .extracting(ElementTarget::elementId)
                    .containsExactly("obj-alice", "inv-max-books");
        });
    }

    private static Project libraryProject() {
        UmlClassId userClassId = new UmlClassId("class-user");
        UmlClassId bookClassId = new UmlClassId("class-book");
        UmlAttributeId userBooksAttributeId = new UmlAttributeId("attr-user-books");
        UmlAttributeId userNameAttributeId = new UmlAttributeId("attr-user-name");
        UmlAttributeId bookTitleAttributeId = new UmlAttributeId("attr-book-title");

        UmlClass userClass = new UmlClass(
                userClassId,
                "User",
                List.of(
                        new UmlAttribute(userNameAttributeId, "name", UmlType.STRING),
                        new UmlAttribute(userBooksAttributeId, "books", UmlType.INTEGER)),
                List.of(new UmlOperation(
                        new UmlOperationId("op-user-can-borrow"),
                        "canBorrow",
                        UmlType.BOOLEAN,
                        List.of())));

        UmlClass bookClass = new UmlClass(
                bookClassId,
                "Book",
                List.of(new UmlAttribute(bookTitleAttributeId, "title", UmlType.STRING)),
                List.of());

        UmlAssociationEnd userEnd = new UmlAssociationEnd(
                new UmlAssociationEndId("assocend-borrows-user"),
                userClassId,
                "borrower",
                Multiplicity.zeroToMany(),
                true);
        UmlAssociationEnd bookEnd = new UmlAssociationEnd(
                new UmlAssociationEndId("assocend-borrows-book"),
                bookClassId,
                "borrowedBooks",
                new Multiplicity(0, 5, false, "0..5"),
                true);
        UmlAssociation borrows = new UmlAssociation(
                new UmlAssociationId("assoc-borrows"),
                "Borrows",
                List.of(userEnd, bookEnd));

        UmlInvariant maxBooks = new UmlInvariant(
                new UmlInvariantId("inv-max-books"),
                "maxBooks",
                userClassId,
                new OclExpression(new OclExpressionId("expr-max-books"), "self.books <= 5", "mvp-ocl"),
                true);

        UmlModel umlModel = new UmlModel(
                new UmlModelId("uml-library"),
                "Library",
                List.of(userClass, bookClass),
                List.of(borrows),
                List.of(maxBooks));

        ObjectInstance alice = new ObjectInstance(
                new ObjectInstanceId("obj-alice"),
                "alice",
                userClassId,
                List.of(
                        new Slot(new SlotId("slot-alice-name"), userNameAttributeId, SlotValue.ofString("Alice")),
                        new Slot(new SlotId("slot-alice-books"), userBooksAttributeId, SlotValue.ofInteger(6))));
        ObjectInstance mobyDick = new ObjectInstance(
                new ObjectInstanceId("obj-mobydick"),
                "mobyDick",
                bookClassId,
                List.of(new Slot(new SlotId("slot-mobydick-title"), bookTitleAttributeId, SlotValue.ofString("Moby Dick"))));

        ObjectLink link = new ObjectLink(
                new ObjectLinkId("link-alice-mobydick"),
                borrows.id(),
                List.of(
                        new ObjectLinkEnd(userEnd.id(), alice.id()),
                        new ObjectLinkEnd(bookEnd.id(), mobyDick.id())));

        ObjectModel objectModel = new ObjectModel(
                new ObjectModelId("snapshot-main"),
                "Main Snapshot",
                List.of(alice, mobyDick),
                List.of(link));

        Instant now = Instant.parse("2026-07-16T00:00:00Z");
        return new Project(
                new ProjectId("project-library"),
                new ProjectMetadata("Library Example", "MVP domain model fixture", "0.1", now, now),
                umlModel,
                objectModel,
                LayoutInformation.empty());
    }
}
