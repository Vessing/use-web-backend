package de.useweb.backend.application.modeltext;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.useweb.backend.api.dto.modeltext.ApplyModelTextRequestDto;
import de.useweb.backend.api.dto.modeltext.ApplyModelTextResponseDto;
import de.useweb.backend.api.dto.modeltext.ModelTextDto;
import de.useweb.backend.api.dto.ocl.OclDiagnosticDto;
import de.useweb.backend.api.dto.ocl.OclParseRequestDto;
import de.useweb.backend.api.mapper.ProjectDtoMapper;
import de.useweb.backend.application.ocl.OclParseService;
import de.useweb.backend.application.project.ProjectService;
import de.useweb.backend.domain.modeltext.ModelText;
import de.useweb.backend.domain.ocl.OclExpression;
import de.useweb.backend.domain.ocl.OclExpressionId;
import de.useweb.backend.domain.project.Project;
import de.useweb.backend.domain.project.ProjectId;
import de.useweb.backend.domain.uml.Multiplicity;
import de.useweb.backend.domain.uml.UmlAssociation;
import de.useweb.backend.domain.uml.UmlAssociationEnd;
import de.useweb.backend.domain.uml.UmlAssociationEndId;
import de.useweb.backend.domain.uml.UmlAssociationId;
import de.useweb.backend.domain.uml.UmlAttribute;
import de.useweb.backend.domain.uml.UmlAttributeId;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlInvariant;
import de.useweb.backend.domain.uml.UmlInvariantId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlModelId;
import de.useweb.backend.domain.uml.UmlOperation;
import de.useweb.backend.domain.uml.UmlOperationId;
import de.useweb.backend.domain.uml.UmlParameter;
import de.useweb.backend.domain.uml.UmlParameterId;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.modeltext.parser.ModelTextParser;
import de.useweb.backend.modeltext.parser.ModelTextParser.ModelTextAssociation;
import de.useweb.backend.modeltext.parser.ModelTextParser.ModelTextAssociationEnd;
import de.useweb.backend.modeltext.parser.ModelTextParser.ModelTextClass;
import de.useweb.backend.modeltext.parser.ModelTextParser.ModelTextInvariant;
import de.useweb.backend.modeltext.parser.ModelTextParser.ModelTextOperation;
import de.useweb.backend.modeltext.parser.ModelTextParser.ModelTextParseResult;

@Service
public class ModelTextApplicationService {

    private final ProjectService projectService;
    private final ModelTextParser modelTextParser;
    private final OclParseService oclParseService;
    private final Clock clock;

    @Autowired
    public ModelTextApplicationService(ProjectService projectService, ModelTextParser modelTextParser, OclParseService oclParseService) {
        this(projectService, modelTextParser, oclParseService, Clock.systemUTC());
    }

    public ModelTextApplicationService(ProjectService projectService, ModelTextParser modelTextParser, OclParseService oclParseService, Clock clock) {
        this.projectService = projectService;
        this.modelTextParser = modelTextParser;
        this.oclParseService = oclParseService;
        this.clock = clock;
    }

    public ModelTextDto getModelText(ProjectId projectId) {
        Project project = projectService.loadProject(projectId);
        ModelText modelText = project.modelText();
        if (modelText == null) {
            modelText = new ModelText(defaultModelText(project), "USE_MODEL_TEXT", "mvp-subset", project.metadata().updatedAt(), null, "generated");
        }
        return ProjectDtoMapper.toDto(project.id(), modelText);
    }

