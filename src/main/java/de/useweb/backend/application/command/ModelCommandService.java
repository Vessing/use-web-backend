package de.useweb.backend.application.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.useweb.backend.api.dto.command.CommandElementReferenceDto;
import de.useweb.backend.api.dto.command.AssociationClassAggregateDto;
import de.useweb.backend.api.dto.command.DeleteCommandRequestDto;
import de.useweb.backend.api.dto.command.DeleteImpactDto;
import de.useweb.backend.api.dto.command.MutationCommandRequestDto;
import de.useweb.backend.api.dto.command.MutationResultDto;
import de.useweb.backend.api.dto.ocl.OclDiagnosticDto;
import de.useweb.backend.api.dto.ocl.OclDefinitionElementDto;
import de.useweb.backend.api.dto.ocl.SourceReferenceDto;
import de.useweb.backend.api.dto.uml.UmlAssociationDto;
import de.useweb.backend.api.dto.uml.UmlAttributeDto;
import de.useweb.backend.api.dto.uml.UmlClassDto;
import de.useweb.backend.api.dto.uml.UmlDataTypeDto;
import de.useweb.backend.api.dto.uml.UmlDataTypePropertyDto;
import de.useweb.backend.api.dto.uml.UmlEnumerationDto;
import de.useweb.backend.api.dto.uml.UmlEnumerationLiteralDto;
import de.useweb.backend.api.dto.uml.UmlInvariantDto;
import de.useweb.backend.api.dto.uml.UmlOperationDto;
import de.useweb.backend.api.dto.uml.UmlOperationContractDto;
import de.useweb.backend.api.dto.uml.UmlParameterDto;
import de.useweb.backend.api.dto.uml.UmlPackageDto;
import de.useweb.backend.api.dto.uml.UmlModelImportDto;
import de.useweb.backend.api.dto.uml.UmlQualifierDefinitionDto;
import de.useweb.backend.application.ocl.OclDiagnosticMapper;
import de.useweb.backend.application.ocl.OclDefinitionApplicationService;
import de.useweb.backend.application.project.ProjectService;
import de.useweb.backend.application.snapshot.ObjectModelService;
import de.useweb.backend.application.uml.StructuredUmlTypeService;
import de.useweb.backend.application.uml.UmlModelService;
import de.useweb.backend.domain.project.Project;
import de.useweb.backend.domain.project.ProjectId;
import de.useweb.backend.domain.ocl.OclDefinitionElementId;
import de.useweb.backend.domain.snapshot.ObjectInstance;
import de.useweb.backend.domain.uml.UmlAssociation;
import de.useweb.backend.domain.uml.UmlAssociationId;
import de.useweb.backend.domain.uml.UmlAttribute;
import de.useweb.backend.domain.uml.UmlAttributeId;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlDataTypeId;
import de.useweb.backend.domain.uml.UmlDataType;
import de.useweb.backend.domain.uml.UmlDataTypeProperty;
import de.useweb.backend.domain.uml.UmlEnumeration;
import de.useweb.backend.domain.uml.UmlEnumerationId;
import de.useweb.backend.domain.uml.UmlEnumerationLiteral;
import de.useweb.backend.domain.uml.UmlEnumerationLiteralId;
import de.useweb.backend.domain.uml.UmlInvariantId;
import de.useweb.backend.domain.uml.UmlOperation;
import de.useweb.backend.domain.uml.UmlOperationId;
import de.useweb.backend.domain.uml.UmlPackage;
import de.useweb.backend.domain.uml.UmlPackageId;
import de.useweb.backend.domain.uml.UmlModelImport;
import de.useweb.backend.domain.uml.UmlModelImportId;
import de.useweb.backend.domain.uml.UmlNamespaceException;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.error.CommandException;
import de.useweb.backend.error.ObjectModelException;
import de.useweb.backend.error.UmlModelException;
import de.useweb.backend.ocl.parser.OclParseResult;
import de.useweb.backend.ocl.parser.OclParser;
import de.useweb.backend.ocl.contract.OperationConstraintKind;
import de.useweb.backend.ocl.definition.OclDefinitionService;
import de.useweb.backend.ocl.typecheck.OclTypeChecker;
import de.useweb.backend.ocl.typecheck.OclType;
import de.useweb.backend.ocl.typecheck.OclTypecheckResult;
import de.useweb.backend.ocl.typecheck.TypeEnvironment;
import de.useweb.backend.ocl.diagnostics.SourceRange;

@Service
public class ModelCommandService {
    private static final String MODEL = "MODEL";
    private static final String SNAPSHOT = "SNAPSHOT";

    private final ProjectService projectService;
    private final UmlModelService umlModelService;
    private final ObjectModelService objectModelService;
    private final ObjectMapper objectMapper;
    private final OclDiagnosticMapper diagnosticMapper;
    private final OclDefinitionApplicationService definitionService;
    private final OclParser parser = new OclParser();
    private final OclTypeChecker typeChecker = new OclTypeChecker();
    private final StructuredUmlTypeService structuredTypes = new StructuredUmlTypeService();
    private final Map<String, Object> projectLocks = new ConcurrentHashMap<>();

    public ModelCommandService(ProjectService projectService, UmlModelService umlModelService,
            ObjectModelService objectModelService, ObjectMapper objectMapper, OclDiagnosticMapper diagnosticMapper,
            OclDefinitionApplicationService definitionService) {
        this.projectService = projectService;
        this.umlModelService = umlModelService;
        this.objectModelService = objectModelService;
        this.objectMapper = objectMapper;
        this.diagnosticMapper = diagnosticMapper;
        this.definitionService = definitionService;
    }

    public MutationResultDto createClass(ProjectId projectId, MutationCommandRequestDto request) {
        UmlClassDto draft = draft(request, UmlClassDto.class);
        return execute(projectId, "CREATE_CLASS", MODEL, request, () -> umlModelService.createClass(projectId, draft));
    }

    public MutationResultDto updateClass(ProjectId projectId, UmlClassId classId, MutationCommandRequestDto request) {
        UmlClassDto draft = draft(request, UmlClassDto.class);
        return execute(projectId, "UPDATE_CLASS", MODEL, request, () -> umlModelService.updateClass(projectId, classId, draft));
    }

    public MutationResultDto setGeneralizations(ProjectId projectId, UmlClassId classId,
            MutationCommandRequestDto request) {
        List<String> supertypeIds = stringList(request.draft(), "supertypeIds");
        UmlClassDto current = umlModelService.getUmlModel(projectId).classes().stream()
                .filter(candidate -> candidate.id().equals(classId.value())).findFirst()
                .orElseThrow(() -> notFound("CLASS", classId.value(), request.draft()));
        UmlClassDto replacement = new UmlClassDto(current.id(), current.name(), current.attributes(), current.operations(),
                current.abstractClass(), supertypeIds, current.visibility(), current.packageId(), current.qualifiedName());
        return execute(projectId, "SET_GENERALIZATIONS", MODEL, request,
                () -> umlModelService.updateClass(projectId, classId, replacement));
    }

    public MutationResultDto updateOperation(ProjectId projectId, UmlClassId classId, UmlOperationId operationId,
            MutationCommandRequestDto request) {
        UmlOperationDto draft = draft(request, UmlOperationDto.class);
        UmlClassDto owner = umlModelService.getUmlModel(projectId).classes().stream()
                .filter(type -> type.id().equals(classId.value())).findFirst()
                .orElseThrow(() -> notFound("CLASS", classId.value(), request.draft()));
        if (owner.operations().stream().noneMatch(operation -> operation.id().equals(operationId.value()))) {
            throw notFound("OPERATION", operationId.value(), request.draft());
        }
        return execute(projectId, "UPDATE_OPERATION", MODEL, request, () -> {
            validateOperationDraft(projectId, classId, draft, request.draft());
            return umlModelService.updateOperation(projectId, classId, operationId, draft);
        });
    }

    public MutationResultDto createOperation(ProjectId projectId, UmlClassId classId,
            MutationCommandRequestDto request) {
        UmlOperationDto draft = draft(request, UmlOperationDto.class);
        return execute(projectId, "CREATE_OPERATION", MODEL, request, () -> {
            validateOperationDraft(projectId, classId, draft, request.draft());
            return umlModelService.addOperation(projectId, classId, draft);
        });
    }

    public MutationResultDto updateAttribute(ProjectId projectId, UmlClassId classId, UmlAttributeId attributeId,
            MutationCommandRequestDto request) {
        UmlAttributeDto draft = draft(request, UmlAttributeDto.class);
        UmlClassDto owner = umlModelService.getUmlModel(projectId).classes().stream()
                .filter(type -> type.id().equals(classId.value())).findFirst()
                .orElseThrow(() -> notFound("CLASS", classId.value(), request.draft()));
        if (owner.attributes().stream().noneMatch(attribute -> attribute.id().equals(attributeId.value()))) {
            throw notFound("ATTRIBUTE", attributeId.value(), request.draft());
        }
        return execute(projectId, "UPDATE_ATTRIBUTE", MODEL, request, () -> {
            validateAttributeDraft(projectId, classId, draft, request.draft());
            return umlModelService.updateAttribute(projectId, classId, attributeId, draft);
        });
    }

    public MutationResultDto createAttribute(ProjectId projectId, UmlClassId classId,
            MutationCommandRequestDto request) {
        UmlAttributeDto draft = draft(request, UmlAttributeDto.class);
        return execute(projectId, "CREATE_ATTRIBUTE", MODEL, request, () -> {
            validateAttributeDraft(projectId, classId, draft, request.draft());
            return umlModelService.addAttribute(projectId, classId, draft);
        });
    }

    public MutationResultDto setFeatureRedefinition(ProjectId projectId, UmlClassId classId,
            MutationCommandRequestDto request) {
        JsonNode draft = request == null ? null : request.draft();
        String kind = requiredText(draft, "featureKind").toUpperCase();
        String localFeatureId = requiredText(draft, "localFeatureId");
        List<String> targets = stringList(draft, "redefinedFeatureIds");
        if (!Set.of("ATTRIBUTE", "OPERATION").contains(kind)) {
            throw new CommandException(400, "INVALID_FEATURE_KIND", "Unknown feature kind: " + kind,
                    "Die Feature-Art muss ATTRIBUTE oder OPERATION sein.",
                    Map.of("draft", nullSafe(draft), "field", "featureKind"));
        }
        UmlClassDto owner = umlModelService.getUmlModel(projectId).classes().stream()
                .filter(type -> type.id().equals(classId.value())).findFirst()
                .orElseThrow(() -> notFound("CLASS", classId.value(), draft));
        boolean featureExists = "ATTRIBUTE".equals(kind)
                ? owner.attributes().stream().anyMatch(feature -> feature.id().equals(localFeatureId))
                : owner.operations().stream().anyMatch(feature -> feature.id().equals(localFeatureId));
        if (!featureExists) throw notFound(kind, localFeatureId, draft);
        List<String> supertypes = draft.has("supertypeIds") ? stringList(draft, "supertypeIds") : null;
        return execute(projectId, "SET_FEATURE_REDEFINITION", MODEL, request,
                () -> umlModelService.updateFeatureRedefinition(projectId, classId, kind, localFeatureId, targets, supertypes));
    }

    public MutationResultDto createAssociation(ProjectId projectId, MutationCommandRequestDto request) {
        UmlAssociationDto draft = draft(request, UmlAssociationDto.class);
        return execute(projectId, "CREATE_ASSOCIATION", MODEL, request,
                () -> umlModelService.createAssociation(projectId, draft));
    }

    public MutationResultDto updateAssociation(ProjectId projectId, UmlAssociationId associationId,
            MutationCommandRequestDto request) {
        UmlAssociationDto draft = draft(request, UmlAssociationDto.class);
        if (umlModelService.getUmlModel(projectId).associations().stream()
                .noneMatch(association -> association.id().equals(associationId.value()))) {
            throw notFound("ASSOCIATION", associationId.value(), request.draft());
        }
        return execute(projectId, "UPDATE_ASSOCIATION", MODEL, request,
                () -> umlModelService.updateAssociation(projectId, associationId, draft));
    }

