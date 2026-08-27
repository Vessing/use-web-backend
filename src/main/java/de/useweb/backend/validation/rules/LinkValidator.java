package de.useweb.backend.validation.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import de.useweb.backend.domain.snapshot.ObjectLink;
import de.useweb.backend.domain.snapshot.ObjectModel;
import de.useweb.backend.domain.snapshot.ObjectInstanceId;
import de.useweb.backend.domain.uml.AggregationKind;
import de.useweb.backend.domain.uml.UmlAssociationEnd;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.validation.ElementTarget;
import de.useweb.backend.domain.validation.ElementType;
import de.useweb.backend.domain.validation.ValidationError;
import de.useweb.backend.domain.validation.ValidationErrorCode;
import de.useweb.backend.validation.result.ValidationErrorFactory;

public class LinkValidator {

    public List<ValidationError> validate(UmlModel umlModel, ObjectModel objectModel, ValidationErrorFactory errorFactory) {
        List<ValidationError> errors = new ArrayList<>();
        for (ObjectLink link : objectModel.links()) {
            var association = umlModel.findAssociation(link.associationId());
            if (association.isEmpty()) {
                errors.add(errorFactory.error(
                        ValidationErrorCode.INVALID_LINK,
                        "Object link '" + link.id().value() + "' references unknown association '" + link.associationId().value() + "'.",
                        List.of(new ElementTarget(ElementType.OBJECT_LINK, link.id().value(), null)),
                        Map.of("linkId", link.id().value(), "associationId", link.associationId().value())));
                continue;
            }
            Set<?> expectedEndIds = association.get().ends().stream().map(UmlAssociationEnd::id).collect(Collectors.toSet());
            Set<?> actualEndIds = link.ends().stream().map(end -> end.associationEndId()).collect(Collectors.toSet());
            if (link.ends().size() != association.get().ends().size() || !actualEndIds.equals(expectedEndIds)) {
                errors.add(errorFactory.error(ValidationErrorCode.INVALID_LINK,
                        "Object link '" + link.id().value() + "' must occupy every association end exactly once.",
                        List.of(new ElementTarget(ElementType.OBJECT_LINK, link.id().value(), null)),
                        Map.of("linkId", link.id().value(), "expectedEndCount", association.get().ends().size(),
                                "actualEndCount", link.ends().size())));
            }
            for (var endValue : link.ends()) {
                var associationEnd = association.get().findEnd(endValue.associationEndId());
                if (associationEnd.isEmpty()) {
                    errors.add(errorFactory.error(
                            ValidationErrorCode.INVALID_LINK,
                            "Object link '" + link.id().value() + "' references unknown association end '"
                                    + endValue.associationEndId().value() + "'.",
                            List.of(new ElementTarget(ElementType.OBJECT_LINK, link.id().value(), null)),
                            Map.of("linkId", link.id().value(), "associationEndId", endValue.associationEndId().value())));
                    continue;
                }
                validateLinkedObject(umlModel, objectModel, link, associationEnd.get(),
                        endValue.objectId().value(), errorFactory, errors);
                Set<?> expectedQualifierIds = associationEnd.get().qualifiers().stream()
                        .map(qualifier -> qualifier.id()).collect(Collectors.toSet());
                Set<?> actualQualifierIds = endValue.qualifierValues().stream()
                        .map(value -> value.qualifierId()).collect(Collectors.toSet());
                if (endValue.qualifierValues().size() != associationEnd.get().qualifiers().size()
                        || !actualQualifierIds.equals(expectedQualifierIds)) {
                    errors.add(errorFactory.error(ValidationErrorCode.INVALID_LINK,
                            "Association end '" + associationEnd.get().roleName()
                                    + "' requires every qualifier value exactly once.",
                            List.of(new ElementTarget(ElementType.OBJECT_LINK, link.id().value(), null),
                                    new ElementTarget(ElementType.ASSOCIATION_END, associationEnd.get().id().value(), null)),
                            Map.of("linkId", link.id().value(), "associationEndId", associationEnd.get().id().value(),
                                    "expectedQualifierCount", associationEnd.get().qualifiers().size(),
                                    "actualQualifierCount", endValue.qualifierValues().size())));
                }
                for (var qualifierValue : endValue.qualifierValues()) {
                    associationEnd.get().qualifiers().stream()
                            .filter(definition -> definition.id().equals(qualifierValue.qualifierId()))
                            .findFirst()
                            .filter(definition -> !definition.type().equals(qualifierValue.value().valueType()))
                            .ifPresent(definition -> errors.add(errorFactory.error(ValidationErrorCode.INVALID_LINK,
                                    "Qualifier '" + definition.name() + "' has an incompatible value type.",
                                    List.of(new ElementTarget(ElementType.OBJECT_LINK, link.id().value(), null)),
                                    Map.of("qualifierId", definition.id().value(),
                                            "expectedType", definition.type().name(),
                                            "actualType", qualifierValue.value().valueType().name()))));
                }
            }
            validateAssociationClassIdentity(umlModel, objectModel, link, association.get(), errorFactory, errors);
        }
        validateAssociationClassObjects(umlModel, objectModel, errorFactory, errors);
        validateComposition(umlModel, objectModel, errorFactory, errors);
        return errors;
    }

