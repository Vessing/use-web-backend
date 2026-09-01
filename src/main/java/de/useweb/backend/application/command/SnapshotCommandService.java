package de.useweb.backend.application.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import de.useweb.backend.api.dto.command.CommandElementReferenceDto;
import de.useweb.backend.api.dto.command.AssociationClassInstanceAggregateDto;
import de.useweb.backend.api.dto.command.AssociationClassInstanceCommandRequestDto;
import de.useweb.backend.api.dto.command.AssociationClassInstanceDraftDto;
import de.useweb.backend.api.dto.command.CreateObjectCommandRequestDto;
import de.useweb.backend.api.dto.command.CreateObjectLinkCommandRequestDto;
import de.useweb.backend.api.dto.command.DeleteCommandRequestDto;
import de.useweb.backend.api.dto.command.MutationResultDto;
import de.useweb.backend.api.dto.command.ObjectLinkDeleteImpactDto;
import de.useweb.backend.api.dto.command.UpdateObjectLinkCommandRequestDto;
import de.useweb.backend.api.dto.command.UpdateSlotCommandRequestDto;
import de.useweb.backend.api.dto.snapshot.ObjectInstanceDto;
import de.useweb.backend.api.dto.snapshot.ObjectLinkDto;
import de.useweb.backend.api.dto.snapshot.SlotDto;
import de.useweb.backend.application.project.ProjectService;
import de.useweb.backend.application.snapshot.ObjectModelService;
import de.useweb.backend.domain.project.Project;
import de.useweb.backend.domain.project.ProjectId;
import de.useweb.backend.domain.snapshot.ObjectInstanceId;
import de.useweb.backend.domain.snapshot.ObjectLinkId;
import de.useweb.backend.domain.uml.AggregationKind;
import de.useweb.backend.error.CommandException;
import de.useweb.backend.error.ObjectModelException;
import de.useweb.backend.error.ProjectNotFoundException;

@Service
public class SnapshotCommandService {

    private static final String SNAPSHOT = "SNAPSHOT";

    private final ProjectService projectService;
    private final ObjectModelService objectModelService;
    private final Map<String, Object> projectLocks = new ConcurrentHashMap<>();

    public SnapshotCommandService(ProjectService projectService, ObjectModelService objectModelService) {
        this.projectService = projectService;
        this.objectModelService = objectModelService;
    }

    public MutationResultDto createObject(ProjectId projectId, CreateObjectCommandRequestDto request) {
        ObjectInstanceDto draft = request == null ? null : request.draft();
        return execute(projectId, "CREATE_OBJECT", expectedRevision(request), draft,
                () -> objectModelService.createObject(projectId, requireDraft(draft)),
                (result, project) -> objectReferences(project, (ObjectInstanceDto) result));
    }

    public MutationResultDto updateSlot(ProjectId projectId, ObjectInstanceId objectId, String slotId,
            UpdateSlotCommandRequestDto request) {
        SlotDto draft = request == null ? null : request.draft();
        return execute(projectId, "UPDATE_SLOT", expectedRevision(request), draft,
                () -> objectModelService.setSlotValue(projectId, objectId,
                        normalizedSlot(slotId, requireDraft(draft))),
                (result, project) -> slotReferences(project, objectId.value(), (SlotDto) result));
    }

    public MutationResultDto createObjectLink(ProjectId projectId, CreateObjectLinkCommandRequestDto request) {
        ObjectLinkDto draft = request == null ? null : request.draft();
        return execute(projectId, "CREATE_OBJECT_LINK", expectedRevision(request), draft,
                () -> objectModelService.createObjectLink(projectId, requireDraft(draft)),
                (result, project) -> linkReferences(project, (ObjectLinkDto) result));
    }

    public MutationResultDto updateObjectLink(ProjectId projectId, ObjectLinkId linkId,
            UpdateObjectLinkCommandRequestDto request) {
        ObjectLinkDto draft = request == null ? null : request.draft();
        return execute(projectId, "UPDATE_OBJECT_LINK", request == null ? null : request.expectedRevision(), draft,
                () -> objectModelService.updateObjectLink(projectId, linkId, requireDraft(draft)),
                (result, project) -> linkReferences(project, (ObjectLinkDto) result, "UPDATED"));
    }

