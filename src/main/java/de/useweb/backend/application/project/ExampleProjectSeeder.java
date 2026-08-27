package de.useweb.backend.application.project;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import de.useweb.backend.domain.layout.DiagramLayout;
import de.useweb.backend.domain.layout.LayoutInformation;
import de.useweb.backend.domain.layout.NodeLayout;
import de.useweb.backend.domain.layout.Viewport;
import de.useweb.backend.domain.modeltext.ModelText;
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
import de.useweb.backend.domain.uml.UmlParameter;
import de.useweb.backend.domain.uml.UmlParameterId;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.persistence.json.ProjectJsonFormat;
import de.useweb.backend.persistence.project.ProjectRepository;
import jakarta.annotation.PostConstruct;

@Component
public class ExampleProjectSeeder {

    public static final ProjectId UNIVERSITY_SYSTEM_PROJECT_ID = new ProjectId("project-university-system");

    private final ProjectRepository projectRepository;
    private final Clock clock;

    @Autowired
    public ExampleProjectSeeder(ProjectRepository projectRepository) {
        this(projectRepository, Clock.systemUTC());
    }

    ExampleProjectSeeder(ProjectRepository projectRepository, Clock clock) {
        this.projectRepository = projectRepository;
        this.clock = clock;
    }

    @PostConstruct
    public void seedExampleProjects() {
        if (!projectRepository.existsById(UNIVERSITY_SYSTEM_PROJECT_ID)) {
            projectRepository.save(universitySystemProject(Instant.now(clock)));
        }
    }