    public MutationResultDto createAssociationClass(ProjectId projectId, UmlAssociationId associationId,
            MutationCommandRequestDto request) {
        UmlClassDto draft = draft(request, UmlClassDto.class);
        if (umlModelService.getUmlModel(projectId).associations().stream()
                .noneMatch(association -> association.id().equals(associationId.value()))) {
            throw notFound("ASSOCIATION", associationId.value(), request == null ? null : request.draft());
        }
        return execute(projectId, "CREATE_ASSOCIATION_CLASS", MODEL, request,
                () -> umlModelService.createAssociationClass(projectId, associationId, draft));
    }

    public MutationResultDto createInvariant(ProjectId projectId, MutationCommandRequestDto request) {
        UmlInvariantDto draft = draft(request, UmlInvariantDto.class);
        validateInvariant(projectId, draft, request.draft());
        return execute(projectId, "CREATE_INVARIANT", MODEL, request,
                () -> umlModelService.createInvariant(projectId, draft));
    }

    public MutationResultDto updateInvariant(ProjectId projectId, UmlInvariantId invariantId,
            MutationCommandRequestDto request) {
        UmlInvariantDto draft = draft(request, UmlInvariantDto.class);
        validateInvariant(projectId, draft, request.draft());
        return execute(projectId, "UPDATE_INVARIANT", MODEL, request,
                () -> umlModelService.updateInvariant(projectId, invariantId, draft));
    }

    public MutationResultDto createDataType(ProjectId projectId, MutationCommandRequestDto request) {
        UmlDataTypeDto draft = draft(request, UmlDataTypeDto.class);
        return execute(projectId, "CREATE_DATATYPE", MODEL, request,
                () -> umlModelService.createDataType(projectId, draft));
    }

    public MutationResultDto updateDataType(ProjectId projectId, UmlDataTypeId dataTypeId,
            MutationCommandRequestDto request) {
        UmlDataTypeDto draft = draft(request, UmlDataTypeDto.class);
        return execute(projectId, "UPDATE_DATATYPE", MODEL, request, () -> {
            Project project = projectService.loadProject(projectId);
            UmlDataType current = requireDataType(project, dataTypeId, request.draft());
            Set<String> retained = safe(draft.properties()).stream().map(UmlDataTypePropertyDto::id)
                    .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
            for (UmlDataTypeProperty removed : current.properties().stream()
                    .filter(property -> !retained.contains(property.id())).toList()) {
                rejectBlockedDataTypePropertyRemoval(project, current, removed, request.draft());
            }
            return umlModelService.updateDataType(projectId, dataTypeId, draft);
        });
    }

    public DeleteImpactDto dataTypePropertyDeleteImpact(ProjectId projectId, UmlDataTypeId dataTypeId,
            String propertyId) {
        return dataTypePropertyDeleteImpact(projectService.loadProject(projectId), dataTypeId, propertyId, null);
    }

    public MutationResultDto deleteDataTypeProperty(ProjectId projectId, UmlDataTypeId dataTypeId, String propertyId,
            DeleteCommandRequestDto request) {
        JsonNode draft = objectMapper.valueToTree(request);
        synchronized (lock(projectId)) {
            Project before = projectService.loadProject(projectId);
            DeleteImpactDto impact = dataTypePropertyDeleteImpact(before, dataTypeId, propertyId, draft);
            requireDeleteRevision(before, request == null ? null : request.expectedRevision(), draft, impact);
            List<String> selected = request == null ? List.of() : request.cascadeReferenceIds();
            if (!selected.isEmpty()) {
                throw new CommandException(400, "INVALID_CASCADE_SELECTION", "DataType property cascades are not allowed",
                        "DataType-Property-Referenzen duerfen nicht automatisch geloescht werden.",
                        Map.of("draft", nullSafe(draft), "unknownReferenceIds", selected,
                                "allowedReferenceIds", List.of(), "currentImpact", impact));
            }
            if (!impact.references().isEmpty()) {
                throw deleteBlocked(draft, impact);
            }
            try {
                UmlDataTypeDto result = umlModelService.deleteDataTypeProperty(projectId, dataTypeId, propertyId);
                Project saved = projectService.loadProject(projectId);
                CommandElementReferenceDto owner = ref("datatype:" + dataTypeId.value(), "DATATYPE",
                        dataTypeId.value(), result.name(), "umlModel.dataTypes", "OWNS_DELETED_PROPERTY", false);
                return new MutationResultDto("DELETE_DATATYPE_PROPERTY", MODEL, revision(saved), result,
                        List.of(impact.target(), owner));
            } catch (CommandException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw commandFailure(exception, draft, before, "DELETE_DATATYPE_PROPERTY");
            }
        }
    }

    public MutationResultDto createPackage(ProjectId projectId, MutationCommandRequestDto request) {
        UmlPackageDto draft = draft(request, UmlPackageDto.class);
        return execute(projectId, "CREATE_PACKAGE", MODEL, request,
                () -> umlModelService.createPackage(projectId, draft));
    }

    public MutationResultDto updatePackage(ProjectId projectId, UmlPackageId packageId,
            MutationCommandRequestDto request) {
        UmlPackageDto draft = draft(request, UmlPackageDto.class);
        if (umlModelService.getUmlModel(projectId).packages().stream()
                .noneMatch(candidate -> candidate.id().equals(packageId.value()))) {
            throw notFound("PACKAGE", packageId.value(), request.draft());
        }
        return execute(projectId, "UPDATE_PACKAGE", MODEL, request,
                () -> umlModelService.updatePackage(projectId, packageId, draft));
    }

    public MutationResultDto createImport(ProjectId projectId, MutationCommandRequestDto request) {
        UmlModelImportDto draft = draft(request, UmlModelImportDto.class);
        return execute(projectId, "CREATE_IMPORT", MODEL, request,
                () -> umlModelService.createImport(projectId, draft));
    }

    public MutationResultDto updateImport(ProjectId projectId, UmlModelImportId importId,
            MutationCommandRequestDto request) {
        UmlModelImportDto draft = draft(request, UmlModelImportDto.class);
        if (umlModelService.getUmlModel(projectId).imports().stream()
                .noneMatch(candidate -> candidate.id().equals(importId.value()))) {
            throw notFound("IMPORT", importId.value(), request.draft());
        }
        return execute(projectId, "UPDATE_IMPORT", MODEL, request,
                () -> umlModelService.updateImport(projectId, importId, draft));
    }

    public MutationResultDto createEnumeration(ProjectId projectId, MutationCommandRequestDto request) {
        UmlEnumerationDto draft = draft(request, UmlEnumerationDto.class);
        return execute(projectId, "CREATE_ENUMERATION", MODEL, request,
                () -> umlModelService.createEnumeration(projectId, draft));
    }

    public MutationResultDto updateEnumeration(ProjectId projectId, UmlEnumerationId enumerationId,
            MutationCommandRequestDto request) {
        UmlEnumerationDto draft = draft(request, UmlEnumerationDto.class);
        Project currentProject = projectService.loadProject(projectId);
        UmlEnumeration current = currentProject.umlModel().enumerations().stream()
                .filter(value -> value.id().equals(enumerationId)).findFirst()
                .orElseThrow(() -> notFound("ENUMERATION", enumerationId.value(), request.draft()));
        String draftPackageId = draft.packageId() == null || draft.packageId().isBlank() ? null : draft.packageId();
        String currentPackageId = current.packageId() == null ? null : current.packageId().value();
        if (!java.util.Objects.equals(current.name(), draft.name())
                || !java.util.Objects.equals(currentPackageId, draftPackageId)) {
            List<CommandElementReferenceDto> blockers = new ArrayList<>();
            enumerationReferences(currentProject, enumerationId.value(), blockers);
            if (!blockers.isEmpty()) {
                throw new CommandException(409, "ENUMERATION_REFERENCED",
                        "Referenced Enumeration cannot be renamed or moved",
                        "Eine verwendete Enumeration kann nicht umbenannt oder verschoben werden.",
                        Map.of("draft", request.draft(), "enumerationId", enumerationId.value(),
                                "blockers", List.copyOf(blockers)));
            }
        }
        draft = withStableLiteralIds(draft, current);
        Map<String, String> replacementNames = safeLiteralNames(draft);
        for (UmlEnumerationLiteral literal : current.literalDefinitions()) {
            String replacementName = replacementNames.get(literal.id().value());
            if (replacementName == null || !replacementName.equals(literal.name())) {
                List<CommandElementReferenceDto> blockers = enumerationLiteralReferences(currentProject, literal.id().value());
                if (!blockers.isEmpty()) {
                    throw new CommandException(409, "ENUMERATION_LITERAL_REFERENCED",
                            "Referenced Enumeration literal cannot be removed or renamed",
                            "Ein verwendetes Enumeration-Literal kann nicht entfernt oder umbenannt werden.",
                            Map.of("draft", request.draft(), "literalId", literal.id().value(), "blockers", blockers));
                }
            }
        }
        UmlEnumerationDto normalizedDraft = draft;
        return execute(projectId, "UPDATE_ENUMERATION", MODEL, request,
                () -> umlModelService.updateEnumeration(projectId, enumerationId, normalizedDraft));
    }

    public MutationResultDto createDefinition(ProjectId projectId, MutationCommandRequestDto request) {
        OclDefinitionElementDto draft = draft(request, OclDefinitionElementDto.class);
        return execute(projectId, "CREATE_DEFINITION", MODEL, request,
                () -> definitionService.create(projectId, draft, request.draft()));
    }

    public MutationResultDto updateDefinition(ProjectId projectId, OclDefinitionElementId definitionId,
            MutationCommandRequestDto request) {
        OclDefinitionElementDto draft = draft(request, OclDefinitionElementDto.class);
        return execute(projectId, "UPDATE_DEFINITION", MODEL, request,
                () -> definitionService.update(projectId, definitionId, draft, request.draft()));
    }

    public DeleteImpactDto deleteImpact(ProjectId projectId, String elementType, String elementId) {
        Project project = projectService.loadProject(projectId);
        String type = elementType.toUpperCase();
        CommandElementReferenceDto target = target(project, type, elementId);
        List<CommandElementReferenceDto> references = references(project, type, elementId);
        return new DeleteImpactDto(scope(type), revision(project), target, references,
                references.stream().anyMatch(reference -> !reference.cascadeAllowed()));
    }