    public MutationResultDto createAssociationClassInstance(ProjectId projectId,
            AssociationClassInstanceCommandRequestDto request) {
        AssociationClassInstanceDraftDto draft = request == null ? null : request.draft();
        return execute(projectId, "CREATE_ASSOCIATION_CLASS_INSTANCE",
                request == null ? null : request.expectedRevision(), draft,
                () -> objectModelService.createAssociationClassInstance(projectId, requireDraft(draft)),
                (result, project) -> associationClassInstanceReferences(project,
                        (AssociationClassInstanceAggregateDto) result, "CREATED"));
    }

    public MutationResultDto updateAssociationClassInstance(ProjectId projectId, ObjectLinkId linkId,
            AssociationClassInstanceCommandRequestDto request) {
        AssociationClassInstanceDraftDto draft = request == null ? null : request.draft();
        return execute(projectId, "UPDATE_ASSOCIATION_CLASS_INSTANCE",
                request == null ? null : request.expectedRevision(), draft,
                () -> objectModelService.updateAssociationClassInstance(projectId, linkId, requireDraft(draft)),
                (result, project) -> associationClassInstanceReferences(project,
                        (AssociationClassInstanceAggregateDto) result, "UPDATED"));
    }

    public ObjectLinkDeleteImpactDto objectLinkDeleteImpact(ProjectId projectId, ObjectLinkId linkId) {
        Project project;
        try {
            project = projectService.loadProject(projectId);
        } catch (ProjectNotFoundException exception) {
            throw failure(404, "ELEMENT_NOT_FOUND", exception.getMessage(),
                    "Das referenzierte Projekt existiert nicht.", null, Map.of("projectId", projectId.value()), List.of());
        }
        var link = project.objectModel().findLink(linkId).orElseThrow(() -> failure(404, "ELEMENT_NOT_FOUND",
                "Unknown object link: " + linkId.value(), "Der referenzierte Objektlink existiert nicht.", null,
                Map.of("linkId", linkId.value()), List.of(reference("OBJECT_LINK", linkId.value(), linkId.value(),
                        "linkId", "TARGET"))));
        ObjectLinkDto dto = de.useweb.backend.api.mapper.ProjectDtoMapper.toDto(link);
        List<CommandElementReferenceDto> context = new ArrayList<>();
        context.add(reference("ASSOCIATION", dto.associationId(), semanticName(project, "ASSOCIATION", dto.associationId()),
                "currentLink.associationId", "INSTANCE_OF"));
        context.addAll(linkReferences(project, dto, "CONTEXT").stream().skip(1).toList());
        var association = project.umlModel().findAssociation(link.associationId()).orElseThrow();
        for (var end : association.ends()) {
            if (end.aggregationKind() != AggregationKind.COMPOSITE) continue;
            link.ends().stream().filter(value -> !value.associationEndId().equals(end.id())).forEach(value ->
                    context.add(reference("OBJECT", value.objectId().value(),
                            semanticName(project, "OBJECT", value.objectId().value()),
                            "currentLink.endValues", "COMPOSITION_PART_DETACHED")));
        }
        List<CommandElementReferenceDto> allowed = new ArrayList<>();
        List<CommandElementReferenceDto> blockers = new ArrayList<>();
        if (link.associationClassObjectId() != null) {
            String objectId = link.associationClassObjectId().value();
            allowed.add(new CommandElementReferenceDto("snapshot:association-class-object:" + objectId, "OBJECT", objectId,
                    semanticName(project, "OBJECT", objectId), "currentLink.associationClassObjectId",
                    "ASSOCIATION_CLASS_IDENTITY", true));
            project.objectModel().links().stream().filter(other -> !other.id().equals(linkId))
                    .filter(other -> other.ends().stream().anyMatch(value -> value.objectId().value().equals(objectId))
                            || objectId.equals(other.associationClassObjectId() == null ? null
                                    : other.associationClassObjectId().value()))
                    .forEach(other -> blockers.add(new CommandElementReferenceDto(
                            "snapshot:blocking-link:" + other.id().value(), "OBJECT_LINK", other.id().value(),
                            other.id().value(), "objectModel.links", "REFERENCES_ASSOCIATION_CLASS_OBJECT", false)));
        }
        List<CommandElementReferenceDto> validationTargets = new ArrayList<>();
        validationTargets.add(reference("ASSOCIATION", dto.associationId(),
                semanticName(project, "ASSOCIATION", dto.associationId()), "validation", "REVALIDATE"));
        dto.endValues().forEach(end -> validationTargets.add(reference("OBJECT", end.objectId(),
                semanticName(project, "OBJECT", end.objectId()), "validation", "REVALIDATE")));
        return new ObjectLinkDeleteImpactDto(SNAPSHOT, revision(project),
                reference("OBJECT_LINK", dto.id(), dto.id(), "linkId", "TARGET"), dto, context, blockers, allowed,
                validationTargets, !blockers.isEmpty());
    }

