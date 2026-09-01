package de.useweb.backend.application.snapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import de.useweb.backend.api.dto.project.ProjectDto;
import de.useweb.backend.api.dto.command.AssociationClassInstanceAggregateDto;
import de.useweb.backend.api.dto.command.AssociationClassInstanceDraftDto;
import de.useweb.backend.api.dto.snapshot.ObjectInstanceDto;
import de.useweb.backend.api.dto.snapshot.ObjectLinkDto;
import de.useweb.backend.api.dto.snapshot.ObjectLinkEndValueDto;
import de.useweb.backend.api.dto.snapshot.ObjectModelDto;
import de.useweb.backend.api.dto.snapshot.SlotDto;
import de.useweb.backend.api.dto.snapshot.SlotValueDto;
import de.useweb.backend.api.mapper.ProjectDtoMapper;
import de.useweb.backend.application.project.ProjectService;
import de.useweb.backend.application.uml.StructuredUmlTypeService;
import de.useweb.backend.domain.layout.DiagramLayout;
import de.useweb.backend.domain.layout.LayoutInformation;
import de.useweb.backend.domain.project.Project;
import de.useweb.backend.domain.project.ProjectId;
import de.useweb.backend.domain.snapshot.ObjectInstance;
import de.useweb.backend.domain.snapshot.ObjectInstanceId;
import de.useweb.backend.domain.snapshot.ObjectLink;
import de.useweb.backend.domain.snapshot.ObjectLinkEnd;
import de.useweb.backend.domain.snapshot.ObjectLinkId;
import de.useweb.backend.domain.snapshot.ObjectModel;
import de.useweb.backend.domain.snapshot.Slot;
import de.useweb.backend.domain.snapshot.SlotId;
import de.useweb.backend.domain.snapshot.SlotValue;
import de.useweb.backend.domain.uml.PrimitiveType;
import de.useweb.backend.domain.uml.UmlAssociation;
import de.useweb.backend.domain.uml.UmlAssociationEnd;
import de.useweb.backend.domain.uml.UmlAssociationEndId;
import de.useweb.backend.domain.uml.UmlAssociationId;
import de.useweb.backend.domain.uml.UmlAttribute;
import de.useweb.backend.domain.uml.UmlAttributeId;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.error.ObjectModelException;
import de.useweb.backend.ocl.definition.OclDefinitionEvaluationException;
import de.useweb.backend.ocl.definition.OclDefinitionService;
import de.useweb.backend.ocl.definition.OclProjectDefinitionFactory;
import de.useweb.backend.ocl.value.BooleanValue;
import de.useweb.backend.ocl.value.EnumValue;
import de.useweb.backend.ocl.value.IntegerValue;
import de.useweb.backend.ocl.value.OclValue;
import de.useweb.backend.ocl.value.RealValue;
import de.useweb.backend.ocl.value.StringValue;

@Service
public class ObjectModelService {

    private static final String TYPE_ERROR = "TYPE_ERROR";
    private static final String UNKNOWN_CLASS = "UNKNOWN_CLASS";
    private static final String UNKNOWN_ATTRIBUTE = "UNKNOWN_ATTRIBUTE";
    private static final String INVALID_SLOT_VALUE = "INVALID_SLOT_VALUE";
    private static final String INVALID_LINK = "INVALID_LINK";

    private final ProjectService projectService;
    private final StructuredUmlTypeService structuredTypes = new StructuredUmlTypeService();

    public ObjectModelService(ProjectService projectService) {
        this.projectService = projectService;
    }

    public ObjectModelDto getCurrentSnapshot(ProjectId projectId) {
        return ProjectDtoMapper.toDto(projectService.loadProject(projectId).objectModel());
    }

    public ObjectInstanceDto createObject(ProjectId projectId, ObjectInstanceDto input) {
        Project project = projectService.loadProject(projectId);
        ObjectModel objectModel = project.objectModel();
        ObjectInstance object = validatedObject(project, objectModel, input, null, null);

        ObjectModel updatedObjectModel = new ObjectModel(
                objectModel.id(),
                objectModel.name(),
                append(objectModel.objects(), object),
                objectModel.links());
        save(project, updatedObjectModel);
        return ProjectDtoMapper.toDto(object);
    }

    public AssociationClassInstanceAggregateDto createAssociationClassInstance(ProjectId projectId,
            AssociationClassInstanceDraftDto input) {
        Project project = projectService.loadProject(projectId);
        ObjectModel current = project.objectModel();
        AssociationClassAggregateParts parts = validatedAssociationClassAggregate(
                project, current, input, null, null);
        ObjectModel updated = new ObjectModel(current.id(), current.name(),
                append(current.objects(), parts.object()), append(current.links(), parts.link()));
        validateLinkCardinality(project.umlModel(), updated.links(), parts.link());
        validateComposition(project.umlModel(), updated.links());
        save(project, updated);
        return new AssociationClassInstanceAggregateDto(
                ProjectDtoMapper.toDto(parts.link()), ProjectDtoMapper.toDto(parts.object()));
    }

