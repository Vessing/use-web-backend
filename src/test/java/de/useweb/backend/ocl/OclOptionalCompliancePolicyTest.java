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
import de.useweb.backend.domain.uml.Multiplicity;
import de.useweb.backend.domain.uml.UmlAssociation;
import de.useweb.backend.domain.uml.UmlAssociationEnd;
import de.useweb.backend.domain.uml.UmlAssociationEndId;
import de.useweb.backend.domain.uml.UmlAssociationId;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlModelId;
import de.useweb.backend.ocl.evaluation.EvaluationContext;
import de.useweb.backend.ocl.evaluation.OclEvaluator;
import de.useweb.backend.ocl.parser.OclParser;
import de.useweb.backend.ocl.typecheck.OclTypeChecker;
import de.useweb.backend.ocl.typecheck.TypeEnvironment;

class OclOptionalCompliancePolicyTest {

    @Test
    void rejectsNavigationWhenTheTargetAssociationEndIsNotNavigable() {
        UmlClassId sourceId = new UmlClassId("class-source");
        UmlClassId targetId = new UmlClassId("class-target");
        UmlClass sourceClass = new UmlClass(sourceId, "Source", List.of(), List.of());
        UmlClass targetClass = new UmlClass(targetId, "Target", List.of(), List.of());
        UmlAssociationEnd sourceEnd = new UmlAssociationEnd(new UmlAssociationEndId("end-source"), sourceId,
                "source", Multiplicity.exactlyOne(), true);
        UmlAssociationEnd hiddenTargetEnd = new UmlAssociationEnd(new UmlAssociationEndId("end-target"), targetId,
                "hiddenTargets", Multiplicity.zeroToMany(), false);
        UmlAssociation association = new UmlAssociation(new UmlAssociationId("association-hidden"), "Hidden",
                List.of(sourceEnd, hiddenTargetEnd));
        UmlModel model = new UmlModel(new UmlModelId("model-optional-compliance"), "Optional compliance",
                List.of(sourceClass, targetClass), List.of(association), List.of());
        ObjectInstance source = new ObjectInstance(new ObjectInstanceId("object-source"), "source1", sourceId,
                List.of());
        ObjectInstance target = new ObjectInstance(new ObjectInstanceId("object-target"), "target1", targetId,
                List.of());
        ObjectLink link = new ObjectLink(new ObjectLinkId("link-hidden"), association.id(), List.of(
                new ObjectLinkEnd(sourceEnd.id(), source.id()), new ObjectLinkEnd(hiddenTargetEnd.id(), target.id())));
        ObjectModel snapshot = new ObjectModel(new ObjectModelId("snapshot-optional-compliance"), "Snapshot",
                List.of(source, target), List.of(link));
        var expression = new OclParser().parse("self.hiddenTargets").ast();

        var checked = new OclTypeChecker().checkExpression(new TypeEnvironment(model, sourceClass), expression);
        var evaluated = new OclEvaluator().evaluate(expression, new EvaluationContext(model, snapshot, source));

        assertThat(checked.success()).isFalse();
        assertThat(checked.diagnostics()).extracting("code").contains("UNKNOWN_ATTRIBUTE");
        assertThat(evaluated.success()).isFalse();
        assertThat(evaluated.diagnostics()).anySatisfy(diagnostic ->
                assertThat(diagnostic.message()).contains("cannot be evaluated"));
    }
}