    public MutationResultDto deleteObjectLink(ProjectId projectId, ObjectLinkId linkId, DeleteCommandRequestDto request) {
        Object draft = request;
        synchronized (lock(projectId)) {
            Project before;
            try {
                before = projectService.loadProject(projectId);
            } catch (ProjectNotFoundException exception) {
                throw failure(404, "ELEMENT_NOT_FOUND", exception.getMessage(), "Das Projekt existiert nicht.", draft,
                        Map.of("projectId", projectId.value()), List.of());
            }
            requireRevision(before, request == null ? null : request.expectedRevision(), draft);
            ObjectLinkDeleteImpactDto impact = objectLinkDeleteImpact(projectId, linkId);
            List<String> selected = request == null ? List.of() : request.cascadeReferenceIds();
            List<String> allowedIds = impact.allowedCascades().stream().map(CommandElementReferenceDto::referenceId).toList();
            List<String> unknown = selected.stream().filter(id -> !allowedIds.contains(id)).toList();
            if (!unknown.isEmpty()) throw failure(400, "INVALID_CASCADE_SELECTION", "Cascade selection is not allowed",
                    "Die Cascade-Auswahl ist nicht erlaubt.", draft,
                    Map.of("unknownReferenceIds", unknown, "allowedReferenceIds", allowedIds), impact.context());
            List<CommandElementReferenceDto> blockers = new ArrayList<>(impact.blockers());
            blockers.addAll(impact.allowedCascades().stream().filter(value -> !selected.contains(value.referenceId())).toList());
            if (!blockers.isEmpty()) throw failure(409, "DELETE_BLOCKED", "References block object-link deletion",
                    "Verbleibende Referenzen blockieren das Loeschen.", draft,
                    Map.of("currentImpact", impact, "blockers", blockers), blockers);
            try {
                Object result = objectModelService.deleteObjectLink(projectId, linkId);
                Project saved = projectService.loadProject(projectId);
                return new MutationResultDto("DELETE_OBJECT_LINK", SNAPSHOT, revision(saved), result,
                        List.of(impact.target()));
            } catch (ObjectModelException exception) {
                throw failure(conflictStatus(exception), exception.error().code(), exception.getMessage(),
                        exception.error().userMessage(), draft, exception.error().details(), impact.context());
            }
        }
    }

    private MutationResultDto execute(ProjectId projectId, String command, String expectedRevision, Object draft,
            Supplier<Object> action,
            java.util.function.BiFunction<Object, Project, List<CommandElementReferenceDto>> references) {
        synchronized (lock(projectId)) {
            Project before;
            try {
                before = projectService.loadProject(projectId);
            } catch (ProjectNotFoundException exception) {
                throw failure(404, "ELEMENT_NOT_FOUND", exception.getMessage(),
                        "Das referenzierte Projekt existiert nicht.", draft,
                        Map.of("projectId", projectId.value()), List.of(reference("PROJECT", projectId.value(),
                                projectId.value(), "projectId", "TARGET")));
            }
            requireRevision(before, expectedRevision, draft);
            try {
                Object result = action.get();
                Project saved = projectService.loadProject(projectId);
                return new MutationResultDto(command, SNAPSHOT, revision(saved), result, references.apply(result, saved));
            } catch (CommandException exception) {
                throw exception;
            } catch (ObjectModelException exception) {
                Map<String, Object> details = new LinkedHashMap<>(exception.error().details());
                List<CommandElementReferenceDto> targets = draftReferences(before, draft, details);
                throw failure(conflictStatus(exception), exception.error().code(),
                        exception.getMessage(), exception.error().userMessage(), draft, details, targets);
            }
        }
    }

