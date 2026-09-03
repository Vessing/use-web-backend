package de.useweb.backend.application.modeltext;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.useweb.backend.api.dto.modeltext.ApplyModelTextRequestDto;
import de.useweb.backend.api.dto.modeltext.ApplyModelTextResponseDto;
import de.useweb.backend.api.dto.modeltext.ModelTextDto;
import de.useweb.backend.api.dto.ocl.OclDiagnosticDto;
import de.useweb.backend.api.dto.ocl.OclParseRequestDto;
import de.useweb.backend.api.mapper.ProjectDtoMapper;
import de.useweb.backend.application.ocl.OclParseService;
import de.useweb.backend.application.ocl.OclDiagnosticMapper;
import de.useweb.backend.application.project.ProjectService;
import de.useweb.backend.domain.modeltext.ModelText;
import de.useweb.backend.domain.modeltext.ModelTextSourceFile;
import de.useweb.backend.domain.ocl.OclExpression;
import de.useweb.backend.domain.ocl.OclExpressionId;
import de.useweb.backend.domain.project.Project;
import de.useweb.backend.domain.project.ProjectId;
import de.useweb.backend.domain.uml.Multiplicity;
import de.useweb.backend.domain.uml.AggregationKind;
import de.useweb.backend.domain.uml.UmlAssociation;
import de.useweb.backend.domain.uml.UmlAssociationEnd;
import de.useweb.backend.domain.uml.UmlAssociationEndId;
import de.useweb.backend.domain.uml.UmlAssociationId;
import de.useweb.backend.domain.uml.UmlAttribute;
import de.useweb.backend.domain.uml.UmlAttributeId;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlDataType;
import de.useweb.backend.domain.uml.UmlDataTypeId;
import de.useweb.backend.domain.uml.UmlDataTypeProperty;
import de.useweb.backend.domain.uml.UmlEnumeration;
import de.useweb.backend.domain.uml.UmlEnumerationId;
import de.useweb.backend.domain.uml.UmlEnumerationLiteral;
import de.useweb.backend.domain.uml.UmlEnumerationLiteralId;
import de.useweb.backend.domain.uml.UmlInvariant;
import de.useweb.backend.domain.uml.UmlInvariantId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlModelId;
import de.useweb.backend.domain.uml.UmlOperation;
import de.useweb.backend.domain.uml.UmlOperationId;
import de.useweb.backend.domain.uml.UmlOperationContract;
import de.useweb.backend.domain.uml.UmlParameter;
import de.useweb.backend.domain.uml.UmlParameterId;
import de.useweb.backend.domain.uml.UmlQualifierDefinition;
import de.useweb.backend.domain.uml.UmlQualifierId;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.domain.uml.UmlVisibility;
import de.useweb.backend.modeltext.parser.ModelTextParser;
import de.useweb.backend.modeltext.parser.ModelTextParser.ModelTextAssociation;
import de.useweb.backend.modeltext.parser.ModelTextParser.ModelTextAssociationEnd;
import de.useweb.backend.modeltext.parser.ModelTextParser.ModelTextClass;
import de.useweb.backend.modeltext.parser.ModelTextParser.ModelTextDataType;
import de.useweb.backend.modeltext.parser.ModelTextParser.ModelTextEnumeration;
import de.useweb.backend.modeltext.parser.ModelTextParser.ModelTextInvariant;
import de.useweb.backend.modeltext.parser.ModelTextParser.ModelTextOperation;
import de.useweb.backend.modeltext.parser.ModelTextParser.ModelTextOperationContext;
import de.useweb.backend.modeltext.parser.ModelTextParser.ModelTextOperationContract;
import de.useweb.backend.modeltext.parser.ModelTextParser.ModelTextParameter;
import de.useweb.backend.modeltext.parser.ModelTextParser.ModelTextParseResult;
import de.useweb.backend.modeltext.importer.ModelTextImportResolver;
import de.useweb.backend.ocl.contract.OperationConstraintKind;
import de.useweb.backend.ocl.diagnostics.OclDiagnostic;
import de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase;
import de.useweb.backend.ocl.parser.OclParser;
import de.useweb.backend.ocl.typecheck.OclType;
import de.useweb.backend.ocl.typecheck.OclTypeChecker;
import de.useweb.backend.ocl.typecheck.TypeEnvironment;

