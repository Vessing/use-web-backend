package de.useweb.backend.validation.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.useweb.backend.domain.snapshot.ObjectInstance;
import de.useweb.backend.domain.snapshot.ObjectModel;
import de.useweb.backend.domain.snapshot.Slot;
import de.useweb.backend.domain.uml.UmlAttribute;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.validation.ElementTarget;
import de.useweb.backend.domain.validation.ElementType;
import de.useweb.backend.domain.validation.ValidationError;
import de.useweb.backend.domain.validation.ValidationErrorCode;
import de.useweb.backend.validation.result.ValidationErrorFactory;

public class SnapshotValidator {

    public List<ValidationError> validate(UmlModel umlModel, ObjectModel objectModel, ValidationErrorFactory errorFactory) {
        List<ValidationError> errors = new ArrayList<>();
        for (ObjectInstance object : objectModel.objects()) {
            var umlClass = umlModel.findClass(object.classId());
            if (umlClass.isEmpty()) {
                errors.add(errorFactory.error(
                        ValidationErrorCode.UNKNOWN_CLASS,
                        "Object '" + object.name() + "' references unknown class '" + object.classId().value() + "'.",
                        List.of(ElementTarget.object(object.id().value())),
                        Map.of("objectId", object.id().value(), "classId", object.classId().value())));
                continue;
            }
            if (umlClass.get().abstractClass()) {
                errors.add(errorFactory.error(
                        ValidationErrorCode.TYPE_ERROR,
                        "Object '" + object.name() + "' instantiates abstract class '" + umlClass.get().name() + "'.",
                        List.of(ElementTarget.object(object.id().value())),
                        Map.of("objectId", object.id().value(), "classId", object.classId().value())));
            }
            validateSlots(umlModel, object, umlClass.get(), errorFactory, errors);
        }
        return errors;
    }

    private void validateSlots(
            UmlModel umlModel, ObjectInstance object,
            UmlClass umlClass,
            ValidationErrorFactory errorFactory,
            List<ValidationError> errors) {
        for (Slot slot : object.slots()) {
            var attribute = umlModel.typeConformanceOrder(umlClass.id()).stream()
                    .map(umlModel::findClass).flatMap(java.util.Optional::stream)
                    .map(type -> type.findAttribute(slot.attributeId()))
                    .flatMap(java.util.Optional::stream).findFirst();
            if (attribute.isEmpty()) {
                errors.add(errorFactory.error(
                        ValidationErrorCode.UNKNOWN_ATTRIBUTE,
                        "Slot '" + slot.id().value() + "' references unknown attribute '" + slot.attributeId().value() + "'.",
                        List.of(
                                ElementTarget.object(object.id().value()),
                                new ElementTarget(ElementType.SLOT, slot.id().value(), null)),
                        Map.of(
                                "objectId", object.id().value(),
                                "slotId", slot.id().value(),
                                "attributeId", slot.attributeId().value())));
                continue;
            }
            validateSlotValue(umlModel, object, slot, attribute.get(), errorFactory, errors);
        }
    }

    private void validateSlotValue(
            UmlModel umlModel, ObjectInstance object,
            Slot slot,
            UmlAttribute attribute,
            ValidationErrorFactory errorFactory,
            List<ValidationError> errors) {
        if (!slot.value().valueType().equals(attribute.type())
                || !runtimeValueMatches(umlModel, attribute, slot.value().value())) {
            errors.add(errorFactory.error(
                    ValidationErrorCode.INVALID_SLOT_VALUE,
                    "Slot '" + attribute.name() + "' of object '" + object.name() + "' expects "
                            + attribute.type().name() + " but contains " + slot.value().valueType().name() + ".",
                    List.of(
                            ElementTarget.object(object.id().value()),
                            new ElementTarget(ElementType.SLOT, slot.id().value(), null),
                            new ElementTarget(ElementType.ATTRIBUTE, attribute.id().value(), null)),
                    Map.of(
                            "objectId", object.id().value(),
                            "slotId", slot.id().value(),
                            "attributeId", attribute.id().value(),
                            "expectedType", attribute.type().name(),
                            "actualType", slot.value().valueType().name())));
        }
    }

    private boolean runtimeValueMatches(UmlModel umlModel, UmlAttribute attribute, Object value) {
        if (value == null) return true;
        return attribute.type().primitiveType()
                .map(primitiveType -> switch (primitiveType) {
                    case STRING -> value instanceof String;
                    case INTEGER -> value instanceof Integer;
                    case REAL -> value instanceof Double;
                    case BOOLEAN -> value instanceof Boolean;
                })
                .orElseGet(() -> umlModel.findEnumerationByName(attribute.type().name())
                        .filter(enumeration -> value instanceof String literal && enumeration.containsLiteral(literal))
                        .isPresent());
    }
}