    public ApplyModelTextResponseDto applyModelText(ProjectId projectId, ApplyModelTextRequestDto request) {
        Project currentProject = projectService.loadProject(projectId);
        String text = request == null ? "" : request.modelText();
        ModelTextParseResult parseResult = modelTextParser.parse(text);
        List<OclDiagnosticDto> diagnostics = new ArrayList<>(parseResult.diagnostics());
        diagnostics.addAll(parseOclDiagnostics(parseResult.invariants()));

        ModelText modelText = new ModelText(
                text,
                request == null || request.format() == null ? "USE_MODEL_TEXT" : request.format(),
                "mvp-subset",
                Instant.now(clock),
                request == null ? null : request.sourceName(),
                request == null ? null : firstNonBlank(request.sourceOrigin(), request.sourceFormat()));

        boolean hasErrors = diagnostics.stream().anyMatch(diagnostic -> "ERROR".equalsIgnoreCase(diagnostic.severity()));
        boolean hasSupportedModelParts = parseResult.hasSupportedModelParts();
        Project projectToSave;
        String status;
        boolean success;
        List<String> changedElementIds;

        if (hasErrors || !hasSupportedModelParts) {
            projectToSave = projectWithModelText(currentProject, modelText);
            status = "NOT_APPLIED";
            success = false;
            changedElementIds = List.of();
        } else {
            UmlModel umlModel = toUmlModel(currentProject.umlModel().id(), currentProject.umlModel().name(), parseResult);
            projectToSave = new Project(
                    currentProject.id(),
                    currentProject.metadata(),
                    modelText,
                    umlModel,
                    currentProject.objectModel(),
                    currentProject.layout());
            status = diagnostics.isEmpty() ? "APPLIED" : "APPLIED_WITH_WARNINGS";
            success = true;
            changedElementIds = changedElementIds(umlModel);
        }

        Project savedProject = projectService.saveProject(projectToSave);
        return new ApplyModelTextResponseDto(
                success,
                status,
                ProjectDtoMapper.toDto(savedProject),
                ProjectDtoMapper.toDto(savedProject.id(), savedProject.modelText()),
                List.copyOf(diagnostics),
                changedElementIds);
    }

    private Project projectWithModelText(Project project, ModelText modelText) {
        return new Project(project.id(), project.metadata(), modelText, project.umlModel(), project.objectModel(), project.layout());
    }

    private String defaultModelText(Project project) {
        return "model " + sanitizeIdentifier(project.metadata().name()) + System.lineSeparator();
    }

    private List<OclDiagnosticDto> parseOclDiagnostics(List<ModelTextInvariant> invariants) {
        List<OclDiagnosticDto> diagnostics = new ArrayList<>();
        for (ModelTextInvariant invariant : invariants) {
            diagnostics.addAll(oclParseService.parse(new OclParseRequestDto(invariant.expression())).diagnostics());
        }
        return diagnostics;
    }

    private UmlModel toUmlModel(UmlModelId umlModelId, String fallbackName, ModelTextParseResult parseResult) {
        List<UmlClass> classes = parseResult.classes().stream().map(this::toUmlClass).toList();
        List<UmlAssociation> associations = parseResult.associations().stream().map(this::toUmlAssociation).toList();
        List<UmlInvariant> invariants = parseResult.invariants().stream().map(this::toUmlInvariant).toList();
        String modelName = parseResult.modelName() == null || parseResult.modelName().isBlank() ? fallbackName : parseResult.modelName();
        return new UmlModel(umlModelId, modelName, classes, associations, invariants);
    }

    private UmlClass toUmlClass(ModelTextClass modelTextClass) {
        String classKey = kebab(modelTextClass.name());
        return new UmlClass(
                new UmlClassId("class-" + classKey),
                modelTextClass.name(),
                modelTextClass.attributes().stream()
                        .map(attribute -> new UmlAttribute(
                                new UmlAttributeId("attr-" + classKey + "-" + kebab(attribute.name())),
                                attribute.name(),
                                typeOf(attribute.type())))
                        .toList(),
                modelTextClass.operations().stream()
                        .map(operation -> toUmlOperation(classKey, operation))
                        .toList());
    }

    private UmlOperation toUmlOperation(String classKey, ModelTextOperation operation) {
        String operationKey = kebab(operation.name());
        List<UmlParameter> parameters = operation.parameters().stream()
                .map(parameter -> new UmlParameter(
                        new UmlParameterId("param-" + classKey + "-" + operationKey + "-" + kebab(parameter.name())),
                        parameter.name(),
                        typeOf(parameter.type())))
                .toList();
        return new UmlOperation(
                new UmlOperationId("op-" + classKey + "-" + operationKey),
                operation.name(),
                typeOf(operation.returnType()),
                parameters);
    }

