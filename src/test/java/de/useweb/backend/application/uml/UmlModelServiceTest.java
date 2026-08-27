package de.useweb.backend.application.uml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.useweb.backend.api.dto.ocl.OclExpressionDto;
import de.useweb.backend.api.dto.snapshot.ObjectInstanceDto;
import de.useweb.backend.api.dto.snapshot.ObjectLinkDto;
import de.useweb.backend.api.dto.snapshot.ObjectLinkEndValueDto;
import de.useweb.backend.api.dto.snapshot.SlotDto;
import de.useweb.backend.api.dto.snapshot.SlotValueDto;
import de.useweb.backend.api.dto.uml.MultiplicityDto;
import de.useweb.backend.api.dto.uml.UmlAssociationDto;
import de.useweb.backend.api.dto.uml.UmlAssociationEndDto;
import de.useweb.backend.api.dto.uml.UmlAttributeDto;
import de.useweb.backend.api.dto.uml.UmlClassDto;
import de.useweb.backend.api.dto.uml.UmlInvariantDto;
import de.useweb.backend.api.dto.uml.UmlOperationDto;
import de.useweb.backend.api.dto.uml.UmlParameterDto;
import de.useweb.backend.api.dto.uml.UmlPackageDto;
import de.useweb.backend.api.dto.uml.UmlModelImportDto;
import de.useweb.backend.application.snapshot.ObjectModelService;
import de.useweb.backend.domain.layout.DiagramLayout;
import de.useweb.backend.domain.layout.EdgeLayout;
import de.useweb.backend.domain.layout.LayoutInformation;
import de.useweb.backend.domain.layout.NodeLayout;
import de.useweb.backend.application.project.ProjectService;
import de.useweb.backend.domain.project.Project;
import de.useweb.backend.domain.project.ProjectId;
import de.useweb.backend.domain.snapshot.ObjectInstanceId;
import de.useweb.backend.domain.uml.UmlAttributeId;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.error.UmlModelException;
import de.useweb.backend.persistence.json.ProjectJsonSerializer;
import de.useweb.backend.persistence.project.InMemoryProjectRepository;

class UmlModelServiceTest {

    private final InMemoryProjectRepository repository = new InMemoryProjectRepository();
    private final ProjectJsonSerializer serializer = new ProjectJsonSerializer();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-16T12:00:00Z"), ZoneOffset.UTC);
    private final ProjectService projectService = new ProjectService(repository, serializer, clock);
    private final UmlModelService umlModelService = new UmlModelService(projectService);
    private final ObjectModelService objectModelService = new ObjectModelService(projectService);

    @Test
    void canBuildLibraryClassModelForMvp() {
        Project project = projectService.createProject("Library", "MVP UML model");
        ProjectId projectId = project.id();

        UmlClassDto user = umlModelService.createClass(projectId, new UmlClassDto("class-user", "User", List.of(), List.of()));
        UmlClassDto book = umlModelService.createClass(projectId, new UmlClassDto("class-book", "Book", List.of(), List.of()));
        UmlAttributeDto books = umlModelService.addAttribute(
                projectId,
                new UmlClassId(user.id()),
                new UmlAttributeDto("attr-user-books", "books", "Integer"));
        UmlOperationDto canBorrow = umlModelService.addOperation(
                projectId,
                new UmlClassId(user.id()),
                new UmlOperationDto(
                        "op-user-can-borrow",
                        "canBorrow",
                        "Boolean",
                        List.of(new UmlParameterDto("param-book", "book", "Book"))));
        UmlAssociationDto borrows = umlModelService.createAssociation(
                projectId,
                new UmlAssociationDto(
                        "assoc-borrows",
                        "Borrows",
                        List.of(
                                new UmlAssociationEndDto("end-borrows-user", user.id(), "borrower", new MultiplicityDto(0, null, true, "0..*"), true),
                                new UmlAssociationEndDto("end-borrows-books", book.id(), "books", new MultiplicityDto(0, 5, false, "0..5"), true))));
        UmlInvariantDto maxBooks = umlModelService.createInvariant(
                projectId,
                new UmlInvariantDto(
                        "inv-max-books",
                        "maxBooks",
                        user.id(),
                        new OclExpressionDto("expr-max-books", "self.books <= 5", "OCL", "MVP"),
                        true));

        Project savedProject = projectService.loadProject(projectId);

        assertThat(savedProject.umlModel().classes()).hasSize(2);
        assertThat(savedProject.umlModel().findClass(new UmlClassId(user.id())).orElseThrow().attributes())
                .extracting(attribute -> attribute.name())
                .containsExactly("books");
        assertThat(books.type()).isEqualTo("Integer");
        assertThat(canBorrow.parameters()).extracting(UmlParameterDto::type).containsExactly("Book");
        assertThat(borrows.ends()).extracting(UmlAssociationEndDto::roleName).containsExactly("borrower", "books");
        assertThat(maxBooks.contextClassId()).isEqualTo(user.id());
        assertThat(maxBooks.expression().text()).isEqualTo("self.books <= 5");
    }

