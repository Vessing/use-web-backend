package de.useweb.backend.ocl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlModelId;
import de.useweb.backend.ocl.parser.OclParser;
import de.useweb.backend.ocl.typecheck.OclTypeChecker;
import de.useweb.backend.ocl.typecheck.TypeEnvironment;

class OclExcludedBehavioralFeaturesTest {
    private final OclParser parser = new OclParser();

    @Test
    void rejectsOclInStateBecauseNoStateMachineRuntimeExists() {
        UmlClass contextClass = new UmlClass(new UmlClassId("class-context"), "Context", List.of(), List.of());
        UmlModel model = new UmlModel(new UmlModelId("model-context"), "Context model",
                List.of(contextClass), List.of(), List.of());
        var parsed = parser.parse("self.oclInState('Active')");

        var checked = new OclTypeChecker().checkExpression(new TypeEnvironment(model, contextClass), parsed.ast());

        assertThat(parsed.success()).isTrue();
        assertThat(checked.success()).isFalse();
        assertThat(checked.diagnostics()).extracting("code").contains("UNKNOWN_FEATURE");
    }

    @Test
    void rejectsBothMessageExpressionOperatorsWithStructuredParserDiagnostics() {
        var singleMessage = parser.parse("self^deposit()");
        var messageCollection = parser.parse("self^^deposit()");

        assertThat(singleMessage.success()).isFalse();
        assertThat(messageCollection.success()).isFalse();
        assertThat(singleMessage.diagnostics()).allSatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isNotBlank();
            assertThat(diagnostic.sourceRange()).isNotNull();
        });
        assertThat(messageCollection.diagnostics()).allSatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isNotBlank();
            assertThat(diagnostic.sourceRange()).isNotNull();
        });
    }
}