@Service
public class ModelTextApplicationService {

    private final ProjectService projectService;
    private final ModelTextParser modelTextParser;
    private final OclParseService oclParseService;
    private final ModelTextImportResolver importResolver;
    private final UseModelTextRenderer renderer;
    private final Clock clock;
    private final OclParser oclParser = new OclParser();
    private final OclTypeChecker oclTypeChecker = new OclTypeChecker();
    private final OclDiagnosticMapper diagnosticMapper = new OclDiagnosticMapper();

    @Autowired
    public ModelTextApplicationService(ProjectService projectService, ModelTextParser modelTextParser, OclParseService oclParseService) {
        this(projectService, modelTextParser, oclParseService, new ModelTextImportResolver(modelTextParser), new UseModelTextRenderer(), Clock.systemUTC());
    }

    public ModelTextApplicationService(ProjectService projectService, ModelTextParser modelTextParser, OclParseService oclParseService, Clock clock) {
        this(projectService, modelTextParser, oclParseService, new ModelTextImportResolver(modelTextParser), new UseModelTextRenderer(), clock);
    }

    public ModelTextApplicationService(ProjectService projectService, ModelTextParser modelTextParser,
            OclParseService oclParseService, ModelTextImportResolver importResolver, Clock clock) {
        this(projectService, modelTextParser, oclParseService, importResolver, new UseModelTextRenderer(), clock);
    }

    public ModelTextApplicationService(ProjectService projectService, ModelTextParser modelTextParser,
            OclParseService oclParseService, ModelTextImportResolver importResolver, UseModelTextRenderer renderer, Clock clock) {
        this.projectService = projectService;
        this.modelTextParser = modelTextParser;
        this.oclParseService = oclParseService;
        this.importResolver = importResolver;
        this.renderer = renderer;
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
        String sourceName = request == null ? null : request.sourceName();
        Map<String, String> sourceFiles = mergedSourceFiles(currentProject.modelText(), request);
        var resolved = importResolver.resolve(sourceName, text, sourceFiles);
        ModelTextParseResult parseResult = resolved.model();
        List<OclDiagnosticDto> diagnostics = new ArrayList<>(parseResult.diagnostics());
        diagnostics.addAll(parseOclDiagnostics(parseResult));

        ModelText modelText = new ModelText(
                text,
                request == null || request.format() == null ? "USE_MODEL_TEXT" : request.format(),
                "mvp-subset",
                Instant.now(clock),
                sourceName,
                request == null ? null : firstNonBlank(request.sourceOrigin(), request.sourceFormat()),
                resolved.provenance(),
                sourceFiles.entrySet().stream().map(source ->
                        new ModelTextSourceFile(source.getKey(), source.getValue())).toList());

        boolean hasErrors = diagnostics.stream().anyMatch(diagnostic -> "ERROR".equalsIgnoreCase(diagnostic.severity()));
        boolean hasSupportedModelParts = parseResult.hasSupportedModelParts();
        UmlModel importedModel = null;
        if (!hasErrors && hasSupportedModelParts) {
            importedModel = toUmlModel(currentProject.umlModel().id(), currentProject.umlModel().name(), parseResult);
            diagnostics.addAll(typecheckOclDiagnostics(importedModel));
            hasErrors = diagnostics.stream().anyMatch(diagnostic -> "ERROR".equalsIgnoreCase(diagnostic.severity()));
        }
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
            UmlModel umlModel = importedModel;
            modelText = new ModelText(
                    renderer.render(umlModel),
                    modelText.language(), modelText.languageVersion(), modelText.updatedAt(), modelText.sourceName(),
                    modelText.sourceOrigin(), modelText.sources(), modelText.sourceFiles());
            projectToSave = new Project(
                    currentProject.id(),
                    currentProject.metadata(),
                    modelText,
                    umlModel,
                    currentProject.objectModel(),
                    currentProject.layout(),
                    currentProject.definitions());
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
        return new Project(project.id(), project.metadata(), modelText, project.umlModel(), project.objectModel(),
                project.layout(), project.definitions());
    }