    public AssociationClassInstanceAggregateDto updateAssociationClassInstance(ProjectId projectId,
            ObjectLinkId linkId, AssociationClassInstanceDraftDto input) {
        Project project = projectService.loadProject(projectId);
        ObjectModel current = project.objectModel();
        ObjectLink existingLink = requireLink(current, linkId);
        if (existingLink.associationClassObjectId() == null) {
            throw associationClassIdentityError(existingLink.associationId().value(), null,
                    "The selected object link has no association-class identity");
        }
        ObjectInstanceId objectId = existingLink.associationClassObjectId();
        requireObject(current, objectId);
        AssociationClassAggregateParts parts = validatedAssociationClassAggregate(
                project, current, input, linkId, objectId);
        List<ObjectInstance> objects = current.objects().stream()
                .map(candidate -> candidate.id().equals(objectId) ? parts.object() : candidate).toList();
        List<ObjectLink> links = current.links().stream()
                .map(candidate -> candidate.id().equals(linkId) ? parts.link() : candidate).toList();
        validateLinkCardinality(project.umlModel(), links, parts.link());
        validateComposition(project.umlModel(), links);
        save(project, new ObjectModel(current.id(), current.name(), objects, links));
        return new AssociationClassInstanceAggregateDto(
                ProjectDtoMapper.toDto(parts.link()), ProjectDtoMapper.toDto(parts.object()));
    }

    public ProjectDto deleteObject(ProjectId projectId, ObjectInstanceId objectId) {
        Project project = projectService.loadProject(projectId);
        List<String> dependentLinkIds = project.objectModel().links().stream()
                .filter(link -> link.ends().stream().anyMatch(end -> end.objectId().equals(objectId))
                        || objectId.equals(link.associationClassObjectId()))
                .map(link -> link.id().value()).toList();
        if (!dependentLinkIds.isEmpty()) {
            throw error("DELETE_BLOCKED", "Object is still referenced by object links",
                    "Verbleibende Objektlinks blockieren das direkte Loeschen.",
                    Map.of("objectId", objectId.value(), "linkIds", dependentLinkIds));
        }
        return deleteObjectWithDependencies(projectId, objectId);
    }

    public ProjectDto deleteObjectWithDependencies(ProjectId projectId, ObjectInstanceId objectId) {
        Project project = projectService.loadProject(projectId);
        ObjectModel objectModel = project.objectModel();
        requireObject(objectModel, objectId);

        Set<ObjectInstanceId> removedObjectIds = compositeDeletionClosure(project.umlModel(), objectModel, objectId);
        objectModel.links().stream()
                .filter(link -> link.associationClassObjectId() != null && removedObjectIds.contains(link.associationClassObjectId()))
                .forEach(link -> removedObjectIds.add(link.associationClassObjectId()));

        List<ObjectInstance> objects = objectModel.objects().stream()
                .filter(object -> !removedObjectIds.contains(object.id()))
                .toList();
        List<ObjectLink> links = objectModel.links().stream()
                .filter(link -> link.ends().stream().noneMatch(end -> removedObjectIds.contains(end.objectId())))
                .filter(link -> link.associationClassObjectId() == null
                        || !removedObjectIds.contains(link.associationClassObjectId()))
                .toList();
        Set<String> remainingLinkIds = links.stream()
                .map(link -> link.id().value())
                .collect(java.util.stream.Collectors.toSet());
        Set<String> removedLinkIds = objectModel.links().stream()
                .map(link -> link.id().value())
                .filter(linkId -> !remainingLinkIds.contains(linkId))
                .collect(java.util.stream.Collectors.toSet());
        Project saved = saveProject(
                project,
                new ObjectModel(objectModel.id(), objectModel.name(), objects, links),
                pruneLayout(project.layout(), removedObjectIds.stream().map(ObjectInstanceId::value)
                        .collect(java.util.stream.Collectors.toSet()), removedLinkIds));
        return ProjectDtoMapper.toDto(saved);
    }

    public SlotDto setSlotValue(ProjectId projectId, ObjectInstanceId objectId, SlotDto input) {
        Project project = projectService.loadProject(projectId);
        UmlModel umlModel = project.umlModel();
        ObjectModel objectModel = project.objectModel();
        ObjectInstance object = requireObject(objectModel, objectId);
        UmlClass umlClass = requireClass(umlModel, object.classId());
        Slot slot = slotFromDto(input, umlModel, umlClass);

        List<ObjectInstance> objects = objectModel.objects().stream()
                .map(currentObject -> currentObject.id().equals(objectId)
                        ? new ObjectInstance(currentObject.id(), currentObject.name(), currentObject.classId(), replaceSlot(currentObject.slots(), slot))
                        : currentObject)
                .toList();
        save(project, new ObjectModel(objectModel.id(), objectModel.name(), objects, objectModel.links()));
        return ProjectDtoMapper.toDto(slot);
    }

    public ObjectLinkDto createObjectLink(ProjectId projectId, ObjectLinkDto input) {
        Project project = projectService.loadProject(projectId);
        UmlModel umlModel = project.umlModel();
        ObjectModel objectModel = project.objectModel();
        ObjectLink link = validatedLink(umlModel, objectModel, input, null);

        List<ObjectLink> updatedLinks = append(objectModel.links(), link);
        validateLinkCardinality(umlModel, updatedLinks, link);
        validateComposition(umlModel, updatedLinks);
        save(project, new ObjectModel(objectModel.id(), objectModel.name(), objectModel.objects(), updatedLinks));
        return ProjectDtoMapper.toDto(link);
    }

