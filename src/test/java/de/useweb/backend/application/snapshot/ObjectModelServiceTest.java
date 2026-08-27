package de.useweb.backend.application.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

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
import de.useweb.backend.application.project.ProjectService;
import de.useweb.backend.application.uml.UmlModelService;
import de.useweb.backend.domain.project.Project;
import de.useweb.backend.domain.project.ProjectId;
import de.useweb.backend.domain.snapshot.ObjectInstanceId;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.error.ObjectModelException;
import de.useweb.backend.persistence.json.ProjectJsonSerializer;
import de.useweb.backend.persistence.project.InMemoryProjectRepository;

class ObjectModelServiceTest {

    private final InMemoryProjectRepository repository = new InMemoryProjectRepository();
    private final ProjectJsonSerializer serializer = new ProjectJsonSerializer();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-16T12:00:00Z"), ZoneOffset.UTC);
    private final ProjectService projectService = new ProjectService(repository, serializer, clock);
    private final UmlModelService umlModelService = new UmlModelService(projectService);
    private final ObjectModelService objectModelService = new ObjectModelService(projectService);

    @Test
    void canBuildLibrarySnapshotForMvp() {
        LibraryModel library = createLibraryModel();

        ObjectInstanceDto alice = objectModelService.createObject(
                library.projectId(),
                new ObjectInstanceDto("obj-alice", "alice", "class-user", List.of()));
        SlotDto books = objectModelService.setSlotValue(
                library.projectId(),
                new ObjectInstanceId(alice.id()),
                new SlotDto("slot-alice-books", "attr-user-books", new SlotValueDto("Integer", 6), false));
        ObjectInstanceDto mobyDick = objectModelService.createObject(
                library.projectId(),
                new ObjectInstanceDto("obj-mobydick", "mobyDick", "class-book", List.of()));
        ObjectLinkDto borrows = objectModelService.createObjectLink(
                library.projectId(),
                new ObjectLinkDto(
                        "link-alice-mobydick",
                        "assoc-borrows",
                        List.of(
                                new ObjectLinkEndValueDto("end-borrows-user", alice.id()),
                                new ObjectLinkEndValueDto("end-borrows-book", mobyDick.id()))));

        Project savedProject = projectService.loadProject(library.projectId());

        assertThat(savedProject.objectModel().objects()).hasSize(2);
        assertThat(books.value().type()).isEqualTo("Integer");
        assertThat(books.value().value()).isEqualTo(6);
        assertThat(savedProject.objectModel().findObject(new ObjectInstanceId(alice.id())).orElseThrow().slots())
                .extracting(slot -> slot.attributeId().value())
                .contains("attr-user-books");
        assertThat(borrows.associationId()).isEqualTo("assoc-borrows");
        assertThat(savedProject.objectModel().links()).hasSize(1);
    }

    @Test
    void slotValueWithWrongPrimitiveTypeIsRejected() {
        LibraryModel library = createLibraryModel();
        ObjectInstanceDto alice = objectModelService.createObject(
                library.projectId(),
                new ObjectInstanceDto("obj-alice", "alice", "class-user", List.of()));

        assertThatThrownBy(() -> objectModelService.setSlotValue(
                library.projectId(),
                new ObjectInstanceId(alice.id()),
                new SlotDto("slot-alice-books", "attr-user-books", new SlotValueDto("Integer", "six"), false)))
                .isInstanceOf(ObjectModelException.class)
                .satisfies(exception -> {
                    ObjectModelException objectModelException = (ObjectModelException) exception;
                    assertThat(objectModelException.error().code()).isEqualTo("INVALID_SLOT_VALUE");
                    assertThat(objectModelException.error().details()).containsEntry("expectedType", "Integer");
                });
    }

    @Test
    void objectLinkWithWrongObjectClassIsRejected() {
        LibraryModel library = createLibraryModel();
        ObjectInstanceDto alice = objectModelService.createObject(
                library.projectId(),
                new ObjectInstanceDto("obj-alice", "alice", "class-user", List.of()));
        ObjectInstanceDto bob = objectModelService.createObject(
                library.projectId(),
                new ObjectInstanceDto("obj-bob", "bob", "class-user", List.of()));

        assertThatThrownBy(() -> objectModelService.createObjectLink(
                library.projectId(),
                new ObjectLinkDto(
                        "link-invalid",
                        "assoc-borrows",
                        List.of(
                                new ObjectLinkEndValueDto("end-borrows-user", alice.id()),
                                new ObjectLinkEndValueDto("end-borrows-book", bob.id())))))
                .isInstanceOf(ObjectModelException.class)
                .satisfies(exception -> {
                    ObjectModelException objectModelException = (ObjectModelException) exception;
                    assertThat(objectModelException.error().code()).isEqualTo("INVALID_LINK");
                    assertThat(objectModelException.error().details()).containsEntry("expectedClassId", "class-book");
                });
    }