    private Map<String, String> mergedSourceFiles(ModelText currentModelText, ApplyModelTextRequestDto request) {
        Map<String, String> result = new LinkedHashMap<>();
        if (currentModelText != null && (request == null || !request.replaceSourceFiles())) {
            currentModelText.sourceFiles().forEach(source -> result.put(source.sourcePath(), source.text()));
        }
        if (request != null) {
            result.putAll(request.sourceFiles());
        }
        return Collections.unmodifiableMap(result);
    }

    private String defaultModelText(Project project) {
        return "model " + sanitizeIdentifier(project.metadata().name()) + System.lineSeparator();
    }

    private List<OclDiagnosticDto> parseOclDiagnostics(ModelTextParseResult parseResult) {
        List<OclDiagnosticDto> diagnostics = new ArrayList<>();
        for (ModelTextInvariant invariant : parseResult.invariants()) {
            diagnostics.addAll(oclParseService.parse(new OclParseRequestDto(invariant.expression())).diagnostics());
        }
        parseResult.classes().forEach(type -> type.operations().forEach(operation -> {
            addParseDiagnostics(diagnostics, operation.bodyExpression());
            operation.contracts().forEach(contract -> addParseDiagnostics(diagnostics, contract.expression()));
        }));
        parseResult.operationContexts().forEach(context -> context.contracts()
                .forEach(contract -> addParseDiagnostics(diagnostics, contract.expression())));
        return diagnostics;
    }

    private void addParseDiagnostics(List<OclDiagnosticDto> diagnostics, String expression) {
        if (expression != null && !expression.isBlank()) {
            diagnostics.addAll(oclParseService.parse(new OclParseRequestDto(expression)).diagnostics());
        }
    }

    private UmlModel toUmlModel(UmlModelId umlModelId, String fallbackName, ModelTextParseResult parseResult) {
        List<UmlClass> classes = parseResult.classes().stream()
                .map(type -> toUmlClass(type, parseResult.operationContexts())).toList();
        List<UmlEnumeration> enumerations = parseResult.enumerations().stream().map(this::toUmlEnumeration).toList();
        List<UmlDataType> dataTypes = parseResult.dataTypes().stream().map(this::toUmlDataType).toList();
        Map<String, List<UmlAssociationEndId>> endIdsByRole = associationEndIdsByRole(parseResult.associations());
        List<UmlAssociation> associations = parseResult.associations().stream()
                .map(association -> toUmlAssociation(association, endIdsByRole)).toList();
        List<UmlInvariant> invariants = parseResult.invariants().stream().map(this::toUmlInvariant).toList();
        String modelName = parseResult.modelName() == null || parseResult.modelName().isBlank() ? fallbackName : parseResult.modelName();
        return new UmlModel(umlModelId, modelName, classes, associations, invariants, enumerations,
                List.of(), List.of(), dataTypes);
    }

    private UmlClass toUmlClass(ModelTextClass modelTextClass, List<ModelTextOperationContext> operationContexts) {
        String classKey = kebab(modelTextClass.name());
        return new UmlClass(
                new UmlClassId("class-" + classKey),
                modelTextClass.name(),
                modelTextClass.attributes().stream()
                        .map(attribute -> new UmlAttribute(
                                new UmlAttributeId("attr-" + classKey + "-" + kebab(attribute.name())),
                                attribute.name(),
                                typeOf(attribute.type()),
                                attribute.derived(),
                                attribute.deriveExpression(),
                                attribute.initExpression()))
                        .toList(),
                modelTextClass.operations().stream()
                        .map(operation -> toUmlOperation(classKey, operation,
                                matchingContexts(modelTextClass.name(), operation, operationContexts)))
                        .toList(),
                modelTextClass.abstractClass(),
                modelTextClass.superClassNames().stream()
                        .map(name -> new UmlClassId("class-" + kebab(name)))
                        .toList());
    }

    private List<ModelTextOperationContext> matchingContexts(String className, ModelTextOperation operation,
            List<ModelTextOperationContext> contexts) {
        return contexts.stream()
                .filter(context -> context.contextClass().equals(className)
                        && context.operationName().equals(operation.name())
                        && context.parameters().stream().map(ModelTextParameter::type).toList()
                                .equals(operation.parameters().stream().map(ModelTextParameter::type).toList()))
                .toList();
    }