    public ObjectLinkDto updateObjectLink(ProjectId projectId, ObjectLinkId linkId, ObjectLinkDto input) {
        Project project = projectService.loadProject(projectId);
        UmlModel umlModel = project.umlModel();
        ObjectModel objectModel = project.objectModel();
        requireLink(objectModel, linkId);
        ObjectLinkDto normalized = new ObjectLinkDto(linkId.value(), input.associationId(), input.endValues(),
                input.associationClassObjectId());
        ObjectLink replacement = validatedLink(umlModel, objectModel, normalized, linkId);
        List<ObjectLink> links = objectModel.links().stream()
                .map(link -> link.id().equals(linkId) ? replacement : link)
                .toList();
        validateLinkCardinality(umlModel, links, replacement);
        validateComposition(umlModel, links);
        save(project, new ObjectModel(objectModel.id(), objectModel.name(), objectModel.objects(), links));
        return ProjectDtoMapper.toDto(replacement);
    }

    private AssociationClassAggregateParts validatedAssociationClassAggregate(Project project,
            ObjectModel current, AssociationClassInstanceDraftDto input, ObjectLinkId linkId,
            ObjectInstanceId forcedObjectId) {
        if (input == null || input.link() == null || input.associationClassObject() == null) {
            throw error(TYPE_ERROR, "Association-class aggregate draft is incomplete",
                    "Der Entwurf fuer Linkobjekt und Objektlink ist unvollstaendig.",
                    Map.of("field", input == null || input.link() == null ? "link" : "associationClassObject"));
        }
        UmlAssociation association = requireAssociation(project.umlModel(),
                new UmlAssociationId(input.link().associationId()));
        if (association.associationClassId() == null) {
            throw associationClassIdentityError(association.id().value(), null,
                    "The selected association has no association class");
        }
        ObjectInstanceDto objectDraft = input.associationClassObject();
        if (!association.associationClassId().value().equals(objectDraft.classId())) {
            throw associationClassIdentityError(association.id().value(), objectDraft.id(),
                    "Association-class object has the wrong classifier");
        }
        if (forcedObjectId != null && objectDraft.id() != null && !objectDraft.id().isBlank()
                && !forcedObjectId.value().equals(objectDraft.id())) {
            throw associationClassIdentityError(association.id().value(), objectDraft.id(),
                    "Association-class object identity cannot change during update");
        }
        ObjectInstance object = validatedObject(project, current, objectDraft, forcedObjectId, forcedObjectId);
        ObjectModel withObject = new ObjectModel(current.id(), current.name(),
                forcedObjectId == null ? append(current.objects(), object)
                        : current.objects().stream()
                                .map(candidate -> candidate.id().equals(forcedObjectId) ? object : candidate).toList(),
                current.links());
        ObjectLinkDto linkDraft = input.link();
        if (linkDraft.associationClassObjectId() != null && !linkDraft.associationClassObjectId().isBlank()
                && !object.id().value().equals(linkDraft.associationClassObjectId())) {
            throw associationClassIdentityError(association.id().value(), linkDraft.associationClassObjectId(),
                    "Object-link identity does not match the aggregate object");
        }
        ObjectLinkDto normalized = new ObjectLinkDto(
                linkId == null ? linkDraft.id() : linkId.value(), linkDraft.associationId(),
                linkDraft.endValues(), object.id().value());
        ObjectLink link = validatedLink(project.umlModel(), withObject, normalized, linkId);
        return new AssociationClassAggregateParts(link, object);
    }

    private ObjectInstance validatedObject(Project project, ObjectModel objectModel, ObjectInstanceDto input,
            ObjectInstanceId forcedId, ObjectInstanceId ignoredId) {
        if (input == null || input.classId() == null || input.classId().isBlank()) {
            throw error(UNKNOWN_CLASS, "Object classifier is missing", "Der Classifier des Objekts fehlt.",
                    Map.of("field", "classId"));
        }
        UmlModel umlModel = project.umlModel();
        UmlClass umlClass = requireClass(umlModel, new UmlClassId(input.classId()));
        if (umlClass.abstractClass()) {
            throw error(TYPE_ERROR, "Cannot instantiate abstract class: " + umlClass.name(),
                    "Von einer abstrakten Klasse kann kein Objekt erzeugt werden.",
                    Map.of("classId", umlClass.id().value()));
        }
        String objectName = requireName(input.name(), "objectName");
        requireUniqueObjectName(objectModel, objectName, ignoredId);
        List<Slot> slots = initialSlots(umlModel, umlClass);
        for (SlotDto slotDto : safeList(input.slots())) {
            slots = replaceSlot(slots, slotFromDto(slotDto, umlModel, umlClass));
        }
        ObjectInstanceId objectId = forcedId == null
                ? new ObjectInstanceId(idOrGenerated(input.id(), "obj")) : forcedId;
        ObjectInstance draft = new ObjectInstance(objectId, objectName, umlClass.id(), slots);
        slots = applyInitValues(project, objectModel, draft, slots);
        return new ObjectInstance(objectId, objectName, umlClass.id(), slots);
    }

    private ObjectModelException associationClassIdentityError(String associationId, String objectId,
            String message) {
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("associationId", associationId);
        if (objectId != null && !objectId.isBlank()) details.put("objectId", objectId);
        return error("ASSOCIATION_CLASS_IDENTITY_VIOLATION", message,
                "Linkobjekt und Objektlink besitzen keine konsistente Association-Class-Identitaet.", details);
    }

    private record AssociationClassAggregateParts(ObjectLink link, ObjectInstance object) {
    }

