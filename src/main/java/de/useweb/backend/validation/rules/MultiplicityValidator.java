package de.useweb.backend.validation.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.useweb.backend.domain.snapshot.ObjectInstance;
import de.useweb.backend.domain.snapshot.ObjectInstanceId;
import de.useweb.backend.domain.snapshot.ObjectLink;
import de.useweb.backend.domain.snapshot.ObjectModel;
import de.useweb.backend.domain.uml.UmlAssociation;
import de.useweb.backend.domain.uml.UmlAssociationEnd;
import de.useweb.backend.domain.uml.UmlAssociationEndId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.validation.ElementTarget;
import de.useweb.backend.domain.validation.ElementType;
import de.useweb.backend.domain.validation.ValidationError;
import de.useweb.backend.domain.validation.ValidationErrorCode;
import de.useweb.backend.validation.result.ValidationErrorFactory;

public class MultiplicityValidator {

    public List<ValidationError> validate(UmlModel umlModel, ObjectModel objectModel,
            ValidationErrorFactory errorFactory) {
        List<ValidationError> errors = new ArrayList<>();
        for (UmlAssociation association : umlModel.associations()) {
            for (UmlAssociationEnd targetEnd : association.ends()) {
                List<UmlAssociationEnd> bindingEnds = association.ends().stream()
                        .filter(end -> !end.id().equals(targetEnd.id())).toList();
                for (Map<UmlAssociationEndId, ObjectInstance> binding : bindings(umlModel, objectModel, bindingEnds)) {
                    List<ObjectLink> links = matchingLinks(objectModel, association, binding, targetEnd);
                    validateCount(association, targetEnd, binding, links, errorFactory, errors);
                }
            }
        }
        return errors;
    }

    private List<Map<UmlAssociationEndId, ObjectInstance>> bindings(UmlModel umlModel, ObjectModel objectModel,
            List<UmlAssociationEnd> ends) {
        List<Map<UmlAssociationEndId, ObjectInstance>> result = new ArrayList<>();
        buildBindings(umlModel, objectModel, ends, 0, new LinkedHashMap<>(), result);
        return result;
    }

    private void buildBindings(UmlModel umlModel, ObjectModel objectModel, List<UmlAssociationEnd> ends,
            int index, Map<UmlAssociationEndId, ObjectInstance> current,
            List<Map<UmlAssociationEndId, ObjectInstance>> result) {
        if (index == ends.size()) {
            result.add(Map.copyOf(current));
            return;
        }
        UmlAssociationEnd end = ends.get(index);
        for (ObjectInstance object : objectModel.objects()) {
            if (umlModel.isSubtypeOf(object.classId(), end.classId())) {
                current.put(end.id(), object);
                buildBindings(umlModel, objectModel, ends, index + 1, current, result);
                current.remove(end.id());
            }
        }
    }

    private List<ObjectLink> matchingLinks(ObjectModel objectModel, UmlAssociation association,
            Map<UmlAssociationEndId, ObjectInstance> binding, UmlAssociationEnd targetEnd) {
        return objectModel.links().stream()
                .filter(link -> link.associationId().equals(association.id()))
                .filter(link -> binding.entrySet().stream().allMatch(entry -> link.ends().stream().anyMatch(end ->
                        end.associationEndId().equals(entry.getKey()) && end.objectId().equals(entry.getValue().id()))))
                .filter(link -> link.ends().stream().anyMatch(end -> end.associationEndId().equals(targetEnd.id())))
                .toList();
    }

    private void validateCount(UmlAssociation association, UmlAssociationEnd targetEnd,
            Map<UmlAssociationEndId, ObjectInstance> binding, List<ObjectLink> links,
            ValidationErrorFactory errorFactory, List<ValidationError> errors) {
        if (targetEnd.qualifiers().isEmpty()) {
            addViolationIfNeeded(association, targetEnd, binding, links, errorFactory, errors, null);
            return;
        }
        Map<List<Object>, List<ObjectLink>> partitions = new LinkedHashMap<>();
        for (ObjectLink link : links) {
            var targetValue = link.ends().stream().filter(end -> end.associationEndId().equals(targetEnd.id())).findFirst();
            if (targetValue.isEmpty()) continue;
            List<Object> key = targetEnd.qualifiers().stream().map(definition -> targetValue.get().qualifierValues().stream()
                    .filter(value -> value.qualifierId().equals(definition.id())).findFirst()
                    .map(value -> value.value().value()).orElse(null)).toList();
            partitions.computeIfAbsent(key, ignored -> new ArrayList<>()).add(link);
        }
        partitions.forEach((qualifierValues, partitionLinks) -> addViolationIfNeeded(association, targetEnd,
                binding, partitionLinks, errorFactory, errors, qualifierValues));
    }

    private void addViolationIfNeeded(UmlAssociation association, UmlAssociationEnd targetEnd,
            Map<UmlAssociationEndId, ObjectInstance> binding, List<ObjectLink> links,
            ValidationErrorFactory errorFactory, List<ValidationError> errors, List<Object> qualifierValues) {
        int actualCount = links.size();
        if (targetEnd.multiplicity().contains(actualCount)) return;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("associationId", association.id().value());
        details.put("associationEndId", targetEnd.id().value());
        details.put("roleName", targetEnd.roleName());
        details.put("binding", binding.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                entry -> entry.getKey().value(), entry -> entry.getValue().id().value())));
        details.put("expectedMultiplicity", targetEnd.multiplicity().raw());
        details.put("actualCount", actualCount);
        details.put("linkIds", links.stream().map(link -> link.id().value()).toList());
        if (qualifierValues != null) details.put("qualifierValues", qualifierValues);
        errors.add(errorFactory.error(ValidationErrorCode.MULTIPLICITY_VIOLATION,
                "Role '" + targetEnd.roleName() + "' has " + actualCount + " linked objects for the bound "
                        + "association ends, but multiplicity is " + targetEnd.multiplicity().raw() + ".",
                targets(binding.values().stream().toList(), association, targetEnd, links), details));
    }

    private List<ElementTarget> targets(List<ObjectInstance> objects, UmlAssociation association,
            UmlAssociationEnd targetEnd, List<ObjectLink> links) {
        List<ElementTarget> targets = new ArrayList<>();
        objects.forEach(object -> targets.add(ElementTarget.object(object.id().value())));
        targets.add(new ElementTarget(ElementType.ASSOCIATION, association.id().value(), null));
        targets.add(new ElementTarget(ElementType.ASSOCIATION_END, targetEnd.id().value(), null));
        links.forEach(link -> targets.add(new ElementTarget(ElementType.OBJECT_LINK, link.id().value(), null)));
        return targets;
    }
}
