package de.useweb.backend.persistence.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.useweb.backend.domain.layout.DiagramLayout;
import de.useweb.backend.domain.layout.EdgeLayout;
import de.useweb.backend.domain.layout.LayoutInformation;
import de.useweb.backend.domain.layout.NodeLayout;
import de.useweb.backend.domain.layout.Point;
import de.useweb.backend.domain.layout.Viewport;
import de.useweb.backend.domain.modeltext.ModelText;
import de.useweb.backend.domain.ocl.OclExpression;
import de.useweb.backend.domain.ocl.OclExpressionId;
import de.useweb.backend.domain.ocl.OclDefinitionElement;
import de.useweb.backend.domain.ocl.OclDefinitionElementId;
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
import de.useweb.backend.domain.snapshot.QualifierValue;
import de.useweb.backend.domain.snapshot.Slot;
import de.useweb.backend.domain.snapshot.SlotId;
import de.useweb.backend.domain.snapshot.SlotValue;
import de.useweb.backend.domain.uml.Multiplicity;
import de.useweb.backend.domain.uml.AggregationKind;
import de.useweb.backend.domain.uml.UmlAssociation;
import de.useweb.backend.domain.uml.UmlAssociationEnd;
import de.useweb.backend.domain.uml.UmlAssociationEndId;
import de.useweb.backend.domain.uml.UmlAssociationId;
import de.useweb.backend.domain.uml.UmlAttribute;
import de.useweb.backend.domain.uml.UmlAttributeId;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlClassifierValue;
import de.useweb.backend.domain.uml.UmlInvariant;
import de.useweb.backend.domain.uml.UmlInvariantId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlModelId;
import de.useweb.backend.domain.uml.UmlOperation;
import de.useweb.backend.domain.uml.UmlOperationId;
import de.useweb.backend.domain.uml.UmlOperationContract;
import de.useweb.backend.domain.uml.UmlParameter;
import de.useweb.backend.domain.uml.UmlParameterId;
import de.useweb.backend.domain.uml.UmlPackage;
import de.useweb.backend.domain.uml.UmlPackageId;
import de.useweb.backend.domain.uml.ParameterDirection;
import de.useweb.backend.domain.uml.UmlVisibility;
import de.useweb.backend.domain.uml.UmlQualifierDefinition;
import de.useweb.backend.domain.uml.UmlQualifierId;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.error.InvalidProjectFormatException;

class ProjectJsonSerializerTest {

    private final ProjectJsonSerializer serializer = new ProjectJsonSerializer();

    @Test
    void libraryProjectCanRoundtripThroughDocumentedJsonFormat() {
        Project project = libraryProject();

        String json = serializer.serialize(project);
        Project restored = serializer.deserialize(json);

        assertThat(json).contains("\"formatVersion\" : \"0.1\"");
        assertThat(restored.id()).isEqualTo(project.id());
        assertThat(restored.umlModel().findClass(new UmlClassId("class-user"))).isPresent();
        assertThat(restored.umlModel().findInvariant(new UmlInvariantId("inv-max-books")))
                .get()
                .satisfies(invariant -> assertThat(invariant.expression().text()).isEqualTo("self.books <= 5"));
        assertThat(restored.objectModel().findObject(new ObjectInstanceId("obj-alice")))
                .get()
                .satisfies(object -> assertThat(object.findSlot(new UmlAttributeId("attr-user-books"))).isPresent());
        assertThat(restored.objectModel().findLink(new ObjectLinkId("link-alice-mobydick"))).isPresent();
        assertThat(restored.umlModel().associations().getFirst().ends().get(1).qualifiers())
                .singleElement().satisfies(qualifier -> assertThat(qualifier.name()).isEqualTo("inventoryKey"));
        assertThat(restored.objectModel().links().getFirst().ends().get(1).qualifierValues())
                .singleElement().satisfies(value -> assertThat(value.value().value()).isEqualTo("BOOK-1"));
        assertThat(restored.umlModel().associations().getFirst().associationClassId())
                .isEqualTo(new UmlClassId("class-loan"));
        assertThat(restored.umlModel().associations().getFirst().ends().getFirst().aggregationKind())
                .isEqualTo(AggregationKind.SHARED);
        assertThat(restored.objectModel().links().getFirst().associationClassObjectId())
                .isEqualTo(new ObjectInstanceId("obj-loan"));
        assertThat(restored.umlModel().findClass(new UmlClassId("class-user")).orElseThrow().operations().getFirst())
                .satisfies(operation -> {
                    assertThat(operation.query()).isTrue();
                    assertThat(operation.abstractOperation()).isFalse();
                    assertThat(operation.staticOperation()).isTrue();
                    assertThat(operation.parameters().getFirst().direction()).isEqualTo(ParameterDirection.INOUT);
                    assertThat(operation.parameters().getFirst().position()).isZero();
                    assertThat(operation.contracts()).singleElement().satisfies(contract -> {
                        assertThat(contract.name()).isEqualTo("BookRequired");
                        assertThat(contract.kind()).isEqualTo(UmlOperationContract.Kind.PRE);
                        assertThat(contract.enabled()).isTrue();
                    });
                    assertThat(operation.redefinedOperationIds()).containsExactly(new UmlOperationId("op-party-can-borrow"));
                });
        assertThat(restored.umlModel().findClass(new UmlClassId("class-user")).orElseThrow().attributes().getFirst()
                .redefinedAttributeIds()).containsExactly(new UmlAttributeId("attr-party-name"));
        assertThat(restored.umlModel().findAttribute(new UmlAttributeId("attr-next-user-number"))).get()
                .satisfies(attribute -> {
                    assertThat(attribute.staticAttribute()).isTrue();
                    assertThat(attribute.classifierValue().value()).isEqualTo(1043);
                });
        assertThat(restored.definitions()).singleElement().satisfies(definition -> {
            assertThat(definition.id().value()).isEqualTo("definition-library-size");
            assertThat(definition.ownerKind()).isEqualTo(OclDefinitionElement.OwnerKind.PACKAGE);
            assertThat(definition.ownerId()).isEqualTo("package-library");
            assertThat(definition.expression()).isEqualTo("42");
        });
    }