    @Test
    void permitsOverloadsButRejectsDuplicateOperationSignatures() {
        Project project = projectService.createProject("Operations", "B9 overload validation");
        UmlClassDto owner = umlModelService.createClass(project.id(),
                new UmlClassDto("class-calculator", "Calculator", List.of(), List.of()));
        UmlOperationDto integerVersion = new UmlOperationDto("op-calculate-integer", "calculate", "Integer",
                List.of(new UmlParameterDto("param-integer", "value", "Integer", "IN", 0)), null,
                "PUBLIC", false, true);
        UmlOperationDto realVersion = new UmlOperationDto("op-calculate-real", "calculate", "Real",
                List.of(new UmlParameterDto("param-real", "value", "Real", "IN", 0)), null,
                "PUBLIC", false, true);

        umlModelService.addOperation(project.id(), new UmlClassId(owner.id()), integerVersion);
        umlModelService.addOperation(project.id(), new UmlClassId(owner.id()), realVersion);

        assertThat(projectService.loadProject(project.id()).umlModel().findClass(new UmlClassId(owner.id()))
                .orElseThrow().operations()).hasSize(2);
        assertThatThrownBy(() -> umlModelService.addOperation(project.id(), new UmlClassId(owner.id()),
                new UmlOperationDto("op-duplicate", "calculate", "Integer",
                        List.of(new UmlParameterDto("param-duplicate", "otherName", "Integer", "IN", 0)), null,
                        "PUBLIC", false, false)))
                .isInstanceOf(UmlModelException.class)
                .satisfies(exception -> assertThat(((UmlModelException) exception).error().code())
                        .isEqualTo("TYPE_ERROR"));
    }

    @Test
    void associationWithUnknownClassIsRejected() {
        Project project = projectService.createProject("Library", "Invalid association test");
        UmlClassDto user = umlModelService.createClass(project.id(), new UmlClassDto("class-user", "User", List.of(), List.of()));

        assertThatThrownBy(() -> umlModelService.createAssociation(
                project.id(),
                new UmlAssociationDto(
                        "assoc-broken",
                        "Broken",
                        List.of(
                                new UmlAssociationEndDto("end-known", user.id(), "user", new MultiplicityDto(1, 1, false, "1"), true),
                                new UmlAssociationEndDto("end-missing", "class-missing", "missing", new MultiplicityDto(0, null, true, "0..*"), true)))))
                .isInstanceOf(UmlModelException.class)
                .satisfies(exception -> {
                    UmlModelException umlException = (UmlModelException) exception;
                    assertThat(umlException.error().code()).isEqualTo("UNKNOWN_CLASS");
                    assertThat(umlException.error().details()).containsEntry("classId", "class-missing");
                });
    }