    @Test
    void rejectsDirectInstancesOfAbstractClasses() {
        Project project = projectService.createProject("Hierarchy", "Abstract class instances");
        UmlClassDto person = umlModelService.createClass(project.id(),
                new UmlClassDto("class-person", "Person", List.of(), List.of(), true, List.of()));

        assertThatThrownBy(() -> objectModelService.createObject(project.id(),
                new ObjectInstanceDto("object-person", "person", person.id(), List.of())))
                .isInstanceOf(ObjectModelException.class)
                .satisfies(exception -> {
                    ObjectModelException modelException = (ObjectModelException) exception;
                    assertThat(modelException.error().code()).isEqualTo("TYPE_ERROR");
                    assertThat(modelException.error().details()).containsEntry("classId", person.id());
                });
    }

    @Test
    void appliesInitDefaultsAndRejectsWritesToDerivedAttributes() {
        Project project = projectService.createProject("Definitions", "Definition lifecycle");
        ProjectId projectId = project.id();
        UmlClassDto item = umlModelService.createClass(projectId,
                new UmlClassDto("class-item", "Item", List.of(), List.of()));
        umlModelService.addAttribute(projectId, new UmlClassId(item.id()),
                new UmlAttributeDto("attr-count", "count", "Integer", false, null, "3"));
        umlModelService.addAttribute(projectId, new UmlClassId(item.id()),
                new UmlAttributeDto("attr-total", "total", "Integer", true, "self.count + 1", null));

        ObjectInstanceDto created = objectModelService.createObject(projectId,
                new ObjectInstanceDto("object-item", "item", item.id(), List.of()));

        assertThat(created.slots()).extracting(slot -> slot.attributeId()).containsExactly("attr-count");
        assertThat(created.slots().getFirst().value().value()).isEqualTo(3);
        assertThatThrownBy(() -> objectModelService.setSlotValue(projectId,
                new ObjectInstanceId(created.id()),
                new SlotDto("slot-total", "attr-total", new SlotValueDto("Integer", 4), false)))
                .isInstanceOf(ObjectModelException.class)
                .satisfies(exception -> assertThat(((ObjectModelException) exception).error().code())
                        .isEqualTo("INVALID_SLOT_VALUE"));
    }

    @Test
    void failingInitDoesNotPersistTheDraftObject() {
        Project project = projectService.createProject("Definitions", "Atomic init failure");
        UmlClassDto item = umlModelService.createClass(project.id(),
                new UmlClassDto("class-item", "Item", List.of(), List.of()));
        umlModelService.addAttribute(project.id(), new UmlClassId(item.id()),
                new UmlAttributeDto("attr-count", "count", "Integer", false, null, "1 / 0"));

        assertThatThrownBy(() -> objectModelService.createObject(project.id(),
                new ObjectInstanceDto("object-item", "item", item.id(), List.of())))
                .isInstanceOf(ObjectModelException.class);
        assertThat(projectService.loadProject(project.id()).objectModel().objects()).isEmpty();
    }

    @Test
    void associationClassObjectIdentifiesExactlyOneLink() {
        Project project = projectService.createProject("University", "Association class");
        ProjectId projectId = project.id();
        UmlClassDto student = umlModelService.createClass(projectId,
                new UmlClassDto("class-student", "Student", List.of(), List.of()));
        UmlClassDto course = umlModelService.createClass(projectId,
                new UmlClassDto("class-course", "Course", List.of(), List.of()));
        UmlClassDto enrollment = umlModelService.createClass(projectId,
                new UmlClassDto("class-enrollment", "Enrollment", List.of(), List.of()));
        umlModelService.addAttribute(projectId, new UmlClassId(enrollment.id()),
                new UmlAttributeDto("attr-grade", "grade", "Real"));
        umlModelService.createAssociation(projectId, new UmlAssociationDto("assoc-enrolls", "Enrolls",
                List.of(end("end-student", student.id(), "student", "NONE"),
                        end("end-course", course.id(), "course", "NONE")), enrollment.id()));
        ObjectInstanceDto studentObject = objectModelService.createObject(projectId,
                new ObjectInstanceDto("student-1", "student1", student.id(), List.of()));
        ObjectInstanceDto courseObject = objectModelService.createObject(projectId,
                new ObjectInstanceDto("course-1", "course1", course.id(), List.of()));
        ObjectInstanceDto enrollmentObject = objectModelService.createObject(projectId,
                new ObjectInstanceDto("enrollment-1", "enrollment1", enrollment.id(), List.of()));

        ObjectLinkDto link = objectModelService.createObjectLink(projectId,
                new ObjectLinkDto("link-enrollment", "assoc-enrolls", List.of(
                        new ObjectLinkEndValueDto("end-student", studentObject.id()),
                        new ObjectLinkEndValueDto("end-course", courseObject.id())), enrollmentObject.id()));

        assertThat(link.associationClassObjectId()).isEqualTo(enrollmentObject.id());
        assertThatThrownBy(() -> objectModelService.createObjectLink(projectId,
                new ObjectLinkDto("link-enrollment-duplicate", "assoc-enrolls", List.of(
                        new ObjectLinkEndValueDto("end-student", studentObject.id()),
                        new ObjectLinkEndValueDto("end-course", courseObject.id())), enrollmentObject.id())))
                .isInstanceOf(ObjectModelException.class);
    }