    Project universitySystemProject(Instant timestamp) {
        UmlClassId studentClassId = new UmlClassId("class-student");
        UmlClassId courseClassId = new UmlClassId("class-course");
        UmlClassId professorClassId = new UmlClassId("class-professor");

        UmlAttributeId studentNameAttributeId = new UmlAttributeId("attr-student-name");
        UmlAttributeId studentMatriculationAttributeId = new UmlAttributeId("attr-student-matriculation-number");
        UmlAttributeId studentSemesterAttributeId = new UmlAttributeId("attr-student-semester");
        UmlAttributeId courseTitleAttributeId = new UmlAttributeId("attr-course-title");
        UmlAttributeId courseCreditsAttributeId = new UmlAttributeId("attr-course-credits");
        UmlAttributeId courseCapacityAttributeId = new UmlAttributeId("attr-course-capacity");
        UmlAttributeId professorNameAttributeId = new UmlAttributeId("attr-professor-name");
        UmlAttributeId professorDepartmentAttributeId = new UmlAttributeId("attr-professor-department");

        UmlClass studentClass = new UmlClass(
                studentClassId,
                "Student",
                List.of(
                        new UmlAttribute(studentNameAttributeId, "name", UmlType.STRING),
                        new UmlAttribute(studentMatriculationAttributeId, "matriculationNumber", UmlType.STRING),
                        new UmlAttribute(studentSemesterAttributeId, "semester", UmlType.INTEGER)),
                List.of(
                        new UmlOperation(
                                new UmlOperationId("op-student-enroll"),
                                "enroll",
                                UmlType.BOOLEAN,
                                List.of(new UmlParameter(new UmlParameterId("param-student-enroll-course-code"), "courseCode", UmlType.STRING))),
                        new UmlOperation(
                                new UmlOperationId("op-student-drop"),
                                "drop",
                                UmlType.BOOLEAN,
                                List.of(new UmlParameter(new UmlParameterId("param-student-drop-course-code"), "courseCode", UmlType.STRING)))));

        UmlClass courseClass = new UmlClass(
                courseClassId,
                "Course",
                List.of(
                        new UmlAttribute(courseTitleAttributeId, "title", UmlType.STRING),
                        new UmlAttribute(courseCreditsAttributeId, "credits", UmlType.INTEGER),
                        new UmlAttribute(courseCapacityAttributeId, "capacity", UmlType.INTEGER)),
                List.of(new UmlOperation(new UmlOperationId("op-course-is-full"), "isFull", UmlType.BOOLEAN, List.of())));

        UmlClass professorClass = new UmlClass(
                professorClassId,
                "Professor",
                List.of(
                        new UmlAttribute(professorNameAttributeId, "name", UmlType.STRING),
                        new UmlAttribute(professorDepartmentAttributeId, "department", UmlType.STRING)),
                List.of(new UmlOperation(
                        new UmlOperationId("op-professor-teach"),
                        "teach",
                        UmlType.VOID,
                        List.of(new UmlParameter(new UmlParameterId("param-professor-teach-course-code"), "courseCode", UmlType.STRING)))));

        UmlAssociationEndId enrollmentStudentsEndId = new UmlAssociationEndId("assoc-end-enrollment-students");
        UmlAssociationEndId enrollmentCoursesEndId = new UmlAssociationEndId("assoc-end-enrollment-courses");
        UmlAssociation enrollmentAssociation = new UmlAssociation(
                new UmlAssociationId("assoc-enrollment"),
                "Enrollment",
                List.of(
                        new UmlAssociationEnd(enrollmentStudentsEndId, studentClassId, "students", Multiplicity.zeroToMany(), true),
                        new UmlAssociationEnd(enrollmentCoursesEndId, courseClassId, "courses", Multiplicity.zeroToMany(), true)));

        UmlAssociationEndId teachesProfessorEndId = new UmlAssociationEndId("assoc-end-teaches-professor");
        UmlAssociationEndId teachesCoursesEndId = new UmlAssociationEndId("assoc-end-teaches-courses");
        UmlAssociation teachesAssociation = new UmlAssociation(
                new UmlAssociationId("assoc-teaches"),
                "Teaches",
                List.of(
                        new UmlAssociationEnd(teachesProfessorEndId, professorClassId, "teacher", Multiplicity.exactlyOne(), true),
                        new UmlAssociationEnd(teachesCoursesEndId, courseClassId, "courses", Multiplicity.zeroToMany(), true)));

        UmlModel umlModel = new UmlModel(
                new UmlModelId("uml-university-system"),
                "University System Class Model",
                List.of(studentClass, courseClass, professorClass),
                List.of(enrollmentAssociation, teachesAssociation),
                List.of(
                        new UmlInvariant(
                                new UmlInvariantId("inv-student-positive-semester"),
                                "positiveSemester",
                                studentClassId,
                                new OclExpression(new OclExpressionId("ocl-student-positive-semester"), "self.semester > 0", "mvp-ocl"),
                                true),
                        new UmlInvariant(
                                new UmlInvariantId("inv-course-max-credits"),
                                "maxCredits",
                                courseClassId,
                                new OclExpression(new OclExpressionId("ocl-course-max-credits"), "self.credits <= 30", "mvp-ocl"),
                                true)));

        ObjectInstanceId aliceObjectId = new ObjectInstanceId("obj-alice");
        ObjectInstanceId bobObjectId = new ObjectInstanceId("obj-bob");
        ObjectInstanceId oclCourseObjectId = new ObjectInstanceId("obj-ocl-course");
        ObjectInstanceId umlCourseObjectId = new ObjectInstanceId("obj-uml-course");
        ObjectInstanceId smithObjectId = new ObjectInstanceId("obj-prof-smith");

        ObjectModel objectModel = new ObjectModel(
                new ObjectModelId("snapshot-university-system"),
                "University Example Snapshot",
                List.of(
                        new ObjectInstance(
                                aliceObjectId,
                                "alice",
                                studentClassId,
                                List.of(
                                        slot("slot-alice-name", studentNameAttributeId, SlotValue.ofString("Alice")),
                                        slot("slot-alice-matriculation", studentMatriculationAttributeId, SlotValue.ofString("s1001")),
                                        slot("slot-alice-semester", studentSemesterAttributeId, SlotValue.ofInteger(3)))),
                        new ObjectInstance(
                                bobObjectId,
                                "bob",
                                studentClassId,
                                List.of(
                                        slot("slot-bob-name", studentNameAttributeId, SlotValue.ofString("Bob")),
                                        slot("slot-bob-matriculation", studentMatriculationAttributeId, SlotValue.ofString("s1002")),
                                        slot("slot-bob-semester", studentSemesterAttributeId, SlotValue.ofInteger(1)))),
                        new ObjectInstance(
                                oclCourseObjectId,
                                "ocl",
                                courseClassId,
                                List.of(
                                        slot("slot-ocl-title", courseTitleAttributeId, SlotValue.ofString("OCL")),
                                        slot("slot-ocl-credits", courseCreditsAttributeId, SlotValue.ofInteger(6)),
                                        slot("slot-ocl-capacity", courseCapacityAttributeId, SlotValue.ofInteger(30)))),
                        new ObjectInstance(
                                umlCourseObjectId,
                                "uml",
                                courseClassId,
                                List.of(
                                        slot("slot-uml-title", courseTitleAttributeId, SlotValue.ofString("UML Modeling")),
                                        slot("slot-uml-credits", courseCreditsAttributeId, SlotValue.ofInteger(5)),
                                        slot("slot-uml-capacity", courseCapacityAttributeId, SlotValue.ofInteger(40)))),
                        new ObjectInstance(
                                smithObjectId,
                                "profSmith",
                                professorClassId,
                                List.of(
                                        slot("slot-smith-name", professorNameAttributeId, SlotValue.ofString("Dr. Smith")),
                                        slot("slot-smith-department", professorDepartmentAttributeId, SlotValue.ofString("Computer Science"))))),
                List.of(
                        link("link-enrollment-alice-ocl", enrollmentAssociation.id(), enrollmentStudentsEndId, aliceObjectId, enrollmentCoursesEndId, oclCourseObjectId),
                        link("link-enrollment-bob-uml", enrollmentAssociation.id(), enrollmentStudentsEndId, bobObjectId, enrollmentCoursesEndId, umlCourseObjectId),
                        link("link-teaches-smith-ocl", teachesAssociation.id(), teachesProfessorEndId, smithObjectId, teachesCoursesEndId, oclCourseObjectId),
                        link("link-teaches-smith-uml", teachesAssociation.id(), teachesProfessorEndId, smithObjectId, teachesCoursesEndId, umlCourseObjectId)));

        return new Project(
                UNIVERSITY_SYSTEM_PROJECT_ID,
                new ProjectMetadata(
                        "University System",
                        "Example project with students, courses, professors, invariants and object links.",
                        ProjectJsonFormat.CURRENT_FORMAT_VERSION,
                        timestamp,
                        timestamp),
                new ModelText(universityModelText(), "USE_MODEL_TEXT", "mvp-subset", timestamp, "UniversitySystem.use", "example-project"),
                umlModel,
                objectModel,
                layout());
    }

