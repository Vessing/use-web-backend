package de.useweb.backend.application.ocl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import de.useweb.backend.api.dto.ocl.OclDefinitionElementDto;
import de.useweb.backend.api.mapper.ProjectDtoMapper;
import de.useweb.backend.application.project.ProjectService;
import de.useweb.backend.domain.ocl.OclDefinitionElement;
import de.useweb.backend.domain.ocl.OclDefinitionElementId;
import de.useweb.backend.domain.project.Project;
import de.useweb.backend.domain.project.ProjectId;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.error.CommandException;
import de.useweb.backend.ocl.definition.OclDefinition;
import de.useweb.backend.ocl.definition.OclDefinitionId;
import de.useweb.backend.ocl.definition.OclDefinitionKind;
import de.useweb.backend.ocl.definition.OclDefinitionService;
import de.useweb.backend.ocl.definition.OclModelDefinitionFactory;
import de.useweb.backend.ocl.definition.OclProjectDefinitionFactory;
import de.useweb.backend.ocl.parser.OclParseResult;
import de.useweb.backend.ocl.parser.OclParser;

@Service
public class OclDefinitionApplicationService {
    private final ProjectService projects;
    private final OclParser parser = new OclParser();

    public OclDefinitionApplicationService(ProjectService projects) {
        this.projects = projects;
    }

    public List<OclDefinitionElementDto> list(ProjectId projectId) {
        Project project = projects.loadProject(projectId);
        return project.definitions().stream().map(value -> ProjectDtoMapper.toDto(value, project.umlModel())).toList();
    }

    public List<OclDefinition> runtimeDefinitions(Project project) {
        return new OclProjectDefinitionFactory().definitions(project);
    }

    public OclDefinitionElementDto create(ProjectId projectId, OclDefinitionElementDto draft, JsonNode rawDraft) {
        Project project = projects.loadProject(projectId);
        String id = draft.id() == null || draft.id().isBlank() ? "definition-" + UUID.randomUUID() : draft.id();
        OclDefinitionElement value = ProjectDtoMapper.toDomain(new OclDefinitionElementDto(id, draft.kind(),
                draft.ownerKind(), draft.ownerId(), draft.ownerName(), draft.name(), draft.qualifiedName(),
                draft.resultType(), draft.parameters(), draft.expression(), draft.sourceRange()));
        List<OclDefinitionElement> definitions = new ArrayList<>(project.definitions());
        definitions.add(value);
        validate(project, definitions, value, rawDraft);
        Project saved = save(project, definitions);
        return ProjectDtoMapper.toDto(value, saved.umlModel());
    }

    public OclDefinitionElementDto update(ProjectId projectId, OclDefinitionElementId id,
            OclDefinitionElementDto draft, JsonNode rawDraft) {
        Project project = projects.loadProject(projectId);
        int index = indexOf(project.definitions(), id);
        OclDefinitionElement value = ProjectDtoMapper.toDomain(new OclDefinitionElementDto(id.value(), draft.kind(),
                draft.ownerKind(), draft.ownerId(), draft.ownerName(), draft.name(), draft.qualifiedName(),
                draft.resultType(), draft.parameters(), draft.expression(), draft.sourceRange()));
        List<OclDefinitionElement> definitions = new ArrayList<>(project.definitions());
        definitions.set(index, value);
        validate(project, definitions, value, rawDraft);
        Project saved = save(project, definitions);
        return ProjectDtoMapper.toDto(value, saved.umlModel());
    }

    public OclDefinitionElementDto delete(ProjectId projectId, OclDefinitionElementId id) {
        Project project = projects.loadProject(projectId);
        int index = indexOf(project.definitions(), id);
        OclDefinitionElement removed = project.definitions().get(index);
        List<OclDefinitionElement> definitions = new ArrayList<>(project.definitions());
        definitions.remove(index);
        save(project, definitions);
        return ProjectDtoMapper.toDto(removed, project.umlModel());
    }

    public void validate(Project project, List<OclDefinitionElement> definitions,
            OclDefinitionElement candidate, JsonNode rawDraft) {
        validateOwner(project, candidate, rawDraft);
        OclParseResult parsed = parser.parse(candidate.expression());
        if (!parsed.success()) {
            fail(400, "OCL_DEFINITION_COMPILE_FAILED", "Definition expression does not parse",
                    "Der Definitionsausdruck enthaelt Syntaxfehler.", rawDraft,
                    Map.of("definitionId", candidate.id().value(), "diagnostics", parsed.diagnostics()));
        }
        if (candidate.ownerKind() == OclDefinitionElement.OwnerKind.PACKAGE
                && candidate.expression().matches("(?s).*\\bself\\b.*")) {
            fail(400, "PACKAGE_DEFINITION_SELF_NOT_ALLOWED", "Package definitions have no implicit self",
                    "Paketdefinitionen besitzen keinen impliziten self-Kontext.", rawDraft,
                    Map.of("definitionId", candidate.id().value(), "ownerId", candidate.ownerId()));
        }
        long duplicate = definitions.stream().filter(other -> !other.id().equals(candidate.id()))
                .filter(other -> other.ownerKind() == candidate.ownerKind() && other.ownerId().equals(candidate.ownerId()))
                .filter(other -> other.kind() == candidate.kind() && other.name().equals(candidate.name()))
                .filter(other -> other.parameters().size() == candidate.parameters().size()).count();
        if (duplicate > 0) fail(409, "OCL_DEFINITION_SIGNATURE_CONFLICT", "Definition signature already exists",
                "Im Namespace existiert bereits eine Definition mit dieser Signatur.", rawDraft,
                Map.of("definitionId", candidate.id().value(), "ownerId", candidate.ownerId()));
        validateCycles(definitions, rawDraft);
        if (candidate.ownerKind() == OclDefinitionElement.OwnerKind.CLASS) {
            List<OclDefinition> runtimeDefinitions = definitions.stream()
                    .filter(value -> value.ownerKind() == OclDefinitionElement.OwnerKind.CLASS)
                    .map(this::runtimeDefinition).toList();
            OclDefinition runtime = runtimeDefinitions.stream()
                    .filter(value -> value.id().value().equals(candidate.id().value())).findFirst().orElseThrow();
            var checked = new OclDefinitionService(project.umlModel(), runtimeDefinitions).check(runtime, project.umlModel());
            if (!checked.success()) fail(400, "OCL_DEFINITION_COMPILE_FAILED", "Definition does not typecheck",
                    "Die Definition enthaelt Typ- oder Namensfehler.", rawDraft,
                    Map.of("definitionId", candidate.id().value(), "diagnostics", checked.diagnostics()));
        }
    }