    private ObjectLink validatedLink(UmlModel umlModel, ObjectModel objectModel, ObjectLinkDto input,
            ObjectLinkId ignoredLinkId) {
        UmlAssociation association = requireAssociation(umlModel, new UmlAssociationId(input.associationId()));
        List<ObjectLinkEndValueDto> endValues = safeList(input.endValues());
        if (endValues.size() != association.ends().size()
                || endValues.stream().map(ObjectLinkEndValueDto::associationEndId).distinct().count() != endValues.size()
                || !endValues.stream().map(ObjectLinkEndValueDto::associationEndId).collect(java.util.stream.Collectors.toSet())
                        .equals(association.ends().stream().map(end -> end.id().value()).collect(java.util.stream.Collectors.toSet()))) {
            throw error(INVALID_LINK, "Object link must bind every association end exactly once",
                    "Ein Objektlink muss jedes Assoziationsende genau einmal belegen.",
                    Map.of("associationId", association.id().value(), "endCount", endValues.size()));
        }

        Map<String, ObjectLinkEndValueDto> valuesByEnd = endValues.stream()
                .collect(java.util.stream.Collectors.toMap(ObjectLinkEndValueDto::associationEndId, value -> value));
        List<ObjectLinkEnd> ends = association.ends().stream()
                .map(end -> linkEnd(umlModel, association, objectModel, valuesByEnd.get(end.id().value())))
                .toList();
        ObjectLink link = new ObjectLink(
                new ObjectLinkId(idOrGenerated(input.id(), "link")),
                association.id(),
                ends,
                associationClassObject(umlModel, association, objectModel, input.associationClassObjectId(), ignoredLinkId));

        List<String> duplicateIds = objectModel.links().stream().filter(existing -> !existing.id().equals(ignoredLinkId))
                .filter(existing -> sameLink(existing, link)).map(existing -> existing.id().value()).toList();
        if (!duplicateIds.isEmpty() && association.ends().stream().anyMatch(UmlAssociationEnd::unique)) {
            throw error("OBJECT_LINK_DUPLICATE", "Duplicate object link violates unique association-end semantics",
                    "Die Endzuordnung ist in diesem Unique-Navigationsbereich bereits vorhanden.",
                    Map.of("associationId", association.id().value(), "conflictingLinkIds", duplicateIds));
        }
        return link;
    }

    private void validateLinkCardinality(UmlModel model, List<ObjectLink> links, ObjectLink changedLink) {
        UmlAssociation association = requireAssociation(model, changedLink.associationId());
        for (UmlAssociationEnd targetEnd : association.ends()) {
            Integer upper = targetEnd.multiplicity().upper();
            if (targetEnd.multiplicity().unbounded() || upper == null) continue;
            ObjectLinkEnd changedTarget = changedLink.ends().stream()
                    .filter(value -> value.associationEndId().equals(targetEnd.id())).findFirst().orElseThrow();
            List<ObjectLink> matching = links.stream().filter(link -> link.associationId().equals(association.id()))
                    .filter(link -> association.ends().stream().filter(end -> !end.id().equals(targetEnd.id()))
                            .allMatch(bindingEnd -> sameAssignedObject(link, changedLink, bindingEnd.id())))
                    .filter(link -> link.ends().stream().filter(value -> value.associationEndId().equals(targetEnd.id()))
                            .findFirst().map(value -> value.qualifierValues().equals(changedTarget.qualifierValues()))
                            .orElse(false))
                    .toList();
            if (matching.size() > upper) {
                throw error("MULTIPLICITY_VIOLATION", "Object-link update exceeds association-end upper bound",
                        "Die Endzuordnung ueberschreitet die obere Multiplizitaetsgrenze.",
                        Map.of("associationId", association.id().value(), "associationEndId", targetEnd.id().value(),
                                "expectedUpper", upper, "actualCount", matching.size(),
                                "linkIds", matching.stream().map(value -> value.id().value()).toList()));
            }
        }
    }

    private boolean sameAssignedObject(ObjectLink left, ObjectLink right, UmlAssociationEndId endId) {
        ObjectInstanceId leftObject = left.ends().stream().filter(value -> value.associationEndId().equals(endId))
                .map(ObjectLinkEnd::objectId).findFirst().orElse(null);
        ObjectInstanceId rightObject = right.ends().stream().filter(value -> value.associationEndId().equals(endId))
                .map(ObjectLinkEnd::objectId).findFirst().orElse(null);
        return java.util.Objects.equals(leftObject, rightObject);
    }

    public ProjectDto deleteObjectLink(ProjectId projectId, ObjectLinkId linkId) {
        Project project = projectService.loadProject(projectId);
        ObjectModel objectModel = project.objectModel();
        ObjectLink removed = requireLink(objectModel, linkId);
        if (removed.associationClassObjectId() != null) {
            List<String> dependentLinks = objectModel.links().stream()
                    .filter(link -> !link.id().equals(linkId))
                    .filter(link -> link.ends().stream().anyMatch(end -> end.objectId().equals(removed.associationClassObjectId()))
                            || removed.associationClassObjectId().equals(link.associationClassObjectId()))
                    .map(link -> link.id().value()).toList();
            if (!dependentLinks.isEmpty()) {
                throw error("DELETE_BLOCKED", "Association-class object is referenced by other links",
                        "Weitere Objektlinks referenzieren die Association-Class-Instanz.",
                        Map.of("linkId", linkId.value(), "associationClassObjectId",
                                removed.associationClassObjectId().value(), "blockingLinkIds", dependentLinks));
            }
        }
        List<ObjectLink> links = objectModel.links().stream()
                .filter(link -> !link.id().equals(linkId))
                .toList();
        Set<String> removedObjectIds = removed.associationClassObjectId() == null ? Set.of()
                : Set.of(removed.associationClassObjectId().value());
        List<ObjectInstance> objects = objectModel.objects().stream()
                .filter(object -> !removedObjectIds.contains(object.id().value())).toList();
        Project saved = saveProject(
                project,
                new ObjectModel(objectModel.id(), objectModel.name(), objects, links),
                pruneLayout(project.layout(), removedObjectIds, Set.of(linkId.value())));
        return ProjectDtoMapper.toDto(saved);
    }