    private UmlOperation toUmlOperation(String classKey, ModelTextOperation operation) {
        return toUmlOperation(classKey, operation, List.of());
    }

    private UmlOperation toUmlOperation(String classKey, ModelTextOperation operation,
            List<ModelTextOperationContext> externalContexts) {
        String operationKey = kebab(operation.name());
        List<UmlParameter> parameters = operation.parameters().stream()
                .map(parameter -> new UmlParameter(
                        new UmlParameterId("param-" + classKey + "-" + operationKey + "-" + kebab(parameter.name())),
                        parameter.name(),
                        typeOf(parameter.type())))
                .toList();
        List<ModelTextOperationContract> importedContracts = new ArrayList<>(operation.contracts());
        externalContexts.forEach(context -> importedContracts.addAll(context.contracts()));
        int[] contractIndex = {0};
        List<UmlOperationContract> contracts = importedContracts.stream().map(contract ->
                new UmlOperationContract(
                        "contract-" + classKey + "-" + operationKey + "-" + contract.kind().toLowerCase()
                                + "-" + kebab(contract.name()) + "-" + contractIndex[0]++,
                        contract.name(), UmlOperationContract.Kind.valueOf(contract.kind()),
                        contract.expression(), true)).toList();
        return new UmlOperation(
                new UmlOperationId("op-" + classKey + "-" + operationKey),
                operation.name(),
                typeOf(operation.returnType()),
                parameters,
                operation.bodyExpression(), UmlVisibility.PUBLIC, false, false, false, contracts, List.of());
    }

    private UmlEnumeration toUmlEnumeration(ModelTextEnumeration enumeration) {
        String enumerationKey = kebab(enumeration.name());
        UmlEnumerationId id = new UmlEnumerationId("enum-" + enumerationKey);
        int[] index = {0};
        return new UmlEnumeration(id, enumeration.name(), enumeration.literals().stream()
                .map(literal -> new UmlEnumerationLiteral(
                        new UmlEnumerationLiteralId(id.value() + ":literal:" + index[0]++), literal))
                .toList(), null, UmlVisibility.PUBLIC);
    }

    private UmlDataType toUmlDataType(ModelTextDataType dataType) {
        String dataTypeKey = kebab(dataType.name());
        return new UmlDataType(
                new UmlDataTypeId("datatype-" + dataTypeKey),
                dataType.name(),
                dataType.properties().stream().map(property -> new UmlDataTypeProperty(
                        "datatype-property-" + dataTypeKey + "-" + kebab(property.name()),
                        property.name(), typeOf(property.type()))).toList(),
                null,
                dataType.operations().stream().map(operation -> toUmlOperation(dataTypeKey, operation)).toList());
    }

    private Map<String, List<UmlAssociationEndId>> associationEndIdsByRole(List<ModelTextAssociation> associations) {
        Map<String, List<UmlAssociationEndId>> result = new HashMap<>();
        for (ModelTextAssociation association : associations) {
            String associationKey = kebab(association.name());
            for (int endIndex = 0; endIndex < association.ends().size(); endIndex++) {
                ModelTextAssociationEnd end = association.ends().get(endIndex);
                String roleName = effectiveRoleName(end);
                result.computeIfAbsent(roleName, ignored -> new ArrayList<>())
                        .add(associationEndId(associationKey, roleName, endIndex));
            }
        }
        return result;
    }

    private UmlAssociation toUmlAssociation(ModelTextAssociation association,
            Map<String, List<UmlAssociationEndId>> endIdsByRole) {
        String associationKey = kebab(association.name());
        int[] endIndex = {0};
        return new UmlAssociation(
                new UmlAssociationId("assoc-" + associationKey),
                association.name(),
                association.ends().stream()
                        .map(end -> toUmlAssociationEnd(associationKey, association.kind(), endIndex[0]++, end,
                                endIdsByRole))
                        .toList(),
                association.associationClassName() == null ? null
                        : new UmlClassId("class-" + kebab(association.associationClassName())));
    }