    @Test
    void updatesAssociationEndMetadataAtomicallyWithStableEndIds() {
        Project project = projectService.createProject("Association metadata", "B6 update contract");
        UmlClassDto owner = umlModelService.createClass(project.id(),
                new UmlClassDto("class-owner", "Owner", List.of(), List.of()));
        UmlClassDto item = umlModelService.createClass(project.id(),
                new UmlClassDto("class-item", "Item", List.of(), List.of()));
        UmlAssociationDto created = umlModelService.createAssociation(project.id(), new UmlAssociationDto(
                "assoc-items", "Items", List.of(
                        new UmlAssociationEndDto("end-owner", owner.id(), "owner",
                                new MultiplicityDto(1, 1, false, "1"), true),
                        new UmlAssociationEndDto("end-items", item.id(), "items",
                                new MultiplicityDto(0, null, true, "0..*"), true))));

        UmlAssociationDto updated = umlModelService.updateAssociation(project.id(),
                new de.useweb.backend.domain.uml.UmlAssociationId(created.id()),
                new UmlAssociationDto(created.id(), created.name(), List.of(
                        created.ends().getFirst(),
                        new UmlAssociationEndDto("end-items", item.id(), "items",
                                new MultiplicityDto(0, null, true, "0..*"), true,
                                true, false, false, false, List.of(), List.of(), null))));

        assertThat(updated.ends().get(1).id()).isEqualTo("end-items");
        assertThat(updated.ends().get(1).navigationType()).isEqualTo("SEQUENCE");
        assertThat(projectService.loadProject(project.id()).umlModel().associations().getFirst().ends().get(1))
                .satisfies(end -> {
                    assertThat(end.ordered()).isTrue();
                    assertThat(end.unique()).isFalse();
                });
    }

    @Test
    void updatesAbstractFlagAndDirectSuperclassesAtomically() {
        Project project = projectService.createProject("Hierarchy", "B4 hierarchy update");
        UmlClassDto person = umlModelService.createClass(project.id(),
                new UmlClassDto("class-person", "Person", List.of(), List.of(), true, List.of()));
        UmlClassDto student = umlModelService.createClass(project.id(),
                new UmlClassDto("class-student", "Student", List.of(), List.of()));

        UmlClassDto updated = umlModelService.updateClass(project.id(), new UmlClassId(student.id()),
                new UmlClassDto(student.id(), "Student", student.attributes(), student.operations(), false,
                        List.of(person.id())));

        assertThat(updated.superClassIds()).containsExactly(person.id());
        assertThat(projectService.loadProject(project.id()).umlModel().isSubtypeOf(
                new UmlClassId(student.id()), new UmlClassId(person.id()))).isTrue();
    }

    @Test
    void rejectsGeneralizationCyclesWithStructuredDetails() {
        Project project = projectService.createProject("Hierarchy", "B4 cycle diagnostics");
        UmlClassDto person = umlModelService.createClass(project.id(),
                new UmlClassDto("class-person", "Person", List.of(), List.of()));
        UmlClassDto student = umlModelService.createClass(project.id(),
                new UmlClassDto("class-student", "Student", List.of(), List.of(), false, List.of(person.id())));

        assertThatThrownBy(() -> umlModelService.updateClass(project.id(), new UmlClassId(person.id()),
                new UmlClassDto(person.id(), person.name(), person.attributes(), person.operations(), false,
                        List.of(student.id()))))
                .isInstanceOf(UmlModelException.class)
                .satisfies(exception -> {
                    UmlModelException modelException = (UmlModelException) exception;
                    assertThat(modelException.error().code()).isEqualTo("GENERALIZATION_CYCLE");
                    assertThat(modelException.error().details()).containsKeys("classId", "cycleAtClassId");
                });
    }