    @Test
    void layoutDataSurvivesJsonRoundtripWithoutChangingSemantics() {
        Project restored = serializer.deserialize(serializer.serialize(libraryProject()));

        assertThat(restored.layout().classDiagram().nodes())
                .extracting(NodeLayout::elementId)
                .containsExactly("class-user", "class-book");
        assertThat(restored.layout().classDiagram().edges())
                .singleElement()
                .satisfies(edge -> {
                    assertThat(edge.elementId()).isEqualTo("assoc-borrows");
                    assertThat(edge.labelPosition()).isEqualTo(new Point(320, 180));
                });
        assertThat(restored.layout().classDiagram().viewport()).isEqualTo(new Viewport(0, 0, 1));
    }

    @Test
    void modelTextSurvivesJsonRoundtripAsProjectSourceText() {
        Project restored = serializer.deserialize(serializer.serialize(libraryProject()));

        assertThat(restored.modelText()).isNotNull();
        assertThat(restored.modelText().text()).contains("context User inv maxBooks:");
        assertThat(restored.modelText().sourceName()).isEqualTo("library.use");
    }

    @Test
    void unsupportedFormatVersionReturnsStructuredImportError() {
        String json = serializer.serialize(libraryProject()).replace("\"formatVersion\" : \"0.1\"", "\"formatVersion\" : \"9.9\"");

        assertThatThrownBy(() -> serializer.deserialize(json))
                .isInstanceOf(InvalidProjectFormatException.class)
                .satisfies(exception -> {
                    InvalidProjectFormatException formatException = (InvalidProjectFormatException) exception;
                    assertThat(formatException.error().code()).isEqualTo("INVALID_PROJECT_FORMAT");
                    assertThat(formatException.error().details()).containsEntry("expectedFormatVersion", "0.1");
                });
    }