    @Test
    void compositionRejectsSharedPartsAndCascadesWhenWholeIsDeleted() {
        Project project = projectService.createProject("Composition", "Ownership");
        ProjectId projectId = project.id();
        UmlClassDto folder = umlModelService.createClass(projectId,
                new UmlClassDto("class-folder", "Folder", List.of(), List.of()));
        UmlClassDto file = umlModelService.createClass(projectId,
                new UmlClassDto("class-file", "File", List.of(), List.of()));
        umlModelService.createAssociation(projectId, new UmlAssociationDto("assoc-contains", "Contains",
                List.of(end("end-whole", folder.id(), "folder", "COMPOSITE"),
                        end("end-part", file.id(), "files", "NONE"))));
        ObjectInstanceDto firstFolder = objectModelService.createObject(projectId,
                new ObjectInstanceDto("folder-1", "folder1", folder.id(), List.of()));
        ObjectInstanceDto secondFolder = objectModelService.createObject(projectId,
                new ObjectInstanceDto("folder-2", "folder2", folder.id(), List.of()));
        ObjectInstanceDto fileObject = objectModelService.createObject(projectId,
                new ObjectInstanceDto("file-1", "file1", file.id(), List.of()));
        objectModelService.createObjectLink(projectId, new ObjectLinkDto("link-contains", "assoc-contains", List.of(
                new ObjectLinkEndValueDto("end-whole", firstFolder.id()),
                new ObjectLinkEndValueDto("end-part", fileObject.id()))));

        assertThatThrownBy(() -> objectModelService.createObjectLink(projectId,
                new ObjectLinkDto("link-shared", "assoc-contains", List.of(
                        new ObjectLinkEndValueDto("end-whole", secondFolder.id()),
                        new ObjectLinkEndValueDto("end-part", fileObject.id())))))
                .isInstanceOf(ObjectModelException.class)
                .satisfies(error -> assertThat(((ObjectModelException) error).error().code())
                        .isEqualTo("COMPOSITE_OWNERSHIP_VIOLATION"));

        objectModelService.deleteObject(projectId, new ObjectInstanceId(firstFolder.id()));
        Project saved = projectService.loadProject(projectId);
        assertThat(saved.objectModel().findObject(new ObjectInstanceId(firstFolder.id()))).isEmpty();
        assertThat(saved.objectModel().findObject(new ObjectInstanceId(fileObject.id()))).isEmpty();
        assertThat(saved.objectModel().findObject(new ObjectInstanceId(secondFolder.id()))).isPresent();
        assertThat(saved.objectModel().links()).isEmpty();
    }

    private UmlAssociationEndDto end(String id, String classId, String role, String aggregationKind) {
        return new UmlAssociationEndDto(id, classId, role, new MultiplicityDto(0, null, true, "0..*"),
                true, false, true, false, false, List.of(), List.of(), "SET", List.of(), aggregationKind);
    }

    private LibraryModel createLibraryModel() {
        Project project = projectService.createProject("Library", "Snapshot test");
        ProjectId projectId = project.id();
        UmlClassDto user = umlModelService.createClass(projectId, new UmlClassDto("class-user", "User", List.of(), List.of()));
        UmlClassDto book = umlModelService.createClass(projectId, new UmlClassDto("class-book", "Book", List.of(), List.of()));
        umlModelService.addAttribute(projectId, new UmlClassId(user.id()), new UmlAttributeDto("attr-user-books", "books", "Integer"));
        umlModelService.createAssociation(
                projectId,
                new UmlAssociationDto(
                        "assoc-borrows",
                        "Borrows",
                        List.of(
                                new UmlAssociationEndDto("end-borrows-user", user.id(), "borrower", new MultiplicityDto(0, null, true, "0..*"), true),
                                new UmlAssociationEndDto("end-borrows-book", book.id(), "books", new MultiplicityDto(0, 5, false, "0..5"), true))));
        return new LibraryModel(projectId);
    }

    private record LibraryModel(ProjectId projectId) {
    }
}