    public MutationResultDto delete(ProjectId projectId, String elementType, String elementId,
            DeleteCommandRequestDto request) {
        String type = elementType.toUpperCase();
        JsonNode draft = objectMapper.valueToTree(request);
        synchronized (lock(projectId)) {
            Project before = projectService.loadProject(projectId);
            UmlClass operationOwner = "OPERATION".equals(type) ? operationOwner(before, elementId, draft) : null;
            DeleteImpactDto impact = deleteImpact(projectId, type, elementId);
            if (operationOwner == null) {
                requireRevision(before, request.expectedRevision(), draft, scope(type));
            } else {
                requireDeleteRevision(before, request.expectedRevision(), draft, impact);
            }
            Set<String> selected = Set.copyOf(request.cascadeReferenceIds());
            Set<String> allowed = impact.references().stream().filter(CommandElementReferenceDto::cascadeAllowed)
                    .map(CommandElementReferenceDto::referenceId).collect(java.util.stream.Collectors.toSet());
            List<String> unknownSelections = selected.stream().filter(referenceId -> !allowed.contains(referenceId)).toList();
            if (!unknownSelections.isEmpty()) {
                throw new CommandException(400, "INVALID_CASCADE_SELECTION", "Cascade selection is not allowed",
                        "Die Cascade-Auswahl enthaelt nicht erlaubte Referenzen.",
                        Map.of("draft", draft, "unknownReferenceIds", unknownSelections, "allowedReferenceIds", allowed));
            }
            List<CommandElementReferenceDto> blockers = impact.references().stream()
                    .filter(reference -> !reference.cascadeAllowed() || !selected.contains(reference.referenceId())).toList();
            if (!blockers.isEmpty()) {
                throw new CommandException(409, "DELETE_BLOCKED", "References block deletion",
                        "Verbleibende Referenzen blockieren das Loeschen.",
                        Map.of("draft", draft, "target", impact.target(), "blockers", blockers,
                                "revision", impact.revision()));
            }
            try {
                Object result = switch (type) {
                    case "CLASS" -> umlModelService.deleteClass(projectId, new UmlClassId(elementId));
                    case "ATTRIBUTE" -> umlModelService.deleteAttribute(projectId,
                            new UmlClassId(requiredOwner(draft, "classId")), new UmlAttributeId(elementId));
                    case "OPERATION" -> umlModelService.deleteOperation(projectId,
                            operationOwner.id(), new UmlOperationId(elementId));
                    case "ASSOCIATION" -> umlModelService.deleteAssociation(projectId, new UmlAssociationId(elementId));
                    case "INVARIANT" -> umlModelService.deleteInvariant(projectId, new UmlInvariantId(elementId));
                    case "OBJECT" -> objectModelService.deleteObjectWithDependencies(projectId,
                            new de.useweb.backend.domain.snapshot.ObjectInstanceId(elementId));
                    case "GENERALIZATION" -> deleteGeneralization(projectId, draft, elementId);
                    case "DEFINITION" -> definitionService.delete(projectId, new OclDefinitionElementId(elementId));
                    case "ENUMERATION" -> umlModelService.deleteEnumeration(projectId, new UmlEnumerationId(elementId));
                    case "DATATYPE" -> umlModelService.deleteDataType(projectId, new UmlDataTypeId(elementId));
                    case "ENUMERATION_LITERAL" -> umlModelService.deleteEnumerationLiteral(projectId,
                            new UmlEnumerationId(request.enumerationId() == null || request.enumerationId().isBlank()
                                    ? requiredOwner(draft, "enumerationId") : request.enumerationId()),
                            new UmlEnumerationLiteralId(elementId));
                    case "PACKAGE" -> umlModelService.deletePackageWithDependencies(projectId,
                            new UmlPackageId(elementId));
                    case "IMPORT" -> umlModelService.deleteImport(projectId, new UmlModelImportId(elementId));
                    default -> throw new CommandException(400, "UNSUPPORTED_DELETE_TARGET",
                            "Unsupported delete target: " + type, "Dieses Modellelement kann nicht geloescht werden.",
                            Map.of("draft", draft, "elementType", type, "elementId", elementId));
                };
                Project saved = projectService.loadProject(projectId);
                List<CommandElementReferenceDto> affected = new ArrayList<>();
                affected.add(impact.target());
                if (operationOwner != null) {
                    affected.add(ref("owner:class:" + operationOwner.id().value(), "CLASS",
                            operationOwner.id().value(), operationOwner.name(), "umlModel.classes.operations",
                            "OWNS_DELETED_OPERATION", false));
                }
                return new MutationResultDto("DELETE_" + type, scope(type), revision(saved), result,
                        List.copyOf(affected));
            } catch (CommandException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw commandFailure(exception, draft, before, "DELETE_" + type);
            }
        }
    }

    private Object deleteGeneralization(ProjectId projectId, JsonNode draft, String supertypeId) {
        String classId = requiredOwner(draft, "classId");
        UmlClassDto current = umlModelService.getUmlModel(projectId).classes().stream()
                .filter(candidate -> candidate.id().equals(classId)).findFirst()
                .orElseThrow(() -> notFound("CLASS", classId, draft));
        List<String> supertypes = current.superClassIds().stream().filter(id -> !id.equals(supertypeId)).toList();
        if (supertypes.size() == current.superClassIds().size()) throw notFound("GENERALIZATION", supertypeId, draft);
        return umlModelService.updateClass(projectId, new UmlClassId(classId), new UmlClassDto(current.id(), current.name(),
                current.attributes(), current.operations(), current.abstractClass(), supertypes, current.visibility(),
                current.packageId(), current.qualifiedName()));
    }

    private MutationResultDto execute(ProjectId projectId, String command, String scope,
            MutationCommandRequestDto request, Supplier<Object> action) {
        JsonNode draft = request == null ? null : request.draft();
        synchronized (lock(projectId)) {
            Project before = projectService.loadProject(projectId);
            requireRevision(before, request == null ? null : request.expectedRevision(), draft, scope);
            try {
                Object result = action.get();
                Project saved = projectService.loadProject(projectId);
                return new MutationResultDto(command, scope, revision(saved), result, affectedElements(command, result));
            } catch (CommandException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw commandFailure(exception, draft, before, command);
            }
        }
    }

    private void validateInvariant(ProjectId projectId, UmlInvariantDto draft, JsonNode rawDraft) {
        if (draft.expression() == null || draft.expression().text() == null) return;
        OclParseResult parsed = parser.parse(draft.expression().text());
        List<OclDiagnosticDto> diagnostics;
        if (!parsed.success()) {
            diagnostics = diagnosticMapper.toDto(parsed.diagnostics(), draft.expression().id(), "INVARIANT", null);
        } else {
            OclTypecheckResult checked = typeChecker.checkInvariant(projectService.loadProject(projectId).umlModel(),
                    new UmlClassId(draft.contextClassId()), parsed.ast());
            diagnostics = diagnosticMapper.toDto(checked.diagnostics(), draft.expression().id(), "INVARIANT", null);
        }
        if (!diagnostics.isEmpty()) {
            throw new CommandException(400, "OCL_COMPILE_FAILED", "Invariant does not compile",
                    "Die Invariante enthaelt Syntax- oder Typfehler.",
                    Map.of("draft", rawDraft, "diagnostics", diagnostics,
                            "target", new CommandElementReferenceDto("invariant:draft", "INVARIANT",
                                    draft.id(), draft.name(), "expression", "CONTAINS_ERROR", false)));
        }
    }

    /**
     * Validates expressions before the model service writes the draft. This keeps a
     * failing attribute or operation command side-effect free while retaining the
     * complete submitted draft in the command error.
     */
    private void validateAttributeDraft(ProjectId projectId, UmlClassId classId, UmlAttributeDto draft,
            JsonNode rawDraft) {
        if (!Boolean.TRUE.equals(draft.derived()) && blank(draft.initExpression())) return;
        Project project = projectService.loadProject(projectId);
        UmlClass owner = project.umlModel().findClass(classId).orElseThrow(() -> notFound("CLASS", classId.value(), rawDraft));
        TypeEnvironment base = definitionEnvironment(project, owner, Map.of(), null);
        OclType expected = featureType(draft.type(), base);
        List<OclDiagnosticDto> diagnostics = new ArrayList<>();
        if (Boolean.TRUE.equals(draft.derived())) {
            diagnostics.addAll(expressionDiagnostics(base, draft.deriveExpression(), expected,
                    draft.id(), "ATTRIBUTE_DERIVE", "deriveExpression"));
        } else {
            diagnostics.addAll(expressionDiagnostics(base, draft.initExpression(), expected,
                    draft.id(), "ATTRIBUTE_INIT", "initExpression"));
        }
        failFeatureDraft("ATTRIBUTE", draft.id(), draft.name(), rawDraft, diagnostics);
    }

    private void validateOperationDraft(ProjectId projectId, UmlClassId classId, UmlOperationDto draft,
            JsonNode rawDraft) {
        Project project = projectService.loadProject(projectId);
        UmlClass owner = project.umlModel().findClass(classId).orElseThrow(() -> notFound("CLASS", classId.value(), rawDraft));
        Map<String, OclType> parameters = new LinkedHashMap<>();
        TypeEnvironment base = definitionEnvironment(project, owner, Map.of(), null);
        for (UmlParameterDto parameter : safe(draft.parameters())) {
            if (parameters.containsKey(parameter.name())) {
                throw operationDraftError("OPERATION_PARAMETER_CONFLICT",
                        "Operation parameter names must be unique",
                        "Parameternamen einer Operation muessen eindeutig sein.", draft, rawDraft,
                        parameter.id(), "parameters");
            }
            parameters.put(parameter.name(), featureType(parameter.type(), base));
        }
        OclType returnType = featureType(draft.returnType(), base);
        List<OclDiagnosticDto> diagnostics = new ArrayList<>();
        if (!blank(draft.bodyExpression())) {
            diagnostics.addAll(expressionDiagnostics(definitionEnvironment(project, owner, parameters, null),
                    draft.bodyExpression(), returnType, draft.id(), "OPERATION_BODY", "bodyExpression"));
        }
        for (UmlOperationContractDto contract : safe(draft.contracts())) {
            OperationConstraintKind kind;
            if ("POST".equalsIgnoreCase(contract.kind())) {
                kind = OperationConstraintKind.POSTCONDITION;
            } else if ("PRE".equalsIgnoreCase(contract.kind())) {
                kind = OperationConstraintKind.PRECONDITION;
            } else {
                throw operationDraftError("INVALID_OPERATION_CONTRACT_KIND",
                        "Operation contract kind must be PRE or POST",
                        "Die Art eines Operationsvertrags muss PRE oder POST sein.", draft, rawDraft,
                        contract.id(), "contracts." + (contract.id() == null ? "draft" : contract.id()) + ".kind");
            }
            Map<String, OclType> bindings = new LinkedHashMap<>(parameters);
            if (kind == OperationConstraintKind.POSTCONDITION && !returnType.sameTypeAs(OclType.VOID)) {
                bindings.put("result", returnType);
            }
            diagnostics.addAll(expressionDiagnostics(definitionEnvironment(project, owner, bindings, kind),
                    contract.expression(), OclType.BOOLEAN, contract.id(), "OPERATION_CONTRACT",
                    "contracts." + (contract.id() == null ? "draft" : contract.id()) + ".expression"));
        }
        failFeatureDraft("OPERATION", draft.id(), draft.name(), rawDraft, diagnostics);
    }

    private CommandException operationDraftError(String code, String message, String userMessage,
            UmlOperationDto draft, JsonNode rawDraft, String elementId, String field) {
        String stableId = elementId == null || elementId.isBlank() ? "draft" : elementId;
        return new CommandException(400, code, message, userMessage,
                Map.of("draft", rawDraft, "field", field,
                        "targets", List.of(ref("operation-draft:" + stableId, "OPERATION",
                                draft.id(), draft.name(), field, "CONTAINS_ERROR", false))));
    }

    private TypeEnvironment definitionEnvironment(Project project, UmlClass owner, Map<String, OclType> variables,
            OperationConstraintKind contractKind) {
        return new TypeEnvironment(project.umlModel(), owner, variables, contractKind,
                new OclDefinitionService(project.umlModel(), definitionService.runtimeDefinitions(project)));
    }

    private OclType featureType(String typeName, TypeEnvironment environment) {
        return UmlType.VOID.name().equals(typeName) ? OclType.VOID
                : OclType.fromUmlType(new UmlType(typeName), environment);
    }