    private UmlAssociationEnd toUmlAssociationEnd(String associationKey, String associationKind, int endIndex,
            ModelTextAssociationEnd end, Map<String, List<UmlAssociationEndId>> endIdsByRole) {
        String roleName = effectiveRoleName(end);
        UmlAssociationEndId endId = associationEndId(associationKey, roleName, endIndex);
        int[] qualifierOrder = {0};
        return new UmlAssociationEnd(
                endId,
                new UmlClassId("class-" + kebab(end.className())),
                roleName,
                multiplicity(end.multiplicity()),
                true,
                end.ordered(),
                end.unique(),
                end.derived(),
                end.union(),
                end.subsettedRoleNames().stream().map(role -> resolveAssociationEndId(role, endIdsByRole)).toList(),
                end.redefinedRoleNames().stream().map(role -> resolveAssociationEndId(role, endIdsByRole)).toList(),
                end.qualifiers().stream().map(qualifier -> qualifier(associationKey, roleName, endId, qualifier,
                        qualifierOrder[0]++)).toList(),
                aggregationKind(associationKind, endIndex),
                end.deriveExpression());
    }

    private UmlQualifierDefinition qualifier(String associationKey, String roleName, UmlAssociationEndId associationEndId,
            ModelTextParameter qualifier, int order) {
        String ownerKey = roleName == null ? associationEndId.value() : associationKey + "-" + kebab(roleName);
        return new UmlQualifierDefinition(
                new UmlQualifierId("qualifier-" + ownerKey + "-" + kebab(qualifier.name())),
                qualifier.name(), typeOf(qualifier.type()), order);
    }

    private AggregationKind aggregationKind(String associationKind, int endIndex) {
        if (endIndex != 0 || associationKind == null) return AggregationKind.NONE;
        return switch (associationKind) {
            case "AGGREGATION" -> AggregationKind.SHARED;
            case "COMPOSITION" -> AggregationKind.COMPOSITE;
            default -> AggregationKind.NONE;
        };
    }

    private UmlAssociationEndId resolveAssociationEndId(String roleName,
            Map<String, List<UmlAssociationEndId>> endIdsByRole) {
        List<UmlAssociationEndId> candidates = endIdsByRole.getOrDefault(roleName, List.of());
        if (candidates.size() != 1) {
            throw new IllegalArgumentException(candidates.isEmpty()
                    ? "Association end reference uses unknown role '" + roleName + "'"
                    : "Association end reference is ambiguous for role '" + roleName + "'");
        }
        return candidates.getFirst();
    }

    private String optionalRoleName(String roleName) {
        return roleName == null || roleName.isBlank() ? null : roleName.trim();
    }

    private String effectiveRoleName(ModelTextAssociationEnd end) {
        String explicitRoleName = optionalRoleName(end.roleName());
        if (explicitRoleName != null) {
            return explicitRoleName;
        }
        String className = end.className();
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }

    private UmlAssociationEndId associationEndId(String associationKey, String roleName, int endIndex) {
        String suffix = roleName == null ? "end-" + endIndex : kebab(roleName);
        return new UmlAssociationEndId("assocend-" + associationKey + "-" + suffix);
    }

    private UmlInvariant toUmlInvariant(ModelTextInvariant invariant) {
        String invariantKey = kebab(invariant.name());
        return new UmlInvariant(
                new UmlInvariantId("inv-" + invariantKey),
                invariant.name(),
                new UmlClassId("class-" + kebab(invariant.contextClass())),
                new OclExpression(new OclExpressionId("expr-" + invariantKey), invariant.expression(), "mvp-subset"),
                true, invariant.contextVariableNames(), invariant.existential());
    }