    private void validateAssociationClassObjects(UmlModel umlModel, ObjectModel objectModel,
            ValidationErrorFactory errorFactory, List<ValidationError> errors) {
        umlModel.associations().stream()
                .filter(association -> association.associationClassId() != null)
                .forEach(association -> objectModel.objects().stream()
                        .filter(object -> umlModel.isSubtypeOf(object.classId(), association.associationClassId()))
                        .filter(object -> objectModel.links().stream()
                                .filter(link -> object.id().equals(link.associationClassObjectId())).count() != 1)
                        .forEach(object -> errors.add(errorFactory.error(
                                ValidationErrorCode.ASSOCIATION_CLASS_IDENTITY_VIOLATION,
                                "Association-class object must identify exactly one object link.",
                                List.of(ElementTarget.object(object.id().value())),
                                Map.of("associationId", association.id().value(),
                                        "associationClassId", association.associationClassId().value(),
                                        "associationClassObjectId", object.id().value())))));
    }

    private void validateAssociationClassIdentity(UmlModel umlModel, ObjectModel objectModel, ObjectLink link,
            de.useweb.backend.domain.uml.UmlAssociation association, ValidationErrorFactory errorFactory,
            List<ValidationError> errors) {
        if (association.associationClassId() == null && link.associationClassObjectId() == null) return;
        var object = link.associationClassObjectId() == null ? java.util.Optional.<de.useweb.backend.domain.snapshot.ObjectInstance>empty()
                : objectModel.findObject(link.associationClassObjectId());
        boolean valid = association.associationClassId() != null && object.isPresent()
                && umlModel.isSubtypeOf(object.get().classId(), association.associationClassId())
                && objectModel.links().stream().filter(candidate -> link.associationClassObjectId() != null
                        && link.associationClassObjectId().equals(candidate.associationClassObjectId())).count() == 1;
        if (!valid) {
            errors.add(errorFactory.error(ValidationErrorCode.ASSOCIATION_CLASS_IDENTITY_VIOLATION,
                    "Association-class instance must identify exactly one compatible object link.",
                    List.of(new ElementTarget(ElementType.OBJECT_LINK, link.id().value(), null)),
                    Map.of("associationId", association.id().value(), "linkId", link.id().value(),
                            "associationClassId", association.associationClassId() == null ? "" : association.associationClassId().value(),
                            "associationClassObjectId", link.associationClassObjectId() == null ? "" : link.associationClassObjectId().value())));
        }
    }

