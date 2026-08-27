package de.useweb.backend.validation.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.useweb.backend.domain.uml.UmlAssociationEnd;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.validation.ElementTarget;
import de.useweb.backend.domain.validation.ElementType;
import de.useweb.backend.domain.validation.ValidationError;
import de.useweb.backend.domain.validation.ValidationErrorCode;
import de.useweb.backend.validation.result.ValidationErrorFactory;

public class UmlStructureValidator {

    public List<ValidationError> validate(UmlModel umlModel, ValidationErrorFactory errorFactory) {
        List<ValidationError> errors = new ArrayList<>();
        validateAssociations(umlModel, errorFactory, errors);
        validateInvariants(umlModel, errorFactory, errors);
        return errors;
    }

    private void validateAssociations(UmlModel umlModel, ValidationErrorFactory errorFactory, List<ValidationError> errors) {
        for (var association : umlModel.associations()) {
            for (UmlAssociationEnd end : association.ends()) {
                if (umlModel.findClass(end.classId()).isEmpty()) {
                    errors.add(errorFactory.error(
                            ValidationErrorCode.UNKNOWN_CLASS,
                            "Association '" + association.name() + "' references unknown class '" + end.classId().value() + "'.",
                            List.of(
                                    new ElementTarget(ElementType.ASSOCIATION, association.id().value(), null),
                                    new ElementTarget(ElementType.ASSOCIATION_END, end.id().value(), null)),
                            Map.of(
                                    "associationId", association.id().value(),
                                    "associationEndId", end.id().value(),
                                    "classId", end.classId().value())));
                }
            }
        }
    }

    private void validateInvariants(UmlModel umlModel, ValidationErrorFactory errorFactory, List<ValidationError> errors) {
        for (var invariant : umlModel.invariants()) {
            if (umlModel.findClass(invariant.contextClassId()).isEmpty()) {
                errors.add(errorFactory.error(
                        ValidationErrorCode.UNKNOWN_CLASS,
                        "Invariant '" + invariant.name() + "' references unknown context class '" + invariant.contextClassId().value() + "'.",
                        List.of(ElementTarget.invariant(invariant.id().value())),
                        Map.of(
                                "invariantId", invariant.id().value(),
                                "contextClassId", invariant.contextClassId().value())));
            }
        }
    }
}