    private static Slot slot(String id, UmlAttributeId attributeId, SlotValue value) {
        return new Slot(new SlotId(id), attributeId, value);
    }

    private static ObjectLink link(
            String id,
            UmlAssociationId associationId,
            UmlAssociationEndId firstEndId,
            ObjectInstanceId firstObjectId,
            UmlAssociationEndId secondEndId,
            ObjectInstanceId secondObjectId) {
        return new ObjectLink(
                new ObjectLinkId(id),
                associationId,
                List.of(
                        new ObjectLinkEnd(firstEndId, firstObjectId),
                        new ObjectLinkEnd(secondEndId, secondObjectId)));
    }

    private static LayoutInformation layout() {
        return new LayoutInformation(
                new DiagramLayout(
                        List.of(
                                new NodeLayout("class-student", 80, 80, 230.0, null),
                                new NodeLayout("class-course", 420, 90, 230.0, null),
                                new NodeLayout("class-professor", 260, 360, 230.0, null)),
                        List.of(),
                        new Viewport(0, 0, 1)),
                new DiagramLayout(
                        List.of(
                                new NodeLayout("obj-alice", 80, 70, 250.0, null),
                                new NodeLayout("obj-bob", 80, 330, 250.0, null),
                                new NodeLayout("obj-ocl-course", 450, 70, 250.0, null),
                                new NodeLayout("obj-uml-course", 450, 330, 250.0, null),
                                new NodeLayout("obj-prof-smith", 800, 200, 250.0, null)),
                        List.of(),
                        new Viewport(0, 0, 1)));
    }

    private static String universityModelText() {
        return """
                model UniversitySystem

                class Student
                attributes
                  name : String
                  matriculationNumber : String
                  semester : Integer
                operations
                  enroll(courseCode : String) : Boolean
                  drop(courseCode : String) : Boolean
                end

                class Course
                attributes
                  title : String
                  credits : Integer
                  capacity : Integer
                operations
                  isFull() : Boolean
                end

                class Professor
                attributes
                  name : String
                  department : String
                operations
                  teach(courseCode : String) : Void
                end

                association Enrollment between
                  Student[*] role students
                  Course[*] role courses
                end

                association Teaches between
                  Professor[1] role teacher
                  Course[*] role courses
                end

                constraints
                context Student
                inv positiveSemester:
                  self.semester > 0

                context Course
                inv maxCredits:
                  self.credits <= 30
                """;
    }
}