    private List<Slot> initialSlots(UmlModel umlModel, UmlClass umlClass) {
        return umlModel.typeConformanceOrder(umlClass.id()).stream()
                .map(umlModel::findClass).flatMap(java.util.Optional::stream)
                .flatMap(type -> type.attributes().stream())
                .filter(attribute -> !attribute.derived() && !attribute.staticAttribute())
                .map(attribute -> new Slot(
                        new SlotId("slot-" + UUID.randomUUID()),
                        attribute.id(),
                        new SlotValue(null, attribute.type())))
                .toList();
    }

    private List<Slot> applyInitValues(Project project, ObjectModel snapshot, ObjectInstance draft,
            List<Slot> slots) {
        UmlModel model = project.umlModel();
        var definitions = new OclProjectDefinitionFactory().definitions(project);
        if (definitions.stream().noneMatch(definition ->
                definition.kind() == de.useweb.backend.ocl.definition.OclDefinitionKind.INIT)) {
            return slots;
        }
        try {
            Map<UmlAttributeId, OclValue> defaults = new OclDefinitionService(model, definitions)
                    .initialValues(model, snapshot, draft);
            List<Slot> initialized = slots;
            for (var entry : defaults.entrySet()) {
                Slot existing = initialized.stream()
                        .filter(slot -> slot.attributeId().equals(entry.getKey())).findFirst().orElse(null);
                if (existing != null && existing.value().value() == null) {
                    initialized = replaceSlot(initialized, new Slot(existing.id(), existing.attributeId(),
                            slotValue(entry.getValue(), existing.value().valueType())));
                }
            }
            return initialized;
        } catch (OclDefinitionEvaluationException exception) {
            throw error(exception.code(), exception.getMessage(),
                    "Ein Initialwert konnte nicht ausgewertet werden.", Map.of("objectName", draft.name()));
        }
    }

    private SlotValue slotValue(OclValue value, UmlType type) {
        Object raw = switch (value) {
            case StringValue string -> string.value();
            case IntegerValue integer -> integer.value();
            case RealValue real -> real.value();
            case BooleanValue bool -> bool.value();
            case EnumValue enumeration -> enumeration.literal();
            default -> throw new OclDefinitionEvaluationException("INIT_VALUE_UNSUPPORTED",
                    "Init expression produced unsupported value type '" + value.typeName() + "'.");
        };
        return new SlotValue(raw, type);
    }

    private Slot slotFromDto(SlotDto input, UmlModel umlModel, UmlClass umlClass) {
        if (input == null || input.attributeId() == null || input.attributeId().isBlank()) {
            throw error(UNKNOWN_ATTRIBUTE, "Slot attributeId is missing", "Der Slot referenziert kein Attribut.", Map.of());
        }
        UmlAttribute attribute = umlModel.typeConformanceOrder(umlClass.id()).stream()
                .map(umlModel::findClass).flatMap(java.util.Optional::stream)
                .map(type -> type.findAttribute(new UmlAttributeId(input.attributeId())))
                .flatMap(java.util.Optional::stream).findFirst()
                .orElseThrow(() -> error(UNKNOWN_ATTRIBUTE, "Unknown attribute for object class: " + input.attributeId(),
                        "Das Attribut gehört nicht zur Klasse des Objekts.",
                        Map.of("classId", umlClass.id().value(), "attributeId", input.attributeId())));
        if (attribute.derived()) {
            throw error(INVALID_SLOT_VALUE, "Derived attribute is read-only: " + attribute.name(),
                    "Abgeleitete Attribute können nicht direkt gesetzt werden.",
                    Map.of("attributeId", attribute.id().value(), "attributeName", attribute.name()));
        }
        if (attribute.staticAttribute()) {
            throw error("STATIC_FEATURE_HAS_NO_OBJECT_SLOT",
                    "Static attribute must not be represented by an object slot: " + attribute.name(),
                    "Statische Attribute besitzen keinen Objekt-Slot.",
                    Map.of("classId", umlClass.id().value(), "attributeId", attribute.id().value(),
                            "attributeName", attribute.name()));
        }
        SlotValue value = slotValue(input.value(), attribute, umlModel, umlClass);
        return new Slot(new SlotId(idOrGenerated(input.id(), "slot")), attribute.id(), value);
    }

