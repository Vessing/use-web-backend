package de.useweb.backend.ocl.definition;

import java.util.List;
import java.util.Optional;

import de.useweb.backend.ocl.evaluation.EvaluationContext;
import de.useweb.backend.ocl.value.ObjectValue;
import de.useweb.backend.ocl.value.OclValue;

public interface OclDefinitionRuntime {
    Optional<OclValue> property(ObjectValue receiver, String propertyName, EvaluationContext context);

    Optional<OclValue> operation(ObjectValue receiver, String operationName, List<OclValue> arguments,
            EvaluationContext context);

    default Optional<OclValue> property(OclDefinitionId definitionId, ObjectValue receiver,
            EvaluationContext context) {
        return property(receiver, "", context);
    }

    default Optional<OclValue> operation(OclDefinitionId definitionId, ObjectValue receiver,
            List<OclValue> arguments, EvaluationContext context) {
        return operation(receiver, "", arguments, context);
    }

    default Optional<OclValue> operationForFeature(String operationId, ObjectValue receiver,
            List<OclValue> arguments, EvaluationContext context) {
        return Optional.empty();
    }
}