    private void requireRevision(Project project, String expectedRevision, Object draft) {
        String actualRevision = revision(project);
        if (expectedRevision == null || expectedRevision.isBlank()) {
            throw failure(400, "EXPECTED_REVISION_REQUIRED", "expectedRevision is required",
                    "Die erwartete Snapshotrevision fehlt.", draft,
                    Map.of("actualRevision", actualRevision), draftReferences(project, draft, Map.of()));
        }
        if (!actualRevision.equals(expectedRevision)) {
            throw failure(409, "STALE_SNAPSHOT_REVISION", "Snapshot revision is stale",
                    "Der Snapshot wurde zwischenzeitlich geaendert.", draft,
                    Map.of("expectedRevision", expectedRevision, "actualRevision", actualRevision),
                    draftReferences(project, draft, Map.of()));
        }
    }

    private <T> T requireDraft(T draft) {
        if (draft == null) {
            throw failure(400, "DRAFT_REQUIRED", "Command draft is required",
                    "Der vollstaendige Entwurf fehlt.", null, Map.of(), List.of());
        }
        return draft;
    }

    private SlotDto normalizedSlot(String slotId, SlotDto draft) {
        return new SlotDto(slotId, draft.attributeId(), draft.value(), draft.isUnset());
    }

    private String expectedRevision(CreateObjectCommandRequestDto request) {
        return request == null ? null : request.expectedRevision();
    }

    private String expectedRevision(UpdateSlotCommandRequestDto request) {
        return request == null ? null : request.expectedRevision();
    }

    private String expectedRevision(CreateObjectLinkCommandRequestDto request) {
        return request == null ? null : request.expectedRevision();
    }

    private List<CommandElementReferenceDto> objectReferences(Project project, ObjectInstanceDto object) {
        return List.of(
                reference("OBJECT", object.id(), object.name(), "draft.id", "CREATED"),
                reference("CLASSIFIER", object.classId(), semanticName(project, "CLASSIFIER", object.classId()),
                        "draft.classId", "INSTANCE_OF"));
    }

    private List<CommandElementReferenceDto> slotReferences(Project project, String objectId, SlotDto slot) {
        return List.of(
                reference("OBJECT", objectId, semanticName(project, "OBJECT", objectId), "objectId", "OWNER"),
                reference("SLOT", slot.id(), slot.id(), "draft.id", "UPDATED"),
                reference("FEATURE", slot.attributeId(), semanticName(project, "FEATURE", slot.attributeId()),
                        "draft.attributeId", "VALUE_OF"));
    }

    private List<CommandElementReferenceDto> linkReferences(Project project, ObjectLinkDto link) {
        return linkReferences(project, link, "CREATED");
    }

    private List<CommandElementReferenceDto> linkReferences(Project project, ObjectLinkDto link, String relation) {
        List<CommandElementReferenceDto> references = new ArrayList<>();
        references.add(reference("OBJECT_LINK", link.id(), link.id(), "draft.id", relation));
        references.add(reference("ASSOCIATION", link.associationId(), semanticName(project, "ASSOCIATION", link.associationId()),
                "draft.associationId", "INSTANCE_OF"));
        if (link.associationClassObjectId() != null) {
            references.add(reference("OBJECT", link.associationClassObjectId(),
                    semanticName(project, "OBJECT", link.associationClassObjectId()),
                    "draft.associationClassObjectId", "LINK_OBJECT"));
        }
        List<de.useweb.backend.api.dto.snapshot.ObjectLinkEndValueDto> endValues =
                link.endValues() == null ? List.of() : link.endValues();
        for (int index = 0; index < endValues.size(); index++) {
            var end = endValues.get(index);
            String path = "draft.endValues[" + index + "]";
            references.add(reference("ASSOCIATION_END", end.associationEndId(),
                    semanticName(project, "ASSOCIATION_END", end.associationEndId()),
                    path + ".associationEndId", "BOUND_END"));
            references.add(reference("OBJECT", end.objectId(), semanticName(project, "OBJECT", end.objectId()),
                    path + ".objectId", "BOUND_OBJECT"));
            List<de.useweb.backend.api.dto.snapshot.QualifierValueDto> qualifierValues =
                    end.qualifierValues() == null ? List.of() : end.qualifierValues();
            for (int qualifierIndex = 0; qualifierIndex < qualifierValues.size(); qualifierIndex++) {
                var qualifier = qualifierValues.get(qualifierIndex);
                references.add(reference("QUALIFIER", qualifier.qualifierId(),
                        semanticName(project, "QUALIFIER", qualifier.qualifierId()),
                        path + ".qualifierValues[" + qualifierIndex + "].qualifierId", "QUALIFIER_VALUE"));
            }
        }
        return List.copyOf(references);
    }