    @Test
    void rejectsChangingAClassWithDirectInstancesToAbstract() {
        Project project = projectService.createProject("Hierarchy", "B4 abstract lifecycle");
        UmlClassDto person = umlModelService.createClass(project.id(),
                new UmlClassDto("class-person", "Person", List.of(), List.of()));
        objectModelService.createObject(project.id(),
                new de.useweb.backend.api.dto.snapshot.ObjectInstanceDto(
                        "object-alice", "alice", person.id(), List.of()));

        assertThatThrownBy(() -> umlModelService.updateClass(project.id(), new UmlClassId(person.id()),
                new UmlClassDto(person.id(), person.name(), person.attributes(), person.operations(), true, List.of())))
                .isInstanceOf(UmlModelException.class)
                .satisfies(exception -> {
                    UmlModelException modelException = (UmlModelException) exception;
                    assertThat(modelException.error().code()).isEqualTo("ABSTRACT_CLASS_HAS_INSTANCES");
                    assertThat(modelException.error().details()).containsEntry("className", "Person");
                });
    }

    @Test
    void persistsPackagesImportsAndFeatureVisibilityThroughServiceContracts() {
        Project project = projectService.createProject("Namespaces", "B5 service contracts");
        UmlPackageDto people = umlModelService.createPackage(project.id(),
                new UmlPackageDto("package-people", "university::people"));
        UmlPackageDto core = umlModelService.createPackage(project.id(),
                new UmlPackageDto("package-core", "shared::core"));
        UmlModelImportDto modelImport = umlModelService.createImport(project.id(),
                new UmlModelImportDto("import-core", people.id(), core.id(), "shared", "core.use", "local:core.use"));
        UmlClassDto person = umlModelService.createClass(project.id(),
                new UmlClassDto("class-person", "Person", List.of(), List.of(), false, List.of(),
                        "PUBLIC", people.id(), null));
        UmlAttributeDto attribute = umlModelService.addAttribute(project.id(), new UmlClassId(person.id()),
                new UmlAttributeDto("attr-secret", "secret", "String", false, null, null, "PUBLIC"));

        UmlAttributeDto updated = umlModelService.updateAttribute(project.id(), new UmlClassId(person.id()),
                new UmlAttributeId(attribute.id()),
                new UmlAttributeDto(attribute.id(), attribute.name(), attribute.type(), false, null, null, "PRIVATE"));

        assertThat(updated.visibility()).isEqualTo("PRIVATE");
        assertThat(modelImport.provenance()).isEqualTo("local:core.use");
        assertThat(projectService.loadProject(project.id()).umlModel().imports()).hasSize(1);
        assertThat(projectService.loadProject(project.id()).umlModel().findClass(new UmlClassId(person.id()))
                .orElseThrow().packageId().value()).isEqualTo(people.id());
    }

    @Test
    void deleteClassCascadesThroughModelSnapshotAndLayout() {
        ProjectId projectId = createLibraryModelWithSnapshotAndLayout();

        umlModelService.deleteClass(projectId, new UmlClassId("class-user"));

        Project savedProject = projectService.loadProject(projectId);
        assertThat(savedProject.umlModel().classes()).extracting(umlClass -> umlClass.id().value()).containsExactly("class-book");
        assertThat(savedProject.umlModel().associations()).isEmpty();
        assertThat(savedProject.umlModel().invariants()).isEmpty();
        assertThat(savedProject.objectModel().objects()).extracting(object -> object.id().value()).containsExactly("obj-mobydick");
        assertThat(savedProject.objectModel().links()).isEmpty();
        assertThat(savedProject.layout().classDiagram().nodes()).extracting(NodeLayout::elementId).containsExactly("class-book");
        assertThat(savedProject.layout().classDiagram().edges()).isEmpty();
        assertThat(savedProject.layout().objectDiagram().nodes()).extracting(NodeLayout::elementId).containsExactly("obj-mobydick");
        assertThat(savedProject.layout().objectDiagram().edges()).isEmpty();
    }

