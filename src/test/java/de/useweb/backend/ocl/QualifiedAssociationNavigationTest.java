package de.useweb.backend.ocl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.useweb.backend.domain.snapshot.ObjectInstance;
import de.useweb.backend.domain.snapshot.ObjectInstanceId;
import de.useweb.backend.domain.snapshot.ObjectLink;
import de.useweb.backend.domain.snapshot.ObjectLinkEnd;
import de.useweb.backend.domain.snapshot.ObjectLinkId;
import de.useweb.backend.domain.snapshot.ObjectModel;
import de.useweb.backend.domain.snapshot.ObjectModelId;
import de.useweb.backend.domain.snapshot.QualifierValue;
import de.useweb.backend.domain.snapshot.SlotValue;
import de.useweb.backend.domain.snapshot.Slot;
import de.useweb.backend.domain.snapshot.SlotId;
import de.useweb.backend.domain.uml.Multiplicity;
import de.useweb.backend.domain.uml.UmlAssociation;
import de.useweb.backend.domain.uml.UmlAssociationEnd;
import de.useweb.backend.domain.uml.UmlAssociationEndId;
import de.useweb.backend.domain.uml.UmlAssociationId;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlAttribute;
import de.useweb.backend.domain.uml.UmlAttributeId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlModelId;
import de.useweb.backend.domain.uml.UmlQualifierDefinition;
import de.useweb.backend.domain.uml.UmlQualifierId;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.ocl.evaluation.EvaluationContext;
import de.useweb.backend.ocl.evaluation.OclEvaluator;
import de.useweb.backend.ocl.parser.OclParser;
import de.useweb.backend.ocl.typecheck.OclTypeChecker;
import de.useweb.backend.ocl.typecheck.TypeEnvironment;
import de.useweb.backend.ocl.value.ObjectValue;

class QualifiedAssociationNavigationTest {

    @Test
    void parsesTypechecksAndEvaluatesQualifiedNavigation() {
        UmlClass ownerClass = new UmlClass(new UmlClassId("owner"), "Owner", List.of(), List.of());
        UmlClass itemClass = new UmlClass(new UmlClassId("item"), "Item", List.of(), List.of());
        UmlAssociationEnd ownerEnd = new UmlAssociationEnd(new UmlAssociationEndId("owner-end"), ownerClass.id(),
                "owner", Multiplicity.exactlyOne(), true);
        UmlQualifierDefinition key = new UmlQualifierDefinition(new UmlQualifierId("item-key"), "key",
                UmlType.STRING, 0);
        UmlAssociationEnd itemEnd = new UmlAssociationEnd(new UmlAssociationEndId("item-end"), itemClass.id(),
                "items", Multiplicity.zeroToMany(), true, false, true, false, false, List.of(), List.of(), List.of(key));
        UmlAssociation association = new UmlAssociation(new UmlAssociationId("owns"), "Owns",
                List.of(ownerEnd, itemEnd));
        UmlModel model = new UmlModel(new UmlModelId("model"), "Qualified", List.of(ownerClass, itemClass),
                List.of(association), List.of());

        ObjectInstance owner = new ObjectInstance(new ObjectInstanceId("owner-1"), "owner1", ownerClass.id(), List.of());
        ObjectInstance first = new ObjectInstance(new ObjectInstanceId("item-1"), "first", itemClass.id(), List.of());
        ObjectInstance second = new ObjectInstance(new ObjectInstanceId("item-2"), "second", itemClass.id(), List.of());
        ObjectModel snapshot = new ObjectModel(new ObjectModelId("snapshot"), "Snapshot", List.of(owner, first, second),
                List.of(link("link-1", association, ownerEnd, owner, itemEnd, first, key, "A"),
                        link("link-2", association, ownerEnd, owner, itemEnd, second, key, "B")));

        var parsed = new OclParser().parse("self.items['B']");
        assertThat(parsed.success()).isTrue();
        assertThat(new OclTypeChecker().checkExpression(new TypeEnvironment(model, ownerClass), parsed.ast()).success())
                .isTrue();
        var evaluated = new OclEvaluator().evaluate(parsed.ast(), new EvaluationContext(model, snapshot, owner));
        assertThat(evaluated.success()).isTrue();
        assertThat(((ObjectValue) ((de.useweb.backend.ocl.value.CollectionValue) evaluated.value()).values().getFirst())
                .object().id()).isEqualTo(second.id());
    }