    private List<CommandElementReferenceDto> associationClassInstanceReferences(Project project,
            AssociationClassInstanceAggregateDto aggregate, String relation) {
        List<CommandElementReferenceDto> references = new ArrayList<>();
        references.addAll(linkReferences(project, aggregate.link(), relation).stream()
                .map(value -> withPathPrefix(value, "draft.link.")).toList());
        ObjectInstanceDto object = aggregate.associationClassObject();
        references.add(reference("OBJECT", object.id(), object.name(),
                "draft.associationClassObject.id", relation));
        references.add(reference("CLASSIFIER", object.classId(),
                semanticName(project, "CLASSIFIER", object.classId()),
                "draft.associationClassObject.classId", "INSTANCE_OF"));
        List<SlotDto> slots = object.slots() == null ? List.of() : object.slots();
        for (int index = 0; index < slots.size(); index++) {
            SlotDto slot = slots.get(index);
            references.add(reference("SLOT", slot.id(), slot.id(),
                    "draft.associationClassObject.slots[" + index + "].id", relation));
            references.add(reference("FEATURE", slot.attributeId(),
                    semanticName(project, "FEATURE", slot.attributeId()),
                    "draft.associationClassObject.slots[" + index + "].attributeId", "VALUE_OF"));
        }
        return List.copyOf(references);
    }

    private CommandElementReferenceDto withPathPrefix(CommandElementReferenceDto value, String prefix) {
        String path = value.path();
        if (path != null && path.startsWith("draft.")) path = prefix + path.substring("draft.".length());
        return new CommandElementReferenceDto(value.referenceId(), value.elementType(), value.elementId(),
                value.elementName(), path, value.relation(), value.cascadeAllowed());
    }

    private List<CommandElementReferenceDto> draftReferences(Project project, Object draft, Map<String, Object> details) {
        if (draft instanceof AssociationClassInstanceDraftDto aggregate) {
            List<CommandElementReferenceDto> references = new ArrayList<>();
            if (aggregate.link() != null) references.addAll(linkReferences(project, aggregate.link()).stream()
                    .map(value -> withPathPrefix(value, "draft.link.")).toList());
            if (aggregate.associationClassObject() != null) {
                ObjectInstanceDto object = aggregate.associationClassObject();
                references.add(reference("OBJECT", valueOrDraft(object.id()), valueOrDraft(object.name()),
                        "draft.associationClassObject.id", "DRAFT"));
                references.add(reference("CLASSIFIER", valueOrDraft(object.classId()),
                        semanticName(project, "CLASSIFIER", object.classId()),
                        "draft.associationClassObject.classId", "TARGET"));
                List<SlotDto> slots = object.slots() == null ? List.of() : object.slots();
                for (int index = 0; index < slots.size(); index++) {
                    SlotDto slot = slots.get(index);
                    references.add(reference("SLOT", valueOrDraft(slot.id()), valueOrDraft(slot.id()),
                            "draft.associationClassObject.slots[" + index + "].id", "DRAFT"));
                    references.add(reference("FEATURE", valueOrDraft(slot.attributeId()),
                            semanticName(project, "FEATURE", slot.attributeId()),
                            "draft.associationClassObject.slots[" + index + "].attributeId", "TARGET"));
                }
            }
            return List.copyOf(references);
        }
        if (draft instanceof ObjectInstanceDto object) return List.of(
                reference("OBJECT", valueOrDraft(object.id()), valueOrDraft(object.name()), "draft.id", "DRAFT"),
                reference("CLASSIFIER", valueOrDraft(object.classId()), semanticName(project, "CLASSIFIER", object.classId()),
                        "draft.classId", "TARGET"));
        if (draft instanceof SlotDto slot) return List.of(
                reference("SLOT", valueOrDraft(slot.id()), valueOrDraft(slot.id()), "draft.id", "DRAFT"),
                reference("FEATURE", valueOrDraft(slot.attributeId()), semanticName(project, "FEATURE", slot.attributeId()),
                        "draft.attributeId", "TARGET"));
        if (draft instanceof ObjectLinkDto link) return linkReferences(project, link);
        return details.entrySet().stream().filter(entry -> entry.getKey().endsWith("Id"))
                .map(entry -> reference(elementType(entry.getKey()), String.valueOf(entry.getValue()),
                        String.valueOf(entry.getValue()), "details." + entry.getKey(), "TARGET"))
                .toList();
    }