    @Test
    void deleteAttributeRemovesSlotsFromObjectsOfOwningClass() {
        ProjectId projectId = createLibraryModelWithSnapshotAndLayout();

        umlModelService.deleteAttribute(projectId, new UmlClassId("class-user"), new UmlAttributeId("attr-user-books"));

        Project savedProject = projectService.loadProject(projectId);
        assertThat(savedProject.umlModel().findClass(new UmlClassId("class-user")).orElseThrow().attributes())
                .extracting(attribute -> attribute.id().value())
                .doesNotContain("attr-user-books");
        assertThat(savedProject.objectModel().findObject(new ObjectInstanceId("obj-alice")).orElseThrow().slots())
                .extracting(slot -> slot.attributeId().value())
                .doesNotContain("attr-user-books");
    }

    private ProjectId createLibraryModelWithSnapshotAndLayout() {
        Project project = projectService.createProject("Library Delete", "Cascade delete test");
        ProjectId projectId = project.id();
        UmlClassDto user = umlModelService.createClass(projectId, new UmlClassDto("class-user", "User", List.of(), List.of()));
        UmlClassDto book = umlModelService.createClass(projectId, new UmlClassDto("class-book", "Book", List.of(), List.of()));
        umlModelService.addAttribute(projectId, new UmlClassId(user.id()), new UmlAttributeDto("attr-user-books", "books", "Integer"));
        umlModelService.addOperation(projectId, new UmlClassId(user.id()), new UmlOperationDto("op-user-can-borrow", "canBorrow", "Boolean", List.of()));
        umlModelService.createAssociation(
                projectId,
                new UmlAssociationDto(
                        "assoc-borrows",
                        "Borrows",
                        List.of(
                                new UmlAssociationEndDto("end-borrows-user", user.id(), "borrower", new MultiplicityDto(1, 1, false, "1"), true),
                                new UmlAssociationEndDto("end-borrows-book", book.id(), "borrowedBooks", new MultiplicityDto(0, 5, false, "0..5"), true))));
        umlModelService.createInvariant(
                projectId,
                new UmlInvariantDto(
                        "inv-max-books",
                        "maxBooks",
                        user.id(),
                        new OclExpressionDto("expr-max-books", "self.books <= 5", "OCL", "MVP"),
                        true));
        ObjectInstanceDto alice = objectModelService.createObject(
                projectId,
                new ObjectInstanceDto(
                        "obj-alice",
                        "alice",
                        user.id(),
                        List.of(new SlotDto("slot-alice-books", "attr-user-books", new SlotValueDto("Integer", 6), false))));
        ObjectInstanceDto mobyDick = objectModelService.createObject(
                projectId,
                new ObjectInstanceDto("obj-mobydick", "mobyDick", book.id(), List.of()));
        objectModelService.createObjectLink(
                projectId,
                new ObjectLinkDto(
                        "link-alice-mobydick",
                        "assoc-borrows",
                        List.of(
                                new ObjectLinkEndValueDto("end-borrows-user", alice.id()),
                                new ObjectLinkEndValueDto("end-borrows-book", mobyDick.id()))));
        Project withData = projectService.loadProject(projectId);
        projectService.replaceProject(
                projectId,
                new Project(
                        withData.id(),
                        withData.metadata(),
                        withData.modelText(),
                        withData.umlModel(),
                        withData.objectModel(),
                        new LayoutInformation(
                                new DiagramLayout(
                                        List.of(new NodeLayout("class-user", 10, 20, null, null), new NodeLayout("class-book", 200, 20, null, null)),
                                        List.of(new EdgeLayout("assoc-borrows", List.of(), null), new EdgeLayout("inv-max-books", List.of(), null)),
                                        null),
                                new DiagramLayout(
                                        List.of(new NodeLayout("obj-alice", 10, 20, null, null), new NodeLayout("obj-mobydick", 200, 20, null, null)),
                                        List.of(new EdgeLayout("link-alice-mobydick", List.of(), null)),
                                        null))));
        return projectId;
    }
}