    private List<OclDiagnosticDto> expressionDiagnostics(TypeEnvironment environment, String expression,
            OclType expected, String sourceId, String sourceKind, String field) {
        if (blank(expression)) return List.of();
        OclParseResult parsed = parser.parse(expression);
        if (!parsed.success()) return diagnosticMapper.toDto(parsed.diagnostics(), sourceId, sourceKind, null);
        OclTypecheckResult checked = typeChecker.checkExpression(environment, parsed.ast());
        List<OclDiagnosticDto> diagnostics = new ArrayList<>(
                diagnosticMapper.toDto(checked.diagnostics(), sourceId, sourceKind, null));
        if (diagnostics.isEmpty() && expected != null && !checked.resultType().conformsTo(expected)) {
            var range = diagnosticMapper.toDto(parsed.ast().sourceRange());
            diagnostics.add(new OclDiagnosticDto(null, "VALIDATION_ERROR", "TYPECHECK", "OCL_FEATURE_TYPE_MISMATCH",
                    "ERROR", "Expression result type '" + checked.resultType().displayName()
                            + "' does not conform to '" + expected.displayName() + "'.",
                    "Der Ausdruck liefert nicht den erwarteten Typ.",
                    "Expression result type does not conform to the feature type.", range,
                    new SourceReferenceDto(sourceId, sourceKind, null, range), List.of(expected.displayName()),
                    checked.resultType().displayName(), List.of(), Map.of("field", field), null));
        }
        return List.copyOf(diagnostics);
    }