    @Test
    void navigatesFromAssociationParticipantToLinkObjectProperties() {
        UmlClass studentClass = new UmlClass(new UmlClassId("student"), "Student", List.of(), List.of());
        UmlClass courseClass = new UmlClass(new UmlClassId("course"), "Course", List.of(), List.of());
        UmlAttributeId gradeId = new UmlAttributeId("grade");
        UmlClass enrollmentClass = new UmlClass(new UmlClassId("enrollment"), "Enrollment",
                List.of(new UmlAttribute(gradeId, "grade", UmlType.REAL)), List.of());
        UmlAssociationEnd studentEnd = new UmlAssociationEnd(new UmlAssociationEndId("student-end"),
                studentClass.id(), "student", Multiplicity.zeroToMany(), true);
        UmlAssociationEnd courseEnd = new UmlAssociationEnd(new UmlAssociationEndId("course-end"),
                courseClass.id(), "course", Multiplicity.zeroToMany(), true);
        UmlAssociation association = new UmlAssociation(new UmlAssociationId("enrolls"), "Enrolls",
                List.of(studentEnd, courseEnd), enrollmentClass.id());
        UmlModel model = new UmlModel(new UmlModelId("university"), "University",
                List.of(studentClass, courseClass, enrollmentClass), List.of(association), List.of());
        ObjectInstance student = new ObjectInstance(new ObjectInstanceId("student-1"), "student1",
                studentClass.id(), List.of());
        ObjectInstance course = new ObjectInstance(new ObjectInstanceId("course-1"), "course1",
                courseClass.id(), List.of());
        ObjectInstance enrollment = new ObjectInstance(new ObjectInstanceId("enrollment-1"), "enrollment1",
                enrollmentClass.id(), List.of(new Slot(new SlotId("grade-slot"), gradeId, SlotValue.ofReal(1.7))));
        ObjectLink link = new ObjectLink(new ObjectLinkId("enrollment-link"), association.id(), List.of(
                new ObjectLinkEnd(studentEnd.id(), student.id()), new ObjectLinkEnd(courseEnd.id(), course.id())),
                enrollment.id());
        ObjectModel snapshot = new ObjectModel(new ObjectModelId("snapshot-university"), "University snapshot",
                List.of(student, course, enrollment), List.of(link));

        var parsed = new OclParser().parse("self.enrollment->collect(e | e.grade)->includes(1.7)");
        assertThat(parsed.success()).isTrue();
        assertThat(new OclTypeChecker().checkExpression(new TypeEnvironment(model, studentClass), parsed.ast()).success())
                .isTrue();
        assertThat(new OclEvaluator().evaluate(parsed.ast(), new EvaluationContext(model, snapshot, student)).value())
                .isEqualTo(new de.useweb.backend.ocl.value.BooleanValue(true));
    }

    private ObjectLink link(String id, UmlAssociation association, UmlAssociationEnd ownerEnd, ObjectInstance owner,
            UmlAssociationEnd itemEnd, ObjectInstance item, UmlQualifierDefinition key, String value) {
        return new ObjectLink(new ObjectLinkId(id), association.id(), List.of(
                new ObjectLinkEnd(ownerEnd.id(), owner.id()),
                new ObjectLinkEnd(itemEnd.id(), item.id(),
                        List.of(new QualifierValue(key.id(), SlotValue.ofString(value))))));
    }
}