    private static Project libraryProject() {
        UmlClassId userClassId = new UmlClassId("class-user");
        UmlClassId bookClassId = new UmlClassId("class-book");
        UmlAttributeId userBooksAttributeId = new UmlAttributeId("attr-user-books");
        UmlAttributeId userNameAttributeId = new UmlAttributeId("attr-user-name");
        UmlAttributeId bookTitleAttributeId = new UmlAttributeId("attr-book-title");

        UmlClass partyClass = new UmlClass(new UmlClassId("class-party"), "Party",
                List.of(new UmlAttribute(new UmlAttributeId("attr-party-name"), "name", UmlType.STRING)),
                List.of(new UmlOperation(new UmlOperationId("op-party-can-borrow"), "canBorrow", UmlType.BOOLEAN,
                        List.of(new UmlParameter(new UmlParameterId("param-party-book"), "book",
                                UmlType.classType("Book"), ParameterDirection.INOUT, 0)), null,
                        UmlVisibility.PUBLIC, false, true, true, List.of(), List.of())));

        UmlClass userClass = new UmlClass(
                userClassId,
                "User",
                List.of(
                        new UmlAttribute(userNameAttributeId, "name", UmlType.STRING, false, null, null,
                                UmlVisibility.PUBLIC, List.of(new UmlAttributeId("attr-party-name"))),
                        new UmlAttribute(userBooksAttributeId, "books", UmlType.INTEGER),
                        new UmlAttribute(new UmlAttributeId("attr-next-user-number"), "nextUserNumber",
                                UmlType.INTEGER, false, null, null, UmlVisibility.PUBLIC, List.of(), true,
                                new UmlClassifierValue(UmlType.INTEGER, 1043))),
                List.of(new UmlOperation(
                        new UmlOperationId("op-user-can-borrow"),
                        "canBorrow",
                        UmlType.BOOLEAN,
                        List.of(new UmlParameter(new UmlParameterId("param-book"), "book",
                                UmlType.classType("Book"), ParameterDirection.INOUT, 0)),
                        null, UmlVisibility.PUBLIC, false, true, true,
                        List.of(new UmlOperationContract("contract-book-required", "BookRequired",
                                UmlOperationContract.Kind.PRE, "not book.oclIsUndefined()", true)),
                        List.of(new UmlOperationId("op-party-can-borrow")))),
                false, List.of(partyClass.id()));
        UmlClass bookClass = new UmlClass(
                bookClassId,
                "Book",
                List.of(new UmlAttribute(bookTitleAttributeId, "title", UmlType.STRING)),
                List.of());
        UmlClass loanClass = new UmlClass(new UmlClassId("class-loan"), "Loan", List.of(), List.of());

        UmlAssociationEnd userEnd = new UmlAssociationEnd(
                new UmlAssociationEndId("assocend-borrows-user"),
                userClassId,
                "borrower",
                Multiplicity.zeroToMany(), true, false, true, false, false,
                List.of(), List.of(), List.of(), AggregationKind.SHARED);
        UmlQualifierDefinition inventoryKey = new UmlQualifierDefinition(
                new UmlQualifierId("qualifier-inventory-key"), "inventoryKey", UmlType.STRING, 0);
        UmlAssociationEnd bookEnd = new UmlAssociationEnd(
                new UmlAssociationEndId("assocend-borrows-book"),
                bookClassId,
                "borrowedBooks",
                new Multiplicity(0, 5, false, "0..5"),
                true, false, true, false, false, List.of(), List.of(), List.of(inventoryKey));
        UmlAssociation borrows = new UmlAssociation(
                new UmlAssociationId("assoc-borrows"),
                "Borrows",
                List.of(userEnd, bookEnd), loanClass.id());

        UmlInvariant maxBooks = new UmlInvariant(
                new UmlInvariantId("inv-max-books"),
                "maxBooks",
                userClassId,
                new OclExpression(new OclExpressionId("expr-max-books"), "self.books <= 5", "mvp-ocl"),
                true);

        UmlModel umlModel = new UmlModel(
                new UmlModelId("uml-library"),
                "Library",
                List.of(partyClass, userClass, bookClass, loanClass),
                List.of(borrows),
                List.of(maxBooks), List.of(),
                List.of(new UmlPackage(new UmlPackageId("package-library"), "library")), List.of(), List.of());

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
        ObjectInstance loan = new ObjectInstance(new ObjectInstanceId("obj-loan"), "loan", loanClass.id(), List.of());
        ObjectLink link = new ObjectLink(
                new ObjectLinkId("link-alice-mobydick"),
                borrows.id(),
                List.of(
                        new ObjectLinkEnd(userEnd.id(), alice.id()),
                        new ObjectLinkEnd(bookEnd.id(), mobyDick.id(),
                                List.of(new QualifierValue(inventoryKey.id(), SlotValue.ofString("BOOK-1"))))), loan.id());

        LayoutInformation layout = new LayoutInformation(
                new DiagramLayout(
                        List.of(
                                new NodeLayout("class-user", 420, 120, 220.0, 150.0),
                                new NodeLayout("class-book", 120, 120, 220.0, 170.0)),
                        List.of(new EdgeLayout("assoc-borrows", List.of(), new Point(320, 180))),
                        new Viewport(0, 0, 1)),
                DiagramLayout.empty());

        Instant now = Instant.parse("2026-07-16T00:00:00Z");
        return new Project(
                new ProjectId("project-library"),
                new ProjectMetadata("Library Example", "MVP JSON fixture", ProjectJsonFormat.CURRENT_FORMAT_VERSION, now, now),
                new ModelText(
                        """
                                model Library
                                class User
                                attributes
                                books : Integer
                                end
                                constraints
                                context User inv maxBooks:
                                self.books <= 5
                                """,
                        "USE_MODEL_TEXT",
                        "mvp-subset",
                        now,
                        "library.use",
                        "test-fixture"),
                umlModel,
                new ObjectModel(new ObjectModelId("snapshot-main"), "Main Snapshot", List.of(alice, mobyDick, loan), List.of(link)),
                layout,
                List.of(new OclDefinitionElement(new OclDefinitionElementId("definition-library-size"),
                        OclDefinitionElement.Kind.PROPERTY_DEF, OclDefinitionElement.OwnerKind.PACKAGE,
                        "package-library", "librarySize", UmlType.INTEGER, List.of(), "42")));
    }
}