    private List<OclDiagnosticDto> typecheckOclDiagnostics(UmlModel model) {
        List<OclDiagnosticDto> diagnostics = new ArrayList<>();
        model.invariants().forEach(invariant -> {
            var parsed = oclParser.parse(invariant.expression().text());
            if (parsed.success()) {
                var checked = oclTypeChecker.checkInvariant(model, invariant.contextClassId(), parsed.ast(),
                        invariant.contextVariableNames());
                diagnostics.addAll(diagnosticMapper.toDto(checked.diagnostics(), invariant.id().value(),
                        "INVARIANT", null));
            }
        });
        model.classes().forEach(owner -> owner.operations().forEach(operation -> {
            Map<String, OclType> parameters = new HashMap<>();
            TypeEnvironment base = new TypeEnvironment(model, owner);
            operation.parameters().forEach(parameter -> parameters.put(parameter.name(),
                    OclType.fromUmlType(parameter.type(), base)));
            typecheckExpression(diagnostics, new TypeEnvironment(model, owner, parameters), operation.bodyExpression(),
                    OclType.fromUmlType(operation.returnType(), base), operation.id().value(), "OPERATION_BODY");
            operation.contracts().forEach(contract -> {
                OperationConstraintKind kind = contract.kind() == UmlOperationContract.Kind.PRE
                        ? OperationConstraintKind.PRECONDITION : OperationConstraintKind.POSTCONDITION;
                Map<String, OclType> bindings = new HashMap<>(parameters);
                if (kind == OperationConstraintKind.POSTCONDITION && !operation.returnType().equals(UmlType.VOID)) {
                    bindings.put("result", OclType.fromUmlType(operation.returnType(), base));
                }
                typecheckExpression(diagnostics, new TypeEnvironment(model, owner, bindings, kind), contract.expression(),
                        OclType.BOOLEAN, contract.id(), "OPERATION_CONTRACT");
            });
        }));
        return List.copyOf(diagnostics);
    }

    private void typecheckExpression(List<OclDiagnosticDto> diagnostics, TypeEnvironment environment,
            String expression, OclType expectedType, String sourceId, String sourceKind) {
        if (expression == null || expression.isBlank()) return;
        var parsed = oclParser.parse(expression);
        if (!parsed.success()) return;
        var checked = oclTypeChecker.checkExpression(environment, parsed.ast());
        diagnostics.addAll(diagnosticMapper.toDto(checked.diagnostics(), sourceId, sourceKind, null));
        if (checked.success() && expectedType != null && !checked.resultType().conformsTo(expectedType)) {
            OclDiagnostic mismatch = new OclDiagnostic(OclDiagnosticPhase.TYPECHECK, "OCL_EMBEDDED_TYPE_MISMATCH",
                    "ERROR", "Embedded OCL expression result does not conform to the declared type.",
                    parsed.ast().sourceRange(), List.of(expectedType.displayName()), checked.resultType().displayName());
            diagnostics.add(diagnosticMapper.toDto(mismatch, sourceId, sourceKind, null));
        }
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
            association.ends().forEach(end -> {
                ids.add(end.id().value());
                end.qualifiers().forEach(qualifier -> ids.add(qualifier.id().value()));
            });
        });
        umlModel.invariants().forEach(invariant -> ids.add(invariant.id().value()));
        umlModel.enumerations().forEach(enumeration -> {
            ids.add(enumeration.id().value());
            enumeration.literalDefinitions().forEach(literal -> ids.add(literal.id().value()));
        });
        umlModel.dataTypes().forEach(dataType -> {
            ids.add(dataType.id().value());
            dataType.properties().forEach(property -> ids.add(property.id()));
            dataType.operations().forEach(operation -> ids.add(operation.id().value()));
        });
        return ids;
    }

    private Multiplicity multiplicity(String raw) {
        String value = raw == null || raw.isBlank() ? "1" : raw.replaceAll("\\s+", "");
        if (value.contains(",")) {
            int lower = Integer.MAX_VALUE;
            Integer upper = 0;
            boolean unbounded = false;
            for (String range : value.split(",")) {
                if ("*".equals(range)) {
                    lower = 0;
                    unbounded = true;
                    upper = null;
                } else if (range.contains("..")) {
                    String[] bounds = range.split("\\.\\.", 2);
                    lower = Math.min(lower, Integer.parseInt(bounds[0]));
                    if ("*".equals(bounds[1])) {
                        unbounded = true;
                        upper = null;
                    } else if (!unbounded) {
                        upper = Math.max(upper, Integer.parseInt(bounds[1]));
                    }
                } else {
                    int exact = Integer.parseInt(range);
                    lower = Math.min(lower, exact);
                    if (!unbounded) upper = Math.max(upper, exact);
                }
            }
            return new Multiplicity(lower, upper, unbounded, value);
        }
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

}