    private SlotValue slotValue(SlotValueDto input, UmlAttribute attribute, UmlModel umlModel, UmlClass contextClass) {
        if (input == null) {
            return new SlotValue(null, attribute.type());
        }
        try {
            var expected = structuredTypes.resolve(umlModel, attribute.type().name(), contextClass, false);
            var actual = structuredTypes.resolve(umlModel, input.type(), contextClass, false);
            if (!expected.umlType().name().equals(actual.umlType().name())) {
                throw invalidSlotValue(attribute, input.type(), input.value());
            }
            Object normalized = structuredTypes.normalizeValue(input.value(), expected, "value");
            return new SlotValue(normalized, expected.umlType());
        } catch (StructuredUmlTypeService.TypeException exception) {
            throw error(INVALID_SLOT_VALUE, exception.getMessage(), "Der Slot-Wert passt nicht zum Attributtyp.",
                    Map.of("attributeId", attribute.id().value(), "attributeName", attribute.name(),
                            "expectedType", attribute.type().name(), "actualType",
                            input.type() == null ? "null" : input.type(), "fieldPath", exception.path(),
                            "reason", exception.reason(), "actualValue", String.valueOf(exception.actualValue())));
        }
    }

    private ObjectLinkEnd linkEnd(UmlModel umlModel, UmlAssociation association, ObjectModel objectModel,
            ObjectLinkEndValueDto input) {
        UmlAssociationEnd associationEnd = association.findEnd(new UmlAssociationEndId(input.associationEndId()))
                .orElseThrow(() -> error(INVALID_LINK, "Unknown association end: " + input.associationEndId(),
                        "Das Association-Ende existiert nicht.",
                        Map.of("associationId", association.id().value(), "associationEndId", input.associationEndId())));
        ObjectInstance object = requireObject(objectModel, new ObjectInstanceId(input.objectId()));
        if (!umlModel.isSubtypeOf(object.classId(), associationEnd.classId())) {
            throw error(INVALID_LINK, "Object class does not match association end",
                    "Das Objekt passt nicht zur Klasse des Association-Endes.",
                    Map.of(
                            "objectId", object.id().value(),
                            "objectClassId", object.classId().value(),
                            "associationEndId", associationEnd.id().value(),
                            "expectedClassId", associationEnd.classId().value()));
        }
        List<de.useweb.backend.api.dto.snapshot.QualifierValueDto> inputValues = safeList(input.qualifierValues());
        if (inputValues.size() != associationEnd.qualifiers().size()
                || inputValues.stream().map(de.useweb.backend.api.dto.snapshot.QualifierValueDto::qualifierId)
                        .distinct().count() != inputValues.size()) {
            throw error(INVALID_LINK, "Qualifier values must bind every qualifier exactly once",
                    "Für jedes Qualifierfeld muss genau ein Wert angegeben werden.",
                    Map.of("associationId", association.id().value(), "associationEndId", associationEnd.id().value()));
        }
        Map<String, de.useweb.backend.api.dto.snapshot.QualifierValueDto> valuesByQualifier = inputValues.stream()
                .collect(java.util.stream.Collectors.toMap(
                        de.useweb.backend.api.dto.snapshot.QualifierValueDto::qualifierId, value -> value));
        List<de.useweb.backend.domain.snapshot.QualifierValue> qualifierValues = associationEnd.qualifiers().stream()
                .map(definition -> qualifierValue(umlModel, association, associationEnd, definition,
                        valuesByQualifier.get(definition.id().value())))
                .toList();
        return new ObjectLinkEnd(associationEnd.id(), object.id(), qualifierValues);
    }

    private de.useweb.backend.domain.snapshot.QualifierValue qualifierValue(UmlModel model,
            UmlAssociation association, UmlAssociationEnd associationEnd,
            de.useweb.backend.domain.uml.UmlQualifierDefinition definition,
            de.useweb.backend.api.dto.snapshot.QualifierValueDto input) {
        if (input == null || input.value() == null || !definition.id().value().equals(input.qualifierId())) {
            throw error(INVALID_LINK, "Missing qualifier value for '" + definition.name() + "'",
                    "Ein erforderlicher Qualifierwert fehlt.",
                    Map.of("associationId", association.id().value(), "associationEndId", associationEnd.id().value(),
                            "qualifierId", definition.id().value(), "qualifierName", definition.name()));
        }
        SlotValue value = typedQualifierValue(model, definition, input.value());
        return new de.useweb.backend.domain.snapshot.QualifierValue(definition.id(), value);
    }

    private SlotValue typedQualifierValue(UmlModel model,
            de.useweb.backend.domain.uml.UmlQualifierDefinition definition,
            de.useweb.backend.api.dto.snapshot.SlotValueDto input) {
        if (!definition.type().name().equals(input.type())) {
            throw error(INVALID_LINK, "Qualifier value type does not match qualifier definition",
                    "Der Qualifierwert besitzt nicht den erwarteten Typ.",
                    Map.of("qualifierId", definition.id().value(), "expectedType", definition.type().name(),
                            "actualType", input.type()));
        }
        Object value = input.value();
        boolean valid = switch (definition.type().primitiveType().orElse(null)) {
            case STRING -> value instanceof String;
            case INTEGER -> value instanceof Integer || value instanceof Long;
            case REAL -> value instanceof Double || value instanceof Float || value instanceof Integer || value instanceof Long;
            case BOOLEAN -> value instanceof Boolean;
            case null -> model.findEnumerationByName(definition.type().name())
                    .map(enumeration -> value instanceof String literal && enumeration.containsLiteral(literal))
                    .orElse(false);
        };
        if (!valid) {
            throw error(INVALID_LINK, "Invalid qualifier value for '" + definition.name() + "'",
                    "Der Qualifierwert passt nicht zum deklarierten Typ.",
                    Map.of("qualifierId", definition.id().value(), "expectedType", definition.type().name()));
        }
        Object normalized = definition.type().equals(UmlType.INTEGER) && value instanceof Long number
                ? Math.toIntExact(number) : value;
        if (definition.type().equals(UmlType.REAL) && value instanceof Number number) normalized = number.doubleValue();
        return new SlotValue(normalized, definition.type());
    }