    private UmlAssociation toUmlAssociation(ModelTextAssociation association) {
        String associationKey = kebab(association.name());
        return new UmlAssociation(
                new UmlAssociationId("assoc-" + associationKey),
                association.name(),
                association.ends().stream()
                        .map(end -> toUmlAssociationEnd(associationKey, end))
                        .toList());
    }

    private UmlAssociationEnd toUmlAssociationEnd(String associationKey, ModelTextAssociationEnd end) {
        String roleName = end.roleName() == null || end.roleName().isBlank() ? lowerCamel(end.className()) : end.roleName();
        return new UmlAssociationEnd(
                new UmlAssociationEndId("assocend-" + associationKey + "-" + kebab(roleName)),
                new UmlClassId("class-" + kebab(end.className())),
                roleName,
                multiplicity(end.multiplicity()),
                true);
    }

    private UmlInvariant toUmlInvariant(ModelTextInvariant invariant) {
        String invariantKey = kebab(invariant.name());
        return new UmlInvariant(
                new UmlInvariantId("inv-" + invariantKey),
                invariant.name(),
                new UmlClassId("class-" + kebab(invariant.contextClass())),
                new OclExpression(new OclExpressionId("expr-" + invariantKey), invariant.expression(), "mvp-subset"),
                true);
    }

    private List<String> changedElementIds(UmlModel umlModel) {
        List<String> ids = new ArrayList<>();
        umlModel.classes().forEach(umlClass -> {
            ids.add(umlClass.id().value());
            umlClass.attributes().forEach(attribute -> ids.add(attribute.id().value()));
            umlClass.operations().forEach(operation -> ids.add(operation.id().value()));
        });
        umlModel.associations().forEach(association -> {
            ids.add(association.id().value());
            association.ends().forEach(end -> ids.add(end.id().value()));
        });
        umlModel.invariants().forEach(invariant -> ids.add(invariant.id().value()));
        return ids;
    }

    private Multiplicity multiplicity(String raw) {
        String value = raw == null || raw.isBlank() ? "1" : raw.trim();
        if ("*".equals(value)) {
            return new Multiplicity(0, null, true, "*");
        }
        if (value.endsWith("..*")) {
            return new Multiplicity(Integer.parseInt(value.substring(0, value.length() - 3)), null, true, value);
        }
        if (value.contains("..")) {
            String[] bounds = value.split("\\.\\.", 2);
            return new Multiplicity(Integer.parseInt(bounds[0]), Integer.parseInt(bounds[1]), false, value);
        }
        int exact = Integer.parseInt(value);
        return new Multiplicity(exact, exact, false, value);
    }

    private UmlType typeOf(String typeName) {
        if ("String".equals(typeName)) {
            return UmlType.STRING;
        }
        if ("Integer".equals(typeName)) {
            return UmlType.INTEGER;
        }
        if ("Real".equals(typeName)) {
            return UmlType.REAL;
        }
        if ("Boolean".equals(typeName)) {
            return UmlType.BOOLEAN;
        }
        if ("Void".equals(typeName)) {
            return UmlType.VOID;
        }
        return UmlType.classType(typeName);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private String sanitizeIdentifier(String value) {
        String sanitized = value == null ? "Model" : value.replaceAll("[^A-Za-z0-9_]", "");
        return sanitized.isBlank() ? "Model" : sanitized;
    }

    private String kebab(String value) {
        return value.replaceAll("([a-z])([A-Z])", "$1-$2")
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("^-|-$", "")
                .toLowerCase(Locale.ROOT);
    }

    private String lowerCamel(String value) {
        if (value == null || value.isBlank()) {
            return "end";
        }
        return value.substring(0, 1).toLowerCase(Locale.ROOT) + value.substring(1);
    }
}