    private void failFeatureDraft(String type, String id, String name, JsonNode rawDraft,
            List<OclDiagnosticDto> diagnostics) {
        if (diagnostics.isEmpty()) return;
        String featureId = id == null || id.isBlank() ? "draft" : id;
        throw new CommandException(400, "OCL_FEATURE_COMPILE_FAILED", type + " draft does not compile",
                "Der Entwurf enthaelt Syntax- oder Typfehler.",
                Map.of("draft", nullSafe(rawDraft), "diagnostics", List.copyOf(diagnostics), "targets", List.of(
                        ref("draft:" + type.toLowerCase() + ":" + featureId, type, featureId,
                                name == null || name.isBlank() ? featureId : name, null, "CONTAINS_ERROR", false))));
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private List<CommandElementReferenceDto> affectedElements(String command, Object result) {
        String relation = command.startsWith("CREATE_") ? "CREATED" : "UPDATED";
        if (result instanceof AssociationClassAggregateDto aggregate) {
            List<CommandElementReferenceDto> affected = new ArrayList<>();
            affected.add(ref("association:" + aggregate.association().id(), "ASSOCIATION",
                    aggregate.association().id(), aggregate.association().name(), "umlModel.associations",
                    "BOUND_ASSOCIATION_CLASS", false));
            affected.add(ref("class:" + aggregate.associationClass().id(), "CLASS",
                    aggregate.associationClass().id(), aggregate.associationClass().name(), "umlModel.classes",
                    "CREATED", false));
            safe(aggregate.associationClass().attributes()).forEach(attribute -> affected.add(ref(
                    "attribute:" + attribute.id(), "ATTRIBUTE", attribute.id(), attribute.name(),
                    "umlModel.classes[" + aggregate.associationClass().id() + "].attributes", "CREATED", false)));
            safe(aggregate.associationClass().operations()).forEach(operation -> affected.add(ref(
                    "operation:" + operation.id(), "OPERATION", operation.id(), operation.name(),
                    "umlModel.classes[" + aggregate.associationClass().id() + "].operations", "CREATED", false)));
            return List.copyOf(affected);
        }
        if (result instanceof UmlAttributeDto attribute) {
            return List.of(ref("attribute:" + attribute.id(), "ATTRIBUTE", attribute.id(), attribute.name(),
                    "umlModel.classes.attributes", relation, false));
        }
        if (result instanceof UmlOperationDto operation) {
            return List.of(ref("operation:" + operation.id(), "OPERATION", operation.id(), operation.name(),
                    "umlModel.classes.operations", relation, false));
        }
        if (result instanceof UmlAssociationDto association) {
            List<CommandElementReferenceDto> affected = new ArrayList<>();
            affected.add(ref("association:" + association.id(), "ASSOCIATION", association.id(), association.name(),
                    "umlModel.associations", relation, false));
            safe(association.ends()).forEach(end -> {
                affected.add(ref("association-end:" + end.id(), "ASSOCIATION_END", end.id(), end.roleName(),
                        "umlModel.associations[" + association.id() + "].ends", relation, false));
                for (UmlQualifierDefinitionDto qualifier : safe(end.qualifiers())) {
                    affected.add(ref("qualifier:" + qualifier.id(), "QUALIFIER", qualifier.id(), qualifier.name(),
                            "umlModel.associations[" + association.id() + "].ends[" + end.id() + "].qualifiers",
                            relation, false));
                }
            });
            return List.copyOf(affected);
        }
        if (result instanceof UmlPackageDto umlPackage) {
            return List.of(ref("package:" + umlPackage.id(), "PACKAGE", umlPackage.id(), umlPackage.qualifiedName(),
                    "umlModel.packages", relation, false));
        }
        if (result instanceof UmlModelImportDto modelImport) {
            String name = modelImport.alias() == null ? modelImport.id() : modelImport.alias();
            return List.of(ref("import:" + modelImport.id(), "IMPORT", modelImport.id(), name,
                    "umlModel.imports", relation, false));
        }
        return List.of();
    }

    private List<CommandElementReferenceDto> references(Project project, String type, String id) {
        List<CommandElementReferenceDto> refs = new ArrayList<>();
        switch (type) {
            case "CLASS" -> classReferences(project, id, refs);
            case "ATTRIBUTE" -> featureReferences(project, id, true, refs);
            case "OPERATION" -> operationReferences(project, id, refs);
            case "ASSOCIATION" -> project.objectModel().links().stream()
                    .filter(link -> link.associationId().value().equals(id))
                    .forEach(link -> refs.add(ref("link:" + link.id().value(), "OBJECT_LINK", link.id().value(),
                            link.id().value(), "objectModel.links", "INSTANCE_OF", true)));
            case "OBJECT" -> project.objectModel().links().stream()
                    .filter(link -> link.ends().stream().anyMatch(end -> end.objectId().value().equals(id))
                            || link.associationClassObjectId() != null && link.associationClassObjectId().value().equals(id))
                    .forEach(link -> refs.add(ref("link:" + link.id().value(), "OBJECT_LINK", link.id().value(),
                            link.id().value(), "objectModel.links", "REFERENCES", true)));
            case "GENERALIZATION", "INVARIANT" -> { }
            case "DEFINITION" -> definitionReferences(project, id, refs);
            case "ENUMERATION" -> enumerationReferences(project, id, refs);
            case "DATATYPE" -> dataTypeReferences(project, id, refs);
            case "ENUMERATION_LITERAL" -> refs.addAll(enumerationLiteralReferences(project, id));
            case "PACKAGE" -> packageReferences(project, id, refs);
            case "IMPORT" -> importReferences(project, id, refs);
            default -> { }
        }
        return refs.stream().collect(java.util.stream.Collectors.toMap(CommandElementReferenceDto::referenceId,
                value -> value, (left, right) -> left, LinkedHashMap::new)).values().stream().toList();
    }

    private void packageReferences(Project project, String id, List<CommandElementReferenceDto> refs) {
        UmlPackage target = project.umlModel().findPackage(new UmlPackageId(id))
                .orElseThrow(() -> notFound("PACKAGE", id, null));
        String prefix = target.qualifiedName() + "::";
        Set<String> packageIds = project.umlModel().packages().stream()
                .filter(candidate -> candidate.id().value().equals(id) || candidate.qualifiedName().startsWith(prefix))
                .map(candidate -> candidate.id().value()).collect(java.util.stream.Collectors.toSet());
        project.umlModel().packages().stream().filter(candidate -> !candidate.id().value().equals(id)
                && packageIds.contains(candidate.id().value())).forEach(candidate -> refs.add(ref(
                        "package:" + candidate.id().value(), "PACKAGE", candidate.id().value(), candidate.qualifiedName(),
                        "umlModel.packages", "CONTAINED_PACKAGE", true)));
        Set<String> classIds = project.umlModel().classes().stream()
                .filter(candidate -> candidate.packageId() != null && packageIds.contains(candidate.packageId().value()))
                .map(candidate -> candidate.id().value()).collect(java.util.stream.Collectors.toSet());
        project.umlModel().classes().stream().filter(candidate -> classIds.contains(candidate.id().value()))
                .forEach(candidate -> refs.add(ref("class:" + candidate.id().value(), "CLASS", candidate.id().value(),
                        candidate.name(), "umlModel.classes", "OWNED_CLASSIFIER", true)));
        project.umlModel().enumerations().stream().filter(candidate -> candidate.packageId() != null
                && packageIds.contains(candidate.packageId().value())).forEach(candidate -> refs.add(ref(
                        "enumeration:" + candidate.id().value(), "ENUMERATION", candidate.id().value(), candidate.name(),
                        "umlModel.enumerations", "OWNED_CLASSIFIER", true)));
        project.umlModel().dataTypes().stream().filter(candidate -> candidate.packageId() != null
                && packageIds.contains(candidate.packageId().value())).forEach(candidate -> refs.add(ref(
                        "datatype:" + candidate.id().value(), "DATATYPE", candidate.id().value(), candidate.name(),
                        "umlModel.dataTypes", "OWNED_CLASSIFIER", true)));
        project.umlModel().classes().stream().filter(candidate -> !classIds.contains(candidate.id().value()))
                .filter(candidate -> candidate.superClassIds().stream().anyMatch(parent -> classIds.contains(parent.value())))
                .forEach(candidate -> refs.add(ref("generalization:" + candidate.id().value(), "GENERALIZATION",
                        candidate.id().value(), candidate.name(), "umlModel.classes.superClassIds", "REFERENCES_PACKAGE", false)));
        Set<String> associationIds = project.umlModel().associations().stream()
                .filter(candidate -> candidate.associationClassId() != null
                        && classIds.contains(candidate.associationClassId().value())
                        || candidate.ends().stream().anyMatch(end -> classIds.contains(end.classId().value())))
                .map(candidate -> candidate.id().value()).collect(java.util.stream.Collectors.toSet());
        project.umlModel().associations().stream().filter(candidate -> associationIds.contains(candidate.id().value()))
                .forEach(candidate -> refs.add(ref("association:" + candidate.id().value(), "ASSOCIATION",
                        candidate.id().value(), candidate.name(), "umlModel.associations", "REFERENCES_PACKAGE", true)));
        project.umlModel().invariants().stream().filter(candidate -> classIds.contains(candidate.contextClassId().value()))
                .forEach(candidate -> refs.add(ref("invariant:" + candidate.id().value(), "INVARIANT",
                        candidate.id().value(), candidate.name(), "umlModel.invariants", "OWNED_CONSTRAINT", true)));
        Set<String> objectIds = project.objectModel().objects().stream()
                .filter(candidate -> classIds.contains(candidate.classId().value())).map(candidate -> candidate.id().value())
                .collect(java.util.stream.Collectors.toSet());
        project.objectModel().objects().stream().filter(candidate -> objectIds.contains(candidate.id().value()))
                .forEach(candidate -> refs.add(ref("object:" + candidate.id().value(), "OBJECT", candidate.id().value(),
                        candidate.name(), "objectModel.objects", "INSTANCE_OF_OWNED_CLASSIFIER", true)));
        project.objectModel().links().stream().filter(candidate -> associationIds.contains(candidate.associationId().value())
                || candidate.ends().stream().anyMatch(end -> objectIds.contains(end.objectId().value()))
                || candidate.associationClassObjectId() != null && objectIds.contains(candidate.associationClassObjectId().value()))
                .forEach(candidate -> refs.add(ref("link:" + candidate.id().value(), "OBJECT_LINK", candidate.id().value(),
                        candidate.id().value(), "objectModel.links", "REFERENCES_PACKAGE", true)));
        project.definitions().stream().filter(candidate -> candidate.ownerKind()
                == de.useweb.backend.domain.ocl.OclDefinitionElement.OwnerKind.PACKAGE
                        && packageIds.contains(candidate.ownerId())
                || candidate.ownerKind() == de.useweb.backend.domain.ocl.OclDefinitionElement.OwnerKind.CLASS
                        && classIds.contains(candidate.ownerId()))
                .forEach(candidate -> refs.add(ref("definition:" + candidate.id().value(), "DEFINITION",
                        candidate.id().value(), candidate.name(), "definitions", "OWNED_DEFINITION", true)));
        List<UmlModelImport> affectedImports = project.umlModel().imports().stream()
                .filter(candidate -> packageIds.contains(candidate.importingPackageId().value())
                        || packageIds.contains(candidate.importedPackageId().value()))
                .toList();
        affectedImports.forEach(candidate -> refs.add(ref(
                "import:" + candidate.id().value(), "IMPORT", candidate.id().value(), importName(candidate),
                "umlModel.imports", "REFERENCES_PACKAGE", true)));
        affectedImports.stream()
                .filter(candidate -> packageIds.contains(candidate.importedPackageId().value())
                        && !packageIds.contains(candidate.importingPackageId().value()))
                .forEach(candidate -> importReferences(project, candidate.id().value(), refs));
        addExternalPackageTypeReferences(project, packageIds, classIds, refs);
        addExternalPackageExpressionReferences(project, target, packageIds, classIds, refs);
    }

    private void addExternalPackageTypeReferences(Project project, Set<String> packageIds, Set<String> classIds,
            List<CommandElementReferenceDto> refs) {
        Set<String> removedTypeNames = new java.util.LinkedHashSet<>();
        project.umlModel().classes().stream().filter(candidate -> classIds.contains(candidate.id().value()))
                .forEach(candidate -> {
                    removedTypeNames.add(candidate.name());
                    removedTypeNames.add(candidate.qualifiedName(project.umlModel()));
                });
        project.umlModel().enumerations().stream().filter(candidate -> candidate.packageId() != null
                && packageIds.contains(candidate.packageId().value())).forEach(candidate -> {
                    removedTypeNames.add(candidate.name());
                    removedTypeNames.add(candidate.qualifiedName(project.umlModel()));
                });
        project.umlModel().dataTypes().stream().filter(candidate -> candidate.packageId() != null
                && packageIds.contains(candidate.packageId().value())).forEach(candidate -> {
                    removedTypeNames.add(candidate.name());
                    removedTypeNames.add(candidate.qualifiedName(project.umlModel()));
                });

        project.umlModel().classes().stream().filter(candidate -> !classIds.contains(candidate.id().value()))
                .forEach(owner -> {
                    owner.attributes().stream().filter(feature -> removedTypeNames.contains(feature.type().name()))
                            .forEach(feature -> refs.add(ref("external-attribute-type:" + feature.id().value(),
                                    "ATTRIBUTE", feature.id().value(), feature.name(), "umlModel.classes.attributes.type",
                                    "TYPED_BY_PACKAGE", false)));
                    owner.operations().stream().filter(feature -> removedTypeNames.contains(feature.returnType().name())
                            || feature.parameters().stream()
                                    .anyMatch(parameter -> removedTypeNames.contains(parameter.type().name())))
                            .forEach(feature -> refs.add(ref("external-operation-type:" + feature.id().value(),
                                    "OPERATION", feature.id().value(), feature.name(), "umlModel.classes.operations",
                                    "TYPED_BY_PACKAGE", false)));
                });
        project.umlModel().dataTypes().stream().filter(candidate -> candidate.packageId() == null
                || !packageIds.contains(candidate.packageId().value()))
                .filter(candidate -> candidate.properties().stream()
                        .anyMatch(property -> removedTypeNames.contains(property.type().name())))
                .forEach(candidate -> refs.add(ref("external-datatype-type:" + candidate.id().value(), "DATATYPE",
                        candidate.id().value(), candidate.name(), "umlModel.dataTypes.properties.type",
                        "TYPED_BY_PACKAGE", false)));
        project.umlModel().associations().stream()
                .filter(association -> association.ends().stream().flatMap(end -> end.qualifiers().stream())
                        .anyMatch(qualifier -> removedTypeNames.contains(qualifier.type().name())))
                .forEach(association -> refs.add(ref("external-association-qualifier:" + association.id().value(),
                        "ASSOCIATION", association.id().value(), association.name(),
                        "umlModel.associations.ends.qualifiers.type", "TYPED_BY_PACKAGE", false)));
        project.definitions().stream().filter(definition -> removedTypeNames.contains(definition.resultType().name())
                || definition.parameters().stream()
                        .anyMatch(parameter -> removedTypeNames.contains(parameter.type().name())))
                .forEach(definition -> refs.add(ref("external-definition-type:" + definition.id().value(),
                        "DEFINITION", definition.id().value(), definition.name(), "definitions.resultType",
                        "TYPED_BY_PACKAGE", false)));
    }

    private void addExternalPackageExpressionReferences(Project project, UmlPackage target, Set<String> packageIds,
            Set<String> classIds, List<CommandElementReferenceDto> refs) {
        Set<String> names = new java.util.LinkedHashSet<>();
        names.add(target.qualifiedName());
        project.umlModel().classes().stream().filter(candidate -> classIds.contains(candidate.id().value()))
                .forEach(candidate -> names.add(candidate.qualifiedName(project.umlModel())));
        project.umlModel().invariants().stream().filter(candidate -> !classIds.contains(candidate.contextClassId().value()))
                .filter(candidate -> containsAnyName(candidate.expression().text(), names)).forEach(candidate -> refs.add(ref(
                        "external-invariant:" + candidate.id().value(), "INVARIANT", candidate.id().value(), candidate.name(),
                        "umlModel.invariants.expression", "REFERENCES_PACKAGE", false)));
        project.umlModel().classes().stream().filter(candidate -> !classIds.contains(candidate.id().value()))
                .forEach(owner -> {
                    owner.attributes().stream().filter(feature -> containsAnyName(feature.initExpression(), names)
                            || containsAnyName(feature.deriveExpression(), names)).forEach(feature -> refs.add(ref(
                                    "external-attribute:" + feature.id().value(), "ATTRIBUTE", feature.id().value(),
                                    feature.name(), "umlModel.classes.attributes", "REFERENCES_PACKAGE", false)));
                    owner.operations().stream().filter(feature -> containsAnyName(feature.bodyExpression(), names)
                            || feature.contracts().stream().anyMatch(contract -> containsAnyName(contract.expression(), names)))
                            .forEach(feature -> refs.add(ref("external-operation:" + feature.id().value(), "OPERATION",
                                    feature.id().value(), feature.name(), "umlModel.classes.operations", "REFERENCES_PACKAGE", false)));
                });
        project.definitions().stream().filter(candidate -> !packageIds.contains(candidate.ownerId())
                && !classIds.contains(candidate.ownerId())).filter(candidate -> containsAnyName(candidate.expression(), names))
                .forEach(candidate -> refs.add(ref("external-definition:" + candidate.id().value(), "DEFINITION",
                        candidate.id().value(), candidate.name(), "definitions.expression", "REFERENCES_PACKAGE", false)));
    }

    private void importReferences(Project project, String id, List<CommandElementReferenceDto> refs) {
        UmlModelImport modelImport = project.umlModel().imports().stream()
                .filter(candidate -> candidate.id().value().equals(id)).findFirst()
                .orElseThrow(() -> notFound("IMPORT", id, null));
        Set<String> names = new java.util.LinkedHashSet<>();
        if (modelImport.alias() != null) names.add(modelImport.alias());
        project.umlModel().findPackage(modelImport.importedPackageId()).ifPresent(pkg -> names.add(pkg.qualifiedName()));
        project.umlModel().classes().stream().filter(candidate -> modelImport.importedPackageId().equals(candidate.packageId()))
                .forEach(candidate -> { names.add(candidate.name()); names.add(candidate.qualifiedName(project.umlModel())); });
        project.umlModel().enumerations().stream().filter(candidate -> modelImport.importedPackageId().equals(candidate.packageId()))
                .forEach(candidate -> { names.add(candidate.name()); names.add(candidate.qualifiedName(project.umlModel())); });
        project.umlModel().dataTypes().stream().filter(candidate -> modelImport.importedPackageId().equals(candidate.packageId()))
                .forEach(candidate -> { names.add(candidate.name()); names.add(candidate.qualifiedName(project.umlModel())); });
        Set<String> importingClassIds = project.umlModel().classes().stream()
                .filter(candidate -> modelImport.importingPackageId().equals(candidate.packageId()))
                .map(candidate -> candidate.id().value()).collect(java.util.stream.Collectors.toSet());
        project.umlModel().invariants().stream().filter(candidate -> importingClassIds.contains(candidate.contextClassId().value()))
                .filter(candidate -> containsAnyName(candidate.expression().text(), names)).forEach(candidate -> refs.add(ref(
                        "import-invariant:" + candidate.id().value(), "INVARIANT", candidate.id().value(), candidate.name(),
                        "umlModel.invariants.expression", "REFERENCES_IMPORT", false)));
        project.umlModel().classes().stream().filter(candidate -> importingClassIds.contains(candidate.id().value()))
                .forEach(owner -> {
                    owner.attributes().stream().filter(feature -> containsAnyName(feature.initExpression(), names)
                            || containsAnyName(feature.deriveExpression(), names)).forEach(feature -> refs.add(ref(
                                    "import-attribute:" + feature.id().value(), "ATTRIBUTE", feature.id().value(),
                                    feature.name(), "umlModel.classes.attributes", "REFERENCES_IMPORT", false)));
                    owner.operations().stream().filter(feature -> containsAnyName(feature.bodyExpression(), names)
                            || feature.contracts().stream().anyMatch(contract -> containsAnyName(contract.expression(), names)))
                            .forEach(feature -> refs.add(ref("import-operation:" + feature.id().value(), "OPERATION",
                                    feature.id().value(), feature.name(), "umlModel.classes.operations",
                                    "REFERENCES_IMPORT", false)));
                });
        project.definitions().stream().filter(candidate -> modelImport.importingPackageId().value().equals(candidate.ownerId())
                || importingClassIds.contains(candidate.ownerId())).filter(candidate -> containsAnyName(candidate.expression(), names))
                .forEach(candidate -> refs.add(ref("import-definition:" + candidate.id().value(), "DEFINITION",
                        candidate.id().value(), candidate.name(), "definitions.expression", "REFERENCES_IMPORT", false)));
    }

    private void definitionReferences(Project project, String id, List<CommandElementReferenceDto> refs) {
        var target = project.definitions().stream().filter(value -> value.id().value().equals(id)).findFirst()
                .orElseThrow(() -> notFound("DEFINITION", id, null));
        project.definitions().stream().filter(value -> !value.id().value().equals(id))
                .filter(value -> containsName(value.expression(), target.name()))
                .forEach(value -> refs.add(ref("definition:" + value.id().value(), "DEFINITION",
                        value.id().value(), value.name(), "definitions.expression", "REFERENCES", false)));
        project.umlModel().invariants().stream().filter(value -> containsName(value.expression().text(), target.name()))
                .forEach(value -> refs.add(ref("invariant:" + value.id().value(), "INVARIANT",
                        value.id().value(), value.name(), "expression", "REFERENCES", false)));
        project.umlModel().classes().stream().flatMap(owner -> owner.operations().stream())
                .filter(value -> containsName(value.bodyExpression(), target.name()))
                .forEach(value -> refs.add(ref("operation:" + value.id().value(), "OPERATION",
                        value.id().value(), value.name(), "bodyExpression", "REFERENCES", false)));
    }

    private void classReferences(Project project, String id, List<CommandElementReferenceDto> refs) {
        project.umlModel().classes().stream().filter(type -> type.superClassIds().stream().anyMatch(s -> s.value().equals(id)))
                .forEach(type -> refs.add(ref("generalization:" + type.id().value() + ":" + id, "GENERALIZATION",
                        id, type.name() + " -> " + nameOfClass(project, id), "umlModel.classes", "SUPERTYPE", false)));
        project.umlModel().associations().stream().filter(association -> association.ends().stream()
                .anyMatch(end -> end.classId().value().equals(id))).forEach(association -> refs.add(ref(
                        "association:" + association.id().value(), "ASSOCIATION", association.id().value(), association.name(),
                        "umlModel.associations", "TYPED_BY", true)));
        project.umlModel().invariants().stream().filter(invariant -> invariant.contextClassId().value().equals(id))
                .forEach(invariant -> refs.add(ref("invariant:" + invariant.id().value(), "INVARIANT",
                        invariant.id().value(), invariant.name(), "umlModel.invariants", "CONTEXT", true)));
        project.objectModel().objects().stream().filter(object -> object.classId().value().equals(id))
                .forEach(object -> refs.add(ref("object:" + object.id().value(), "OBJECT", object.id().value(), object.name(),
                        "objectModel.objects", "INSTANCE_OF", true)));
    }

    private void featureReferences(Project project, String id, boolean attribute, List<CommandElementReferenceDto> refs) {
        String name = attribute ? project.umlModel().findAttribute(new UmlAttributeId(id)).map(UmlAttribute::name).orElse(id)
                : project.umlModel().classes().stream().flatMap(type -> type.operations().stream())
                        .filter(operation -> operation.id().value().equals(id)).map(UmlOperation::name).findFirst().orElse(id);
        project.umlModel().invariants().stream().filter(invariant -> containsName(invariant.expression().text(), name))
                .forEach(invariant -> refs.add(ref("invariant:" + invariant.id().value(), "INVARIANT",
                        invariant.id().value(), invariant.name(), "expression", "REFERENCES", false)));
        project.umlModel().classes().stream().flatMap(type -> type.operations().stream())
                .filter(operation -> operation.bodyExpression() != null && containsName(operation.bodyExpression(), name))
                .forEach(operation -> refs.add(ref("operation:" + operation.id().value(), "OPERATION",
                        operation.id().value(), operation.name(), "bodyExpression", "REFERENCES", false)));
        if (attribute) project.objectModel().objects().stream().flatMap(object -> object.slots().stream())
                .filter(slot -> slot.attributeId().value().equals(id)).forEach(slot -> refs.add(ref(
                        "slot:" + slot.id().value(), "SLOT", slot.id().value(), name, "objectModel.objects.slots",
                        "VALUE_OF", true)));
        if (attribute) {
            project.umlModel().classes().forEach(owner -> owner.attributes().stream()
                    .filter(feature -> feature.redefinedAttributeIds().stream().anyMatch(target -> target.value().equals(id)))
                    .forEach(feature -> refs.add(ref("redefinition:" + feature.id().value() + ":" + id,
                            "ATTRIBUTE", feature.id().value(), feature.name(), "redefinedAttributeIds",
                            "REDEFINES", false))));
        } else {
            project.umlModel().classes().forEach(owner -> owner.operations().stream()
                    .filter(feature -> feature.redefinedOperationIds().stream().anyMatch(target -> target.value().equals(id)))
                    .forEach(feature -> refs.add(ref("redefinition:" + feature.id().value() + ":" + id,
                            "OPERATION", feature.id().value(), feature.name(), "redefinedOperationIds",
                            "REDEFINES", false))));
        }
    }

    private void operationReferences(Project project, String id, List<CommandElementReferenceDto> refs) {
        UmlClass owner = operationOwner(project, id, null);
        UmlOperation target = owner.findOperation(new UmlOperationId(id)).orElseThrow();
        String name = target.name();

        project.umlModel().invariants().stream().filter(invariant -> containsName(invariant.expression().text(), name))
                .forEach(invariant -> refs.add(ref("invariant:" + invariant.id().value() + ":OPERATION", "INVARIANT",
                        invariant.id().value(), invariant.name(), "expression", "REFERENCES_OPERATION", false)));
        project.umlModel().classes().forEach(classifier -> {
            classifier.attributes().stream().filter(attribute -> containsName(attribute.initExpression(), name)
                    || containsName(attribute.deriveExpression(), name)).forEach(attribute -> refs.add(ref(
                            "attribute-expression:" + attribute.id().value() + ":OPERATION", "ATTRIBUTE",
                            attribute.id().value(), attribute.name(), "initExpression|deriveExpression",
                            "REFERENCES_OPERATION", false)));
            classifier.operations().stream().filter(operation -> !operation.id().value().equals(id))
                    .filter(operation -> containsName(operation.bodyExpression(), name)
                            || operation.contracts().stream()
                                    .anyMatch(contract -> containsName(contract.expression(), name)))
                    .forEach(operation -> refs.add(ref("operation-expression:" + operation.id().value()
                            + ":OPERATION", "OPERATION", operation.id().value(), operation.name(),
                            "bodyExpression|contracts", "REFERENCES_OPERATION", false)));
            classifier.operations().stream()
                    .filter(operation -> operation.redefinedOperationIds().stream()
                            .anyMatch(redefined -> redefined.value().equals(id)))
                    .forEach(operation -> refs.add(ref("redefinition:" + operation.id().value() + ":" + id,
                            "OPERATION", operation.id().value(), operation.name(), "redefinedOperationIds",
                            "REDEFINES", false)));
        });
        project.definitions().stream().filter(definition -> containsName(definition.expression(), name))
                .forEach(definition -> refs.add(ref("definition:" + definition.id().value() + ":OPERATION",
                        "DEFINITION", definition.id().value(), definition.name(), "definitions.expression",
                        "REFERENCES_OPERATION", false)));
    }

    private CommandElementReferenceDto target(Project project, String type, String id) {
        String name = switch (type) {
            case "CLASS" -> project.umlModel().findClass(new UmlClassId(id)).map(UmlClass::name)
                    .orElseThrow(() -> notFound(type, id, null));
            case "ATTRIBUTE" -> project.umlModel().findAttribute(new UmlAttributeId(id)).map(UmlAttribute::name)
                    .orElseThrow(() -> notFound(type, id, null));
            case "OPERATION" -> {
                UmlClass owner = operationOwner(project, id, null);
                yield owner.findOperation(new UmlOperationId(id)).orElseThrow().name();
            }
            case "ASSOCIATION" -> project.umlModel().findAssociation(new UmlAssociationId(id)).map(UmlAssociation::name)
                    .orElseThrow(() -> notFound(type, id, null));
            case "INVARIANT" -> project.umlModel().findInvariant(new UmlInvariantId(id)).map(invariant -> invariant.name())
                    .orElseThrow(() -> notFound(type, id, null));
            case "OBJECT" -> project.objectModel().objects().stream().filter(o -> o.id().value().equals(id))
                    .map(ObjectInstance::name).findFirst().orElseThrow(() -> notFound(type, id, null));
            case "DEFINITION" -> project.definitions().stream().filter(value -> value.id().value().equals(id))
                    .map(value -> value.name()).findFirst().orElseThrow(() -> notFound(type, id, null));
            case "ENUMERATION" -> project.umlModel().enumerations().stream().filter(value -> value.id().value().equals(id))
                    .map(UmlEnumeration::name).findFirst().orElseThrow(() -> notFound(type, id, null));
            case "DATATYPE" -> project.umlModel().dataTypes().stream().filter(value -> value.id().value().equals(id))
                    .map(value -> value.name()).findFirst().orElseThrow(() -> notFound(type, id, null));
            case "ENUMERATION_LITERAL" -> project.umlModel().enumerations().stream()
                    .flatMap(value -> value.literalDefinitions().stream()).filter(value -> value.id().value().equals(id))
                    .map(UmlEnumerationLiteral::name).findFirst().orElseThrow(() -> notFound(type, id, null));
            case "PACKAGE" -> project.umlModel().findPackage(new UmlPackageId(id)).map(UmlPackage::qualifiedName)
                    .orElseThrow(() -> notFound(type, id, null));
            case "IMPORT" -> project.umlModel().imports().stream().filter(value -> value.id().value().equals(id))
                    .map(this::importName).findFirst().orElseThrow(() -> notFound(type, id, null));
            default -> id;
        };
        return ref("target:" + type.toLowerCase() + ":" + id, type, id, name, null, "TARGET", false);
    }

    private CommandElementReferenceDto ref(String referenceId, String type, String id, String name, String path,
            String relation, boolean cascadeAllowed) {
        return new CommandElementReferenceDto(referenceId, type, id, name, path, relation, cascadeAllowed);
    }

    private String nameOfClass(Project project, String id) {
        return project.umlModel().findClass(new UmlClassId(id)).map(UmlClass::name).orElse(id);
    }

    private boolean containsName(String text, String name) {
        return text != null && text.matches("(?s).*\\b" + java.util.regex.Pattern.quote(name) + "\\b.*");
    }

    private boolean containsAnyName(String text, Set<String> names) {
        return names.stream().anyMatch(name -> containsName(text, name));
    }

    private String importName(UmlModelImport modelImport) {
        if (modelImport.alias() != null) return modelImport.alias();
        if (modelImport.source() != null) return modelImport.source();
        return modelImport.id().value();
    }

    private void dataTypeReferences(Project project, String dataTypeId,
            List<CommandElementReferenceDto> refs) {
        var dataType = project.umlModel().dataTypes().stream()
                .filter(value -> value.id().value().equals(dataTypeId)).findFirst()
                .orElseThrow(() -> notFound("DATATYPE", dataTypeId, null));
        project.umlModel().classes().forEach(owner -> {
            owner.attributes().stream().filter(value -> typeReferences(project, owner, value.type().name(), dataTypeId))
                    .forEach(value -> refs.add(ref("attribute:" + value.id().value(), "ATTRIBUTE", value.id().value(),
                            value.name(), "umlModel.classes.attributes.type", "TYPED_BY", false)));
            owner.operations().forEach(operation -> {
                if (typeReferences(project, owner, operation.returnType().name(), dataTypeId)) refs.add(ref(
                        "operation-return:" + operation.id().value(), "OPERATION", operation.id().value(), operation.name(),
                        "umlModel.classes.operations.returnType", "TYPED_BY", false));
                operation.parameters().stream()
                        .filter(value -> typeReferences(project, owner, value.type().name(), dataTypeId))
                        .forEach(value -> refs.add(ref("parameter:" + value.id().value(), "PARAMETER", value.id().value(),
                                value.name(), "umlModel.classes.operations.parameters.type", "TYPED_BY", false)));
            });
        });
        project.umlModel().dataTypes().stream().filter(value -> !value.id().value().equals(dataTypeId))
                .forEach(owner -> owner.properties().stream()
                        .filter(value -> typeReferences(project, null, value.type().name(), dataTypeId))
                        .forEach(value -> refs.add(ref("datatype-property:" + value.id(), "DATATYPE_PROPERTY", value.id(),
                                value.name(), "umlModel.dataTypes.properties.type", "TYPED_BY", false))));
        project.objectModel().objects().stream().flatMap(value -> value.slots().stream())
                .filter(value -> typeReferences(project, null, value.value().valueType().name(), dataTypeId))
                .forEach(value -> refs.add(ref("slot:" + value.id().value(), "SLOT", value.id().value(),
                        value.id().value(), "objectModel.objects.slots.value", "TYPED_BY", false)));
        expressionReferences(project, dataType.name(), "DATATYPE", refs);
        String qualifiedName = dataType.qualifiedName(project.umlModel());
        if (!qualifiedName.equals(dataType.name())) expressionReferences(project, qualifiedName, "DATATYPE", refs);
    }

    private DeleteImpactDto dataTypePropertyDeleteImpact(Project project, UmlDataTypeId dataTypeId,
            String propertyId, JsonNode draft) {
        UmlDataType dataType = requireDataType(project, dataTypeId, draft);
        UmlDataTypeProperty property = dataType.properties().stream()
                .filter(candidate -> candidate.id().equals(propertyId)).findFirst()
                .orElseThrow(() -> notFound("DATATYPE_PROPERTY", propertyId, draft));
        CommandElementReferenceDto target = ref("datatype-property:" + dataTypeId.value() + ":" + propertyId,
                "DATATYPE_PROPERTY", propertyId, property.name(),
                "umlModel.dataTypes[" + dataTypeId.value() + "].properties", "TARGET", false);
        List<CommandElementReferenceDto> references = dataTypePropertyReferences(project, dataType, property);
        return new DeleteImpactDto(MODEL, revision(project), target, references, !references.isEmpty());
    }

    private UmlDataType requireDataType(Project project, UmlDataTypeId dataTypeId, JsonNode draft) {
        return project.umlModel().dataTypes().stream().filter(value -> value.id().equals(dataTypeId)).findFirst()
                .orElseThrow(() -> notFound("DATATYPE", dataTypeId.value(), draft));
    }

    private void rejectBlockedDataTypePropertyRemoval(Project project, UmlDataType dataType,
            UmlDataTypeProperty property, JsonNode draft) {
        DeleteImpactDto impact = dataTypePropertyDeleteImpact(project, dataType.id(), property.id(), draft);
        if (!impact.references().isEmpty()) throw deleteBlocked(draft, impact);
    }

    private CommandException deleteBlocked(JsonNode draft, DeleteImpactDto impact) {
        return new CommandException(409, "DELETE_BLOCKED", "References block deletion",
                "Verbleibende Referenzen blockieren das Loeschen.",
                Map.of("draft", nullSafe(draft), "target", impact.target(), "blockers", impact.references(),
                        "revision", impact.revision(), "currentImpact", impact));
    }

    private List<CommandElementReferenceDto> dataTypePropertyReferences(Project project, UmlDataType dataType,
            UmlDataTypeProperty property) {
        List<CommandElementReferenceDto> refs = new ArrayList<>();
        project.umlModel().classes().forEach(owner -> owner.attributes().forEach(attribute -> {
            if (attribute.classifierValue() == null) return;
            StructuredUmlTypeService.ResolvedType resolved = resolvedType(project, owner,
                    attribute.classifierValue().valueType().name());
            if (resolved == null) return;
            String base = "umlModel.classes[" + owner.id().value() + "].attributes[" + attribute.id().value()
                    + "].classifierValue.value";
            List<String> paths = structuredTypes.dataTypePropertyValuePaths(attribute.classifierValue().value(), resolved,
                    dataType.id().value(), property.id(), base);
            for (int index = 0; index < paths.size(); index++) {
                refs.add(ref("datatype-property-value:attribute:" + attribute.id().value() + ":" + index,
                        "ATTRIBUTE", attribute.id().value(), attribute.name(), paths.get(index),
                        "STORES_DATATYPE_PROPERTY", false));
            }
        }));
        project.objectModel().objects().forEach(object -> {
            UmlClass context = project.umlModel().findClass(object.classId()).orElse(null);
            object.slots().forEach(slot -> {
                StructuredUmlTypeService.ResolvedType resolved = resolvedType(project, context,
                        slot.value().valueType().name());
                if (resolved == null) return;
                String base = "objectModel.objects[" + object.id().value() + "].slots[" + slot.id().value() + "].value";
                List<String> paths = structuredTypes.dataTypePropertyValuePaths(slot.value().value(), resolved,
                        dataType.id().value(), property.id(), base);
                for (int index = 0; index < paths.size(); index++) {
                    refs.add(ref("datatype-property-value:slot:" + slot.id().value() + ":" + index,
                            "SLOT", slot.id().value(), object.name(), paths.get(index),
                            "STORES_DATATYPE_PROPERTY", false));
                }
            });
        });
        dataTypePropertyExpressionReferences(project, property.name(), refs);
        return refs.stream().collect(java.util.stream.Collectors.toMap(CommandElementReferenceDto::referenceId,
                value -> value, (left, right) -> left, LinkedHashMap::new)).values().stream().toList();
    }

    private StructuredUmlTypeService.ResolvedType resolvedType(Project project, UmlClass context, String typeName) {
        try {
            return structuredTypes.resolve(project.umlModel(), typeName, context, false);
        } catch (StructuredUmlTypeService.TypeException ignored) {
            return null;
        }
    }

    private void dataTypePropertyExpressionReferences(Project project, String propertyName,
            List<CommandElementReferenceDto> refs) {
        project.umlModel().invariants().forEach(invariant -> addPropertyExpressionReferences(invariant.expression().text(),
                propertyName, "INVARIANT", invariant.id().value(), invariant.name(), "expression", refs));
        project.umlModel().classes().forEach(owner -> {
            owner.attributes().forEach(attribute -> {
                addPropertyExpressionReferences(attribute.initExpression(), propertyName, "ATTRIBUTE",
                        attribute.id().value(), attribute.name(), "initExpression", refs);
                addPropertyExpressionReferences(attribute.deriveExpression(), propertyName, "ATTRIBUTE",
                        attribute.id().value(), attribute.name(), "deriveExpression", refs);
            });
            owner.operations().forEach(operation -> {
                addPropertyExpressionReferences(operation.bodyExpression(), propertyName, "OPERATION",
                        operation.id().value(), operation.name(), "bodyExpression", refs);
                operation.contracts().forEach(contract -> addPropertyExpressionReferences(contract.expression(),
                        propertyName, "OPERATION", operation.id().value(), operation.name(),
                        "contracts[" + contract.id() + "].expression", refs));
            });
        });
        project.definitions().forEach(definition -> addPropertyExpressionReferences(definition.expression(), propertyName,
                "DEFINITION", definition.id().value(), definition.name(), "definitions.expression", refs));
    }

    private void addPropertyExpressionReferences(String expression, String propertyName, String elementType,
            String elementId, String elementName, String path, List<CommandElementReferenceDto> refs) {
        if (expression == null || expression.isBlank()) return;
        OclParseResult parsed = parser.parse(expression);
        if (!parsed.success() || parsed.ast() == null) return;
        for (SourceRange range : OclPropertyAccessFinder.find(parsed.ast(), propertyName)) {
            refs.add(new CommandElementReferenceDto(
                    "datatype-property-expression:" + elementType.toLowerCase() + ":" + elementId + ":"
                            + range.start().offset(),
                    elementType, elementId, elementName, path, "REFERENCES_DATATYPE_PROPERTY", false,
                    diagnosticMapper.toDto(range)));
        }
    }

    private boolean typeReferences(Project project, UmlClass contextClass, String typeName, String elementId) {
        try {
            return structuredTypes.references(structuredTypes.resolve(project.umlModel(), typeName, contextClass, true),
                    elementId);
        } catch (StructuredUmlTypeService.TypeException ignored) {
            return false;
        }
    }

    private void enumerationReferences(Project project, String enumerationId,
            List<CommandElementReferenceDto> refs) {
        UmlEnumeration enumeration = project.umlModel().enumerations().stream()
                .filter(value -> value.id().value().equals(enumerationId)).findFirst()
                .orElseThrow(() -> notFound("ENUMERATION", enumerationId, null));
        String qualifiedName = enumeration.qualifiedName(project.umlModel());
        project.umlModel().classes().forEach(owner -> {
            owner.attributes().stream().filter(value -> typeReferences(project, owner, value.type().name(), enumerationId))
                    .forEach(value -> refs.add(ref("attribute:" + value.id().value(), "ATTRIBUTE", value.id().value(),
                            value.name(), "umlModel.classes.attributes.type", "TYPED_BY", false)));
            owner.operations().forEach(operation -> {
                if (typeReferences(project, owner, operation.returnType().name(), enumerationId)) refs.add(ref(
                        "operation-return:" + operation.id().value(), "OPERATION", operation.id().value(), operation.name(),
                        "umlModel.classes.operations.returnType", "TYPED_BY", false));
                operation.parameters().stream().filter(value -> typeReferences(project, owner, value.type().name(), enumerationId))
                        .forEach(value -> refs.add(ref("parameter:" + value.id().value(), "PARAMETER", value.id().value(),
                                value.name(), "umlModel.classes.operations.parameters.type", "TYPED_BY", false)));
            });
        });
        project.umlModel().dataTypes().forEach(owner -> owner.properties().stream()
                .filter(value -> typeReferences(project, null, value.type().name(), enumerationId))
                .forEach(value -> refs.add(ref("datatype-property:" + value.id(), "DATATYPE_PROPERTY", value.id(),
                        value.name(), "umlModel.dataTypes.properties.type", "TYPED_BY", false))));
        project.umlModel().associations().forEach(association -> association.ends().forEach(end -> end.qualifiers().stream()
                .filter(value -> typeReferences(project, null, value.type().name(), enumerationId))
                .forEach(value -> refs.add(ref("qualifier:" + value.id().value(), "QUALIFIER", value.id().value(),
                        value.name(), "umlModel.associations.ends.qualifiers.type", "TYPED_BY", false)))));
        project.objectModel().objects().stream().flatMap(value -> value.slots().stream())
                .filter(value -> typeReferences(project, null, value.value().valueType().name(), enumerationId))
                .forEach(value -> refs.add(ref("slot:" + value.id().value(), "SLOT", value.id().value(),
                        value.id().value(), "objectModel.objects.slots.value", "TYPED_BY", false)));
        project.objectModel().links().forEach(link -> link.ends().forEach(end -> end.qualifierValues().stream()
                .filter(value -> typeReferences(project, null, value.value().valueType().name(), enumerationId))
                .forEach(value -> refs.add(ref("qualifier-value:" + link.id().value() + ":" + value.qualifierId().value(),
                        "QUALIFIER_VALUE", value.qualifierId().value(), value.qualifierId().value(),
                        "objectModel.links.ends.qualifierValues", "TYPED_BY", false)))));
        expressionReferences(project, enumeration.name(), "ENUMERATION", refs);
        if (!qualifiedName.equals(enumeration.name())) expressionReferences(project, qualifiedName, "ENUMERATION", refs);
    }

    private List<CommandElementReferenceDto> enumerationLiteralReferences(Project project, String literalId) {
        List<CommandElementReferenceDto> refs = new ArrayList<>();
        UmlEnumerationLiteral literal = project.umlModel().enumerations().stream()
                .flatMap(value -> value.literalDefinitions().stream()).filter(value -> value.id().value().equals(literalId))
                .findFirst().orElseThrow(() -> notFound("ENUMERATION_LITERAL", literalId, null));
        project.objectModel().objects().forEach(object -> object.slots().stream()
                .filter(slot -> java.util.Objects.equals(slot.value().value(), literal.name()))
                .forEach(slot -> refs.add(ref("slot:" + slot.id().value(), "SLOT", slot.id().value(), object.name(),
                        "objectModel.objects.slots.value", "USES_LITERAL", false))));
        project.objectModel().links().forEach(link -> link.ends().forEach(end -> end.qualifierValues().stream()
                .filter(value -> java.util.Objects.equals(value.value().value(), literal.name()))
                .forEach(value -> refs.add(ref("qualifier-value:" + link.id().value() + ":" + value.qualifierId().value(),
                        "QUALIFIER_VALUE", value.qualifierId().value(), value.qualifierId().value(),
                        "objectModel.links.ends.qualifierValues", "USES_LITERAL", false)))));
        project.umlModel().classes().forEach(owner -> owner.attributes().stream()
                .filter(value -> value.classifierValue() != null
                        && java.util.Objects.equals(value.classifierValue().value(), literal.name()))
                .forEach(value -> refs.add(ref("attribute-value:" + value.id().value(), "ATTRIBUTE", value.id().value(),
                        value.name(), "umlModel.classes.attributes.classifierValue", "USES_LITERAL", false))));
        expressionReferences(project, literal.name(), "ENUMERATION_LITERAL", refs);
        return List.copyOf(refs);
    }

    private void expressionReferences(Project project, String name, String relation,
            List<CommandElementReferenceDto> refs) {
        project.umlModel().invariants().stream().filter(value -> containsName(value.expression().text(), name))
                .forEach(value -> refs.add(ref("invariant:" + value.id().value() + ":" + relation, "INVARIANT",
                        value.id().value(), value.name(), "expression", "REFERENCES_" + relation, false)));
        project.umlModel().classes().forEach(owner -> {
            owner.attributes().stream().filter(value -> containsName(value.initExpression(), name)
                    || containsName(value.deriveExpression(), name)).forEach(value -> refs.add(ref(
                            "attribute-expression:" + value.id().value() + ":" + relation, "ATTRIBUTE", value.id().value(),
                            value.name(), "initExpression|deriveExpression", "REFERENCES_" + relation, false)));
            owner.operations().stream().filter(value -> containsName(value.bodyExpression(), name)
                    || value.contracts().stream().anyMatch(contract -> containsName(contract.expression(), name)))
                    .forEach(value -> refs.add(ref("operation-expression:" + value.id().value() + ":" + relation,
                            "OPERATION", value.id().value(), value.name(), "bodyExpression|contracts",
                            "REFERENCES_" + relation, false)));
        });
        project.definitions().stream().filter(value -> containsName(value.expression(), name))
                .forEach(value -> refs.add(ref("definition:" + value.id().value() + ":" + relation, "DEFINITION",
                        value.id().value(), value.name(), "definitions.expression", "REFERENCES_" + relation, false)));
    }

    private Map<String, String> safeLiteralNames(UmlEnumerationDto draft) {
        if (draft.literalDefinitions() == null || draft.literalDefinitions().isEmpty()) return Map.of();
        Map<String, String> values = new LinkedHashMap<>();
        draft.literalDefinitions().forEach(value -> {
            if (value.id() != null) values.put(value.id(), value.name());
        });
        return values;
    }

    private UmlEnumerationDto withStableLiteralIds(UmlEnumerationDto draft, UmlEnumeration current) {
        if (draft.literalDefinitions() != null && !draft.literalDefinitions().isEmpty()) return draft;
        List<UmlEnumerationLiteralDto> definitions = (draft.literals() == null ? List.<String>of() : draft.literals())
                .stream().map(name -> current.literalDefinitions().stream()
                        .filter(value -> value.name().equals(name)).findFirst()
                        .map(value -> new UmlEnumerationLiteralDto(value.id().value(), name))
                        .orElseGet(() -> new UmlEnumerationLiteralDto(null, name))).toList();
        return new UmlEnumerationDto(draft.id(), draft.name(), draft.literals(), draft.packageId(), draft.qualifiedName(),
                draft.visibility(), definitions);
    }

    private void requireRevision(Project project, String expectedRevision, JsonNode draft, String scope) {
        String actual = revision(project);
        if (expectedRevision == null || expectedRevision.isBlank()) {
            throw new CommandException(400, "EXPECTED_REVISION_REQUIRED", "expectedRevision is required",
                    "Die erwartete Revision fehlt.", Map.of("draft", nullSafe(draft), "actualRevision", actual));
        }
        if (!actual.equals(expectedRevision)) {
            throw new CommandException(409, SNAPSHOT.equals(scope) ? "STALE_SNAPSHOT_REVISION" : "STALE_MODEL_REVISION",
                    "Project revision is stale",
                    "Das Modell wurde zwischenzeitlich geaendert.",
                    Map.of("draft", nullSafe(draft), "expectedRevision", expectedRevision, "actualRevision", actual));
        }
    }

    private void requireDeleteRevision(Project project, String expectedRevision, JsonNode draft,
            DeleteImpactDto currentImpact) {
        String actual = revision(project);
        if (expectedRevision == null || expectedRevision.isBlank()) {
            throw new CommandException(400, "EXPECTED_REVISION_REQUIRED", "expectedRevision is required",
                    "Die erwartete Revision fehlt.", Map.of("draft", nullSafe(draft),
                            "actualRevision", actual, "currentImpact", currentImpact));
        }
        if (!actual.equals(expectedRevision)) {
            throw new CommandException(409, "STALE_MODEL_REVISION", "Project revision is stale",
                    "Das Modell wurde zwischenzeitlich geaendert.",
                    Map.of("draft", nullSafe(draft), "expectedRevision", expectedRevision,
                            "actualRevision", actual, "currentImpact", currentImpact));
        }
    }

    private CommandException commandFailure(RuntimeException exception, JsonNode draft, Project project,
            String command) {
        Map<String, Object> details = new LinkedHashMap<>();
        String code = "COMMAND_VALIDATION_FAILED";
        String userMessage = "Der Befehl konnte fachlich nicht ausgefuehrt werden.";
        if (exception instanceof UmlModelException uml) {
            code = uml.error().code(); userMessage = uml.error().userMessage(); details.putAll(uml.error().details());
        } else if (exception instanceof UmlNamespaceException namespace) {
            code = namespace.code(); userMessage = "Der Namespace oder Import ist ungueltig.";
            details.putAll(namespace.details());
        } else if (exception instanceof ObjectModelException object) {
            code = object.error().code(); userMessage = object.error().userMessage(); details.putAll(object.error().details());
        }
        details.put("draft", nullSafe(draft));
        if (!details.containsKey("targets")) {
            List<CommandElementReferenceDto> targets = targets(details, project);
            if (targets.isEmpty()) {
                CommandElementReferenceDto draftTarget = commandDraftTarget(command, draft);
                if (draftTarget != null) targets = List.of(draftTarget);
            }
            details.put("targets", targets);
        }
        int status = Set.of("AMBIGUOUS_INHERITED_FEATURE", "DUPLICATE_NAMESPACE", "DUPLICATE_IMPORT_ALIAS",
                "IMPORT_CYCLE", "PACKAGE_CYCLE", "QUALIFIED_NAME_CONFLICT",
                "ASSOCIATION_CLASS_ALREADY_BOUND").contains(code) ? 409 : 400;
        return new CommandException(status, code, exception.getMessage(), userMessage, details);
    }

    private CommandElementReferenceDto commandDraftTarget(String command, JsonNode draft) {
        if (draft == null || !draft.isObject()) return null;
        String type;
        String path;
        if (command.endsWith("ASSOCIATION_CLASS")) {
            type = "CLASS";
            path = "umlModel.classes";
        } else if (command.endsWith("ATTRIBUTE")) {
            type = "ATTRIBUTE";
            path = "umlModel.classes.attributes";
        } else if (command.endsWith("OPERATION")) {
            type = "OPERATION";
            path = "umlModel.classes.operations";
        } else if (command.endsWith("ASSOCIATION")) {
            type = "ASSOCIATION";
            path = "umlModel.associations";
        } else if (command.endsWith("PACKAGE")) {
            type = "PACKAGE";
            path = "umlModel.packages";
        } else if (command.endsWith("IMPORT")) {
            type = "IMPORT";
            path = "umlModel.imports";
        } else {
            return null;
        }
        String id = draft.path("id").asText("draft");
        String name = draft.path("name").asText(id);
        return ref("error:draft:" + type.toLowerCase() + ":" + id, type, id, name, path, "INVALID", false);
    }

    private List<CommandElementReferenceDto> targets(Map<String, Object> details, Project project) {
        List<CommandElementReferenceDto> targets = new ArrayList<>();
        details.forEach((key, value) -> {
            if (key.endsWith("Id") && value instanceof String id) {
                targets.add(ref("error:" + key + ":" + id, key.substring(0, key.length() - 2).toUpperCase(),
                        id, elementName(project, id), key, "INVALID", false));
            } else if (key.endsWith("Ids") && value instanceof Iterable<?> ids) {
                String type = key.substring(0, key.length() - 3).toUpperCase();
                for (Object valueId : ids) {
                    String id = String.valueOf(valueId);
                    targets.add(ref("error:" + key + ":" + id, type, id, elementName(project, id), key, "CONFLICT", false));
                }
            }
        });
        return List.copyOf(targets);
    }

    private String elementName(Project project, String id) {
        return project.umlModel().findClass(new UmlClassId(id)).map(UmlClass::name)
                .or(() -> project.umlModel().findAttribute(new UmlAttributeId(id)).map(UmlAttribute::name))
                .or(() -> project.umlModel().findPackage(new UmlPackageId(id)).map(UmlPackage::qualifiedName))
                .or(() -> project.umlModel().imports().stream().filter(value -> value.id().value().equals(id))
                        .map(this::importName).findFirst())
                .orElseGet(() -> project.umlModel().classes().stream().flatMap(type -> type.operations().stream())
                        .filter(operation -> operation.id().value().equals(id)).map(UmlOperation::name)
                        .findFirst().orElse(id));
    }

    private <T> T draft(MutationCommandRequestDto request, Class<T> type) {
        if (request == null || request.draft() == null || request.draft().isNull()) {
            throw new CommandException(400, "DRAFT_REQUIRED", "Command draft is required",
                    "Der vollstaendige Entwurf fehlt.", Map.of("draft", "null"));
        }
        try { return objectMapper.treeToValue(request.draft(), type); }
        catch (Exception exception) { throw new CommandException(400, "INVALID_DRAFT", exception.getMessage(),
                "Der Entwurf besitzt nicht das erwartete Format.", Map.of("draft", request.draft())); }
    }

    private List<String> stringList(JsonNode draft, String field) {
        if (draft == null || !draft.has(field) || !draft.get(field).isArray()) return List.of();
        List<String> result = new ArrayList<>();
        draft.get(field).forEach(value -> result.add(value.asText()));
        return List.copyOf(result);
    }

    private String requiredText(JsonNode draft, String field) {
        if (draft == null || !draft.hasNonNull(field) || draft.get(field).asText().isBlank()) {
            throw new CommandException(400, "INVALID_DRAFT", "Required field is missing: " + field,
                    "Ein Pflichtfeld fehlt.", Map.of("draft", nullSafe(draft), "field", field));
        }
        return draft.get(field).asText();
    }

    private String requiredOwner(JsonNode draft, String field) {
        if (draft == null || !draft.hasNonNull(field) || draft.get(field).asText().isBlank()) {
            throw new CommandException(400, "OWNER_REQUIRED", "Owner is required: " + field,
                    "Der definierende Classifier fehlt.", Map.of("draft", nullSafe(draft), "field", field));
        }
        return draft.get(field).asText();
    }

    private CommandException notFound(String type, String id, JsonNode draft) {
        return new CommandException(404, "ELEMENT_NOT_FOUND", type + " not found: " + id,
                "Das referenzierte Modellelement existiert nicht.",
                Map.of("draft", nullSafe(draft), "elementType", type, "elementId", id));
    }

    private UmlClass operationOwner(Project project, String operationId, JsonNode draft) {
        return project.umlModel().classes().stream()
                .filter(owner -> owner.findOperation(new UmlOperationId(operationId)).isPresent())
                .findFirst()
                .orElseThrow(() -> new CommandException(404, "ELEMENT_NOT_FOUND",
                        "OPERATION not found: " + operationId,
                        "Die referenzierte Operation existiert nicht.",
                        Map.of("draft", nullSafe(draft), "elementType", "OPERATION", "elementId", operationId,
                                "target", ref("target:operation:" + operationId, "OPERATION", operationId,
                                        operationId, "umlModel.classes.operations", "TARGET", false))));
    }

    private Object lock(ProjectId projectId) { return projectLocks.computeIfAbsent(projectId.value(), ignored -> new Object()); }
    private String revision(Project project) { return project.metadata().updatedAt().toString(); }
    private String scope(String type) { return Set.of("OBJECT", "OBJECT_LINK", "SLOT").contains(type) ? SNAPSHOT : MODEL; }
    private Object nullSafe(JsonNode value) { return value == null ? "null" : value; }
}