    private UmlClass requireClass(UmlModel model, UmlClassId classId) {
        return model.findClass(classId)
                .orElseThrow(() -> error(UNKNOWN_CLASS, "Unknown class: " + classId.value(), "Die referenzierte Klasse existiert nicht.",
                        Map.of("classId", classId.value())));
    }

    private UmlAssociation requireAssociation(UmlModel model, UmlAssociationId associationId) {
        return model.findAssociation(associationId)
                .orElseThrow(() -> error(INVALID_LINK, "Unknown association: " + associationId.value(), "Die referenzierte Assoziation existiert nicht.",
                        Map.of("associationId", associationId.value())));
    }

    private ObjectInstance requireObject(ObjectModel objectModel, ObjectInstanceId objectId) {
        return objectModel.findObject(objectId)
                .orElseThrow(() -> error(INVALID_LINK, "Unknown object: " + objectId.value(), "Das referenzierte Objekt existiert nicht.",
                        Map.of("objectId", objectId.value())));
    }

    private ObjectLink requireLink(ObjectModel objectModel, ObjectLinkId linkId) {
        return objectModel.findLink(linkId)
                .orElseThrow(() -> error(INVALID_LINK, "Unknown object link: " + linkId.value(), "Der referenzierte Objektlink existiert nicht.",
                        Map.of("linkId", linkId.value())));
    }

    private void requireUniqueObjectName(ObjectModel objectModel, String name, ObjectInstanceId ignoredObjectId) {
        boolean duplicate = objectModel.objects().stream()
                .anyMatch(object -> object.name().equals(name) && !object.id().equals(ignoredObjectId));
        if (duplicate) {
            throw error(TYPE_ERROR, "Duplicate object name: " + name, "Der Objektname ist im Snapshot bereits vorhanden.",
                    Map.of("objectName", name));
        }
    }

    private boolean sameLink(ObjectLink left, ObjectLink right) {
        return left.associationId().equals(right.associationId())
                && left.ends().equals(right.ends());
    }

    private ObjectInstanceId associationClassObject(UmlModel model, UmlAssociation association,
            ObjectModel objectModel, String inputObjectId, ObjectLinkId ignoredLinkId) {
        if (association.associationClassId() == null) {
            if (inputObjectId != null && !inputObjectId.isBlank()) {
                throw error(INVALID_LINK, "A normal association cannot bind an association-class object",
                        "Diese Assoziation besitzt keine Association Class.",
                        Map.of("associationId", association.id().value()));
            }
            return null;
        }
        if (inputObjectId == null || inputObjectId.isBlank()) {
            throw error(INVALID_LINK, "Association-class link requires an object identity",
                    "Für den Link fehlt die Instanz der Association Class.",
                    Map.of("associationId", association.id().value(),
                            "associationClassId", association.associationClassId().value()));
        }
        ObjectInstance object = requireObject(objectModel, new ObjectInstanceId(inputObjectId));
        if (!model.isSubtypeOf(object.classId(), association.associationClassId())) {
            throw error(INVALID_LINK, "Association-class object has the wrong classifier",
                    "Das Linkobjekt besitzt nicht die verknüpfte Association Class.",
                    Map.of("objectId", inputObjectId, "expectedClassId", association.associationClassId().value()));
        }
        if (objectModel.links().stream().filter(link -> !link.id().equals(ignoredLinkId))
                .anyMatch(link -> object.id().equals(link.associationClassObjectId()))) {
            throw error(INVALID_LINK, "Association-class object already identifies another link",
                    "Das Linkobjekt ist bereits mit einem anderen Link verbunden.", Map.of("objectId", inputObjectId));
        }
        return object.id();
    }

    private void validateComposition(UmlModel model, List<ObjectLink> links) {
        Map<ObjectInstanceId, ObjectInstanceId> owners = new java.util.HashMap<>();
        Map<ObjectInstanceId, Set<ObjectInstanceId>> graph = new java.util.HashMap<>();
        for (ObjectLink link : links) {
            UmlAssociation association = requireAssociation(model, link.associationId());
            UmlAssociationEnd wholeEnd = association.ends().stream()
                    .filter(end -> end.aggregationKind() == de.useweb.backend.domain.uml.AggregationKind.COMPOSITE)
                    .findFirst().orElse(null);
            if (wholeEnd == null) continue;
            ObjectInstanceId whole = link.ends().stream().filter(end -> end.associationEndId().equals(wholeEnd.id()))
                    .map(ObjectLinkEnd::objectId).findFirst().orElseThrow();
            for (ObjectLinkEnd partEnd : link.ends()) {
                if (partEnd.associationEndId().equals(wholeEnd.id())) continue;
                ObjectInstanceId previous = owners.putIfAbsent(partEnd.objectId(), whole);
                if (previous != null && !previous.equals(whole)) {
                    throw error("COMPOSITE_OWNERSHIP_VIOLATION", "Composite part has more than one owner",
                            "Ein Teilobjekt darf nur genau einem Composite-Ganzen gehören.",
                            Map.of("partObjectId", partEnd.objectId().value(), "existingWholeObjectId", previous.value(),
                                    "requestedWholeObjectId", whole.value()));
                }
                graph.computeIfAbsent(whole, ignored -> new java.util.HashSet<>()).add(partEnd.objectId());
            }
        }
        for (ObjectInstanceId origin : graph.keySet()) {
            if (reaches(origin, origin, graph, new java.util.HashSet<>(), false)) {
                throw error("COMPOSITION_CYCLE", "Composition graph contains a cycle",
                        "Composition-Beziehungen dürfen keinen Zyklus bilden.", Map.of("objectId", origin.value()));
            }
        }
    }