    private void validateComposition(UmlModel umlModel, ObjectModel objectModel,
            ValidationErrorFactory errorFactory, List<ValidationError> errors) {
        Map<ObjectInstanceId, ObjectInstanceId> owners = new java.util.HashMap<>();
        Map<ObjectInstanceId, Set<ObjectInstanceId>> graph = new java.util.HashMap<>();
        for (ObjectLink link : objectModel.links()) {
            var association = umlModel.findAssociation(link.associationId()).orElse(null);
            if (association == null) continue;
            UmlAssociationEnd wholeEnd = association.ends().stream()
                    .filter(end -> end.aggregationKind() == AggregationKind.COMPOSITE).findFirst().orElse(null);
            if (wholeEnd == null) continue;
            ObjectInstanceId whole = link.ends().stream().filter(end -> end.associationEndId().equals(wholeEnd.id()))
                    .map(end -> end.objectId()).findFirst().orElse(null);
            if (whole == null) continue;
            for (var end : link.ends()) {
                if (end.associationEndId().equals(wholeEnd.id())) continue;
                ObjectInstanceId previous = owners.putIfAbsent(end.objectId(), whole);
                if (previous != null && !previous.equals(whole)) {
                    errors.add(errorFactory.error(ValidationErrorCode.COMPOSITE_OWNERSHIP_VIOLATION,
                            "Composite part has more than one owner.",
                            List.of(ElementTarget.object(end.objectId().value())),
                            Map.of("partObjectId", end.objectId().value(), "existingWholeObjectId", previous.value(),
                                    "additionalWholeObjectId", whole.value())));
                }
                graph.computeIfAbsent(whole, ignored -> new java.util.HashSet<>()).add(end.objectId());
            }
        }
        for (ObjectInstanceId origin : graph.keySet()) {
            if (compositionCycle(origin, origin, graph, new java.util.HashSet<>(), false)) {
                errors.add(errorFactory.error(ValidationErrorCode.COMPOSITION_CYCLE,
                        "Composition graph contains a cycle.", List.of(ElementTarget.object(origin.value())),
                        Map.of("objectId", origin.value())));
            }
        }
    }

    private boolean compositionCycle(ObjectInstanceId origin, ObjectInstanceId current,
            Map<ObjectInstanceId, Set<ObjectInstanceId>> graph, Set<ObjectInstanceId> path, boolean moved) {
        if (moved && current.equals(origin)) return true;
        if (!path.add(current)) return false;
        return graph.getOrDefault(current, Set.of()).stream().anyMatch(next ->
                compositionCycle(origin, next, graph, new java.util.HashSet<>(path), true));
    }

    private void validateLinkedObject(
            UmlModel umlModel, ObjectModel objectModel,
            ObjectLink link,
            UmlAssociationEnd associationEnd,
            String objectId,
            ValidationErrorFactory errorFactory,
            List<ValidationError> errors) {
        var object = objectModel.findObject(new de.useweb.backend.domain.snapshot.ObjectInstanceId(objectId));
        if (object.isEmpty()) {
            errors.add(errorFactory.error(
                    ValidationErrorCode.INVALID_LINK,
                    "Object link '" + link.id().value() + "' references missing object '" + objectId + "'.",
                    List.of(new ElementTarget(ElementType.OBJECT_LINK, link.id().value(), null)),
                    Map.of("linkId", link.id().value(), "objectId", objectId)));
            return;
        }
        if (!umlModel.isSubtypeOf(object.get().classId(), associationEnd.classId())) {
            errors.add(errorFactory.error(
                    ValidationErrorCode.INVALID_LINK,
                    "Object link '" + link.id().value() + "' connects object '" + object.get().name()
                            + "' to a role that expects class '" + associationEnd.classId().value() + "'.",
                    List.of(
                            new ElementTarget(ElementType.OBJECT_LINK, link.id().value(), null),
                            ElementTarget.object(object.get().id().value()),
                            new ElementTarget(ElementType.ASSOCIATION_END, associationEnd.id().value(), null)),
                    Map.of(
                            "linkId", link.id().value(),
                            "objectId", object.get().id().value(),
                            "expectedClassId", associationEnd.classId().value(),
                            "actualClassId", object.get().classId().value())));
        }
    }
}