    private OclDefinition runtimeDefinition(OclDefinitionElement value) {
        OclParseResult parsed = parser.parse(value.expression());
        if (!parsed.success()) throw new IllegalArgumentException("Stored definition does not parse: " + value.id().value());
        return new OclDefinition(new OclDefinitionId(value.id().value()),
                value.kind() == OclDefinitionElement.Kind.PROPERTY_DEF
                        ? OclDefinitionKind.PROPERTY_DEF : OclDefinitionKind.OPERATION_DEF,
                new UmlClassId(value.ownerId()), value.name(), null, null, value.resultType(), value.parameters(),
                value.expression(), parsed.ast());
    }

    private void validateOwner(Project project, OclDefinitionElement value, JsonNode draft) {
        boolean exists = value.ownerKind() == OclDefinitionElement.OwnerKind.CLASS
                ? project.umlModel().findClass(new UmlClassId(value.ownerId())).isPresent()
                : project.umlModel().packages().stream().anyMatch(pkg -> pkg.id().value().equals(value.ownerId()));
        if (!exists) fail(404, "OCL_DEFINITION_OWNER_NOT_FOUND", "Definition owner not found",
                "Der Definitionskontext existiert nicht.", draft,
                Map.of("definitionId", value.id().value(), "ownerId", value.ownerId(),
                        "ownerKind", value.ownerKind().name()));
        Set<String> names = new HashSet<>();
        value.parameters().forEach(parameter -> {
            if (!names.add(parameter.name())) fail(400, "OCL_DEFINITION_PARAMETER_CONFLICT",
                    "Duplicate definition parameter: " + parameter.name(),
                    "Parameternamen einer Definition muessen eindeutig sein.", draft,
                    Map.of("definitionId", value.id().value(), "parameterId", parameter.id().value()));
        });
    }

    private void validateCycles(List<OclDefinitionElement> definitions, JsonNode draft) {
        Map<String, Set<String>> graph = new HashMap<>();
        definitions.forEach(source -> {
            Set<String> dependencies = new HashSet<>();
            definitions.stream().filter(target -> !target.id().equals(source.id()))
                    .filter(target -> source.expression().matches("(?s).*\\b"
                            + java.util.regex.Pattern.quote(target.name()) + "\\b.*"))
                    .forEach(target -> dependencies.add(target.id().value()));
            graph.put(source.id().value(), dependencies);
        });
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String id : graph.keySet()) if (cycle(id, graph, visiting, visited)) {
            fail(409, "OCL_DEFINITION_CYCLE", "Definition dependency cycle detected",
                    "Definitionen duerfen keinen Abhaengigkeitszyklus bilden.", draft,
                    Map.of("definitionId", id, "dependencyGraph", graph));
        }
    }

    private boolean cycle(String id, Map<String, Set<String>> graph, Set<String> visiting, Set<String> visited) {
        if (visiting.contains(id)) return true;
        if (!visited.add(id)) return false;
        visiting.add(id);
        for (String next : graph.getOrDefault(id, Set.of())) if (cycle(next, graph, visiting, visited)) return true;
        visiting.remove(id);
        return false;
    }

    private int indexOf(List<OclDefinitionElement> definitions, OclDefinitionElementId id) {
        for (int index = 0; index < definitions.size(); index++) if (definitions.get(index).id().equals(id)) return index;
        throw new CommandException(404, "OCL_DEFINITION_NOT_FOUND", "Definition not found: " + id.value(),
                "Die Definition existiert nicht.", Map.of("definitionId", id.value()));
    }

    private Project save(Project project, List<OclDefinitionElement> definitions) {
        return projects.saveProject(new Project(project.id(), project.metadata(), project.modelText(), project.umlModel(),
                project.objectModel(), project.layout(), definitions));
    }

    private void fail(int status, String code, String message, String userMessage, JsonNode draft,
            Map<String, Object> details) {
        Map<String, Object> copy = new HashMap<>(details);
        copy.put("draft", draft == null ? Map.of() : draft);
        throw new CommandException(status, code, message, userMessage, copy);
    }
}