    private CommandException failure(int status, String code, String message, String userMessage, Object draft,
            Map<String, Object> details, List<CommandElementReferenceDto> targets) {
        Map<String, Object> payload = new LinkedHashMap<>(details);
        payload.put("draft", draft == null ? "null" : draft);
        payload.put("targets", targets);
        return new CommandException(status, code, message, userMessage, payload);
    }

    private int conflictStatus(ObjectModelException exception) {
        if (exception.getMessage() != null && exception.getMessage().startsWith("Unknown ")) return 404;
        return switch (exception.error().code()) {
            case "INVALID_LINK", "ASSOCIATION_CLASS_IDENTITY_VIOLATION", "OBJECT_LINK_DUPLICATE", "MULTIPLICITY_VIOLATION",
                    "COMPOSITE_OWNERSHIP_VIOLATION", "COMPOSITION_CYCLE" -> 409;
            case "UNKNOWN_CLASS", "UNKNOWN_ATTRIBUTE", "ELEMENT_NOT_FOUND" -> 404;
            default -> 400;
        };
    }

    private CommandElementReferenceDto reference(String type, String id, String name, String path, String relation) {
        String stableId = valueOrDraft(id);
        return new CommandElementReferenceDto("snapshot:" + type.toLowerCase() + ":" + stableId,
                type, stableId, valueOrDraft(name), path, relation, false);
    }

    private String elementType(String key) {
        return key.substring(0, key.length() - 2).replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
    }

    private String semanticName(Project project, String type, String id) {
        if (id == null || id.isBlank()) return "draft";
        return switch (type) {
            case "CLASSIFIER" -> project.umlModel().classes().stream()
                    .filter(value -> value.id().value().equals(id)).map(value -> value.name()).findFirst().orElse(id);
            case "FEATURE" -> project.umlModel().classes().stream().flatMap(value -> value.attributes().stream())
                    .filter(value -> value.id().value().equals(id)).map(value -> value.name()).findFirst().orElse(id);
            case "ASSOCIATION" -> project.umlModel().associations().stream()
                    .filter(value -> value.id().value().equals(id)).map(value -> value.name()).findFirst().orElse(id);
            case "ASSOCIATION_END" -> project.umlModel().associations().stream().flatMap(value -> value.ends().stream())
                    .filter(value -> value.id().value().equals(id)).map(value -> value.roleName()).findFirst().orElse(id);
            case "QUALIFIER" -> project.umlModel().associations().stream().flatMap(value -> value.ends().stream())
                    .flatMap(value -> value.qualifiers().stream()).filter(value -> value.id().value().equals(id))
                    .map(value -> value.name()).findFirst().orElse(id);
            case "OBJECT" -> project.objectModel().objects().stream()
                    .filter(value -> value.id().value().equals(id)).map(value -> value.name()).findFirst().orElse(id);
            default -> id;
        };
    }

    private String valueOrDraft(String value) {
        return value == null || value.isBlank() ? "draft" : value;
    }

    private Object lock(ProjectId projectId) {
        return projectLocks.computeIfAbsent(projectId.value(), ignored -> new Object());
    }

    private String revision(Project project) {
        return project.metadata().updatedAt().toString();
    }
}