    private boolean reaches(ObjectInstanceId origin, ObjectInstanceId current,
            Map<ObjectInstanceId, Set<ObjectInstanceId>> graph, Set<ObjectInstanceId> visited, boolean moved) {
        if (moved && current.equals(origin)) return true;
        if (!visited.add(current) && !current.equals(origin)) return false;
        return graph.getOrDefault(current, Set.of()).stream()
                .anyMatch(next -> reaches(origin, next, graph, new java.util.HashSet<>(visited), true));
    }

    private Set<ObjectInstanceId> compositeDeletionClosure(UmlModel model, ObjectModel objectModel,
            ObjectInstanceId root) {
        Set<ObjectInstanceId> result = new java.util.LinkedHashSet<>();
        result.add(root);
        boolean changed;
        do {
            changed = false;
            for (ObjectLink link : objectModel.links()) {
                UmlAssociation association = requireAssociation(model, link.associationId());
                UmlAssociationEnd wholeEnd = association.ends().stream()
                        .filter(end -> end.aggregationKind() == de.useweb.backend.domain.uml.AggregationKind.COMPOSITE)
                        .findFirst().orElse(null);
                if (wholeEnd == null) continue;
                boolean removesWhole = link.ends().stream().anyMatch(end -> end.associationEndId().equals(wholeEnd.id())
                        && result.contains(end.objectId()));
                if (removesWhole) {
                    for (ObjectLinkEnd end : link.ends()) {
                        if (!end.associationEndId().equals(wholeEnd.id())) changed |= result.add(end.objectId());
                    }
                    if (link.associationClassObjectId() != null) changed |= result.add(link.associationClassObjectId());
                }
            }
        } while (changed);
        return result;
    }

    private List<Slot> replaceSlot(List<Slot> slots, Slot replacement) {
        List<Slot> updatedSlots = new ArrayList<>();
        boolean replaced = false;
        for (Slot slot : slots) {
            if (slot.attributeId().equals(replacement.attributeId())) {
                updatedSlots.add(replacement);
                replaced = true;
            } else {
                updatedSlots.add(slot);
            }
        }
        if (!replaced) {
            updatedSlots.add(replacement);
        }
        return List.copyOf(updatedSlots);
    }

    private String requireName(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw error(TYPE_ERROR, "Required field is blank: " + fieldName, "Ein Pflichtfeld ist leer.",
                    Map.of("field", fieldName));
        }
        return value.trim();
    }

    private ObjectModel save(Project project, ObjectModel updatedObjectModel) {
        Project saved = saveProject(project, updatedObjectModel, project.layout());
        return saved.objectModel();
    }

    private Project saveProject(Project project, ObjectModel updatedObjectModel, LayoutInformation updatedLayout) {
        return projectService.saveProject(new Project(
                project.id(),
                project.metadata(),
                project.modelText(),
                project.umlModel(),
                updatedObjectModel,
                updatedLayout,
                project.definitions()));
    }

    private LayoutInformation pruneLayout(LayoutInformation layout, Set<String> removedNodeIds, Set<String> removedEdgeIds) {
        return new LayoutInformation(
                pruneDiagramLayout(layout.classDiagram(), removedNodeIds, removedEdgeIds),
                pruneDiagramLayout(layout.objectDiagram(), removedNodeIds, removedEdgeIds));
    }

    private DiagramLayout pruneDiagramLayout(DiagramLayout layout, Set<String> removedNodeIds, Set<String> removedEdgeIds) {
        return new DiagramLayout(
                layout.nodes().stream().filter(node -> !removedNodeIds.contains(node.elementId())).toList(),
                layout.edges().stream().filter(edge -> !removedEdgeIds.contains(edge.elementId())).toList(),
                layout.viewport());
    }

    private <T> List<T> append(List<T> existing, T element) {
        List<T> elements = new ArrayList<>(existing);
        elements.add(element);
        return List.copyOf(elements);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String idOrGenerated(String id, String prefix) {
        return id == null || id.isBlank() ? prefix + "-" + UUID.randomUUID() : id;
    }

    private ObjectModelException invalidSlotValue(UmlAttribute attribute, String actualType, Object actualValue) {
        return error(INVALID_SLOT_VALUE, "Invalid slot value for attribute: " + attribute.name(),
                "Der Slot-Wert passt nicht zum Attributtyp.",
                Map.of(
                        "attributeId", attribute.id().value(),
                        "attributeName", attribute.name(),
                        "expectedType", attribute.type().name(),
                        "actualType", actualType == null ? "null" : actualType,
                        "actualValue", actualValue == null ? "null" : actualValue));
    }

    private ObjectModelException error(String code, String message, String userMessage, Map<String, Object> details) {
        return new ObjectModelException(code, message, userMessage, details);
    }
}
