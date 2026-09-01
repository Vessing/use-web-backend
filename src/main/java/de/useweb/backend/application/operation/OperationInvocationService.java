package de.useweb.backend.application.operation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import de.useweb.backend.api.dto.operation.NamedElementReferenceDto;
import de.useweb.backend.api.dto.operation.NamedOperationValueDto;
import de.useweb.backend.api.dto.operation.OperationArgumentDto;
import de.useweb.backend.api.dto.operation.OperationInvocationRequestDto;
import de.useweb.backend.api.dto.operation.OperationInvocationResultDto;
import de.useweb.backend.api.dto.operation.OperationLifecycleDiffDto;
import de.useweb.backend.api.dto.operation.OperationContractResultDto;
import de.useweb.backend.api.dto.snapshot.SlotValueDto;
import de.useweb.backend.application.project.ProjectService;
import de.useweb.backend.domain.project.Project;
import de.useweb.backend.domain.project.ProjectId;
import de.useweb.backend.domain.snapshot.ObjectInstance;
import de.useweb.backend.domain.snapshot.ObjectInstanceId;
import de.useweb.backend.domain.snapshot.ObjectModel;
import de.useweb.backend.domain.snapshot.ObjectModelId;
import de.useweb.backend.domain.snapshot.SlotValue;
import de.useweb.backend.domain.uml.ParameterDirection;
import de.useweb.backend.domain.uml.UmlOperationId;
import de.useweb.backend.domain.uml.UmlParameter;
import de.useweb.backend.domain.uml.UmlParameterId;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.error.ObjectModelException;
import de.useweb.backend.validation.service.ValidationService;
import de.useweb.backend.application.ocl.OclDiagnosticMapper;
import de.useweb.backend.domain.uml.UmlOperationContract;
import de.useweb.backend.ocl.contract.OperationConstraintKind;
import de.useweb.backend.ocl.contract.OperationContext;
import de.useweb.backend.ocl.contract.OperationContextReference;
import de.useweb.backend.ocl.contract.OperationContract;
import de.useweb.backend.ocl.contract.OperationContractId;
import de.useweb.backend.ocl.contract.OperationContractResult;
import de.useweb.backend.ocl.contract.OperationContractService;
import de.useweb.backend.ocl.contract.OperationInvocationId;
import de.useweb.backend.ocl.contract.OperationResultSlot;
import de.useweb.backend.ocl.parser.OclParser;
import de.useweb.backend.ocl.value.BooleanValue;
import de.useweb.backend.ocl.value.IntegerValue;
import de.useweb.backend.ocl.value.OclValue;
import de.useweb.backend.ocl.value.OclVoidValue;
import de.useweb.backend.ocl.value.ObjectValue;
import de.useweb.backend.ocl.value.RealValue;
import de.useweb.backend.ocl.value.StringValue;
import de.useweb.backend.ocl.value.EnumValue;
import de.useweb.backend.ocl.value.DataTypeValue;
import de.useweb.backend.ocl.definition.OclDefinitionService;
import de.useweb.backend.ocl.definition.OclProjectDefinitionFactory;

@Service
public class OperationInvocationService {
    private final ProjectService projectService;
    private final OperationImplementationRegistry registry;
    private final ValidationService validationService;
    private final OperationResolver resolver = new OperationResolver();
    private final OperationContractService contractService = new OperationContractService();
    private final OclParser contractParser = new OclParser();
    private final OclDiagnosticMapper diagnosticMapper = new OclDiagnosticMapper();

    public OperationInvocationService(ProjectService projectService, OperationImplementationRegistry registry,
            ValidationService validationService) {
        this.projectService = projectService;
        this.registry = registry;
        this.validationService = validationService;
    }

    public synchronized OperationInvocationResultDto invoke(ProjectId projectId, OperationInvocationRequestDto request) {
        Project project = projectService.loadProject(projectId);
        long revision = revision(project);
        if (request.expectedRevision() != null && request.expectedRevision() != revision) {
            throw error("STALE_PROJECT_REVISION", "Project revision changed before invocation.",
                    "Das Projekt wurde zwischenzeitlich geaendert. Bitte laden Sie den aktuellen Stand.",
                    Map.of("expectedRevision", request.expectedRevision(), "actualRevision", revision));
        }
        ObjectInstance receiver = project.objectModel().findObject(new ObjectInstanceId(request.receiverObjectId()))
                .orElseThrow(() -> error("UNKNOWN_RECEIVER", "Unknown invocation receiver.",
                        "Das ausgewaehlte Empfaengerobjekt existiert nicht.",
                        Map.of("receiverObjectId", request.receiverObjectId())));
        ResolvedOperation resolved;
        try {
            resolved = resolver.resolve(project.umlModel(), receiver.classId(), new UmlOperationId(request.operationId()));
        } catch (IllegalArgumentException exception) {
            throw error("OPERATION_RESOLUTION_ERROR", exception.getMessage(),
                    "Die Operation konnte fuer den Laufzeittyp nicht eindeutig aufgeloest werden.",
                    Map.of("receiverObjectId", receiver.id().value(), "operationId", request.operationId()));
        }
        if (resolved.operation().abstractOperation()) {
            throw error("ABSTRACT_OPERATION", "Abstract operation cannot be invoked.",
                    "Eine abstrakte Operation kann nicht direkt ausgefuehrt werden.",
                    Map.of("operationId", resolved.operation().id().value()));
        }
        Map<UmlParameterId, SlotValue> arguments = bindArguments(resolved.operation().parameters(), request.arguments());
        OperationImplementation implementation = registry.find(resolved.operation().id())
                .orElseGet(() -> bodyImplementation(project, resolved, receiver, arguments));
        String invocationId = "invocation-" + UUID.randomUUID();
        List<OperationContractResultDto> contractResults = new ArrayList<>();
        OperationContext preContext = contractContext(invocationId, project, resolved, receiver, null,
                arguments, null, Map.of(), OperationConstraintKind.PRECONDITION);
        evaluateContracts(resolved.operation(), OperationConstraintKind.PRECONDITION, preContext, contractResults);
        addDisabledContracts(resolved.operation(), OperationConstraintKind.PRECONDITION, contractResults);
        if (hasFailedContract(contractResults, "PRE")) {
            addNotEvaluatedContracts(resolved.operation(), OperationConstraintKind.POSTCONDITION, contractResults);
            return result(invocationId, "BLOCKED", project, project.objectModel(), project.objectModel(), null, receiver,
                    resolved, null, Map.of(), revision, contractCodes(contractResults), contractResults);
        }
        OperationExecutionResult execution;
        try {
            execution = implementation.execute(new OperationExecutionContext(project, receiver, resolved.operation(), arguments));
            validateExecutionResult(project, resolved, execution);
        } catch (RuntimeException exception) {
            addNotEvaluatedContracts(resolved.operation(), OperationConstraintKind.POSTCONDITION, contractResults);
            return result(invocationId, "ROLLED_BACK", project, project.objectModel(), project.objectModel(), null,
                    receiver, resolved, null, Map.of(), revision, List.of(exception.getMessage()), contractResults);
        }

        ObjectModel candidate = new ObjectModel(new ObjectModelId("snapshot-" + invocationId),
                "After " + resolved.operation().name(), execution.candidateState().objects(), execution.candidateState().links());
        Project candidateProject = new Project(project.id(), project.metadata(), project.modelText(), project.umlModel(),
                candidate, project.layout(), project.definitions());
        var validation = validationService.validate(candidateProject);
        if (!validation.findings().isEmpty()) {
            addNotEvaluatedContracts(resolved.operation(), OperationConstraintKind.POSTCONDITION, contractResults);
            return result(invocationId, "ROLLED_BACK", project, project.objectModel(), candidate, candidate, receiver, resolved,
                    execution.result(), execution.outValues(), revision,
                    validation.findings().stream().map(finding -> finding.code().name()).distinct().toList(), contractResults);
        }
        OperationContext postContext = contractContext(invocationId, project, resolved, receiver, candidate,
                arguments, execution.result(), execution.outValues(), OperationConstraintKind.POSTCONDITION);
        evaluateContracts(resolved.operation(), OperationConstraintKind.POSTCONDITION, postContext, contractResults);
        addDisabledContracts(resolved.operation(), OperationConstraintKind.POSTCONDITION, contractResults);
        if (hasFailedContract(contractResults, "POST")) {
            return result(invocationId, "ROLLED_BACK", project, project.objectModel(), candidate, candidate, receiver,
                    resolved, execution.result(), execution.outValues(), revision, contractCodes(contractResults), contractResults);
        }
        Project saved = projectService.saveProject(candidateProject);
        return result(invocationId, "SUCCEEDED", saved, project.objectModel(), candidate, candidate, receiver, resolved,
                execution.result(), execution.outValues(), revision(saved), List.of(), contractResults);
    }

    private OperationImplementation bodyImplementation(Project project, ResolvedOperation resolved,
            ObjectInstance receiver, Map<UmlParameterId, SlotValue> arguments) {
        if (resolved.operation().bodyExpression() == null) {
            throw error("OPERATION_IMPLEMENTATION_MISSING", "No operation implementation is registered.",
                    "Fuer diese Operation ist noch keine ausfuehrbare Semantik registriert.",
                    Map.of("operationId", resolved.operation().id().value()));
        }
        var definitions = new OclProjectDefinitionFactory().definitions(project);
        var body = definitions.stream()
                .filter(definition -> resolved.operation().id().equals(definition.operationId()))
                .findFirst().orElseThrow();
        OclDefinitionService service = new OclDefinitionService(project.umlModel(), definitions);
        Map<String, OclValue> bindings = new LinkedHashMap<>();
        resolved.operation().parameters().forEach(parameter -> {
            SlotValue value = arguments.get(parameter.id());
            if (value != null) bindings.put(parameter.name(), oclValue(value, project, project.objectModel()));
        });
        return new OperationImplementation() {
            @Override public UmlOperationId operationId() { return resolved.operation().id(); }

            @Override public OperationExecutionResult execute(OperationExecutionContext ignored) {
                var evaluated = service.evaluate(body, project.umlModel(), project.objectModel(), receiver, bindings);
                if (!evaluated.success()) {
                    var diagnostic = evaluated.diagnostics().getFirst();
                    throw new IllegalStateException(diagnostic.code() + ": " + diagnostic.message());
                }
                return OperationExecutionResult.unchanged(project.objectModel(), slotValue(evaluated.value(), project));
            }
        };
    }

    private SlotValue slotValue(OclValue value, Project project) {
        return switch (value) {
            case BooleanValue booleanValue -> SlotValue.ofBoolean(booleanValue.value());
            case IntegerValue integerValue -> SlotValue.ofInteger(integerValue.value());
            case RealValue realValue -> SlotValue.ofReal(realValue.value());
            case StringValue stringValue -> SlotValue.ofString(stringValue.value());
            case EnumValue enumValue -> new SlotValue(enumValue.literal(), new UmlType(enumValue.enumerationName()));
            case DataTypeValue dataTypeValue -> new SlotValue(dataTypeValue.properties().entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                            entry -> slotValue(entry.getValue(), project).value())),
                    new UmlType(dataTypeValue.dataTypeName()));
            case ObjectValue objectValue -> new SlotValue(objectValue.object().id().value(),
                    new UmlType(project.umlModel().findClass(objectValue.object().classId()).orElseThrow().name()));
            case OclVoidValue ignored -> new SlotValue(null, resolvedVoidType());
            default -> throw new IllegalStateException("OPERATION_BODY_RESULT_UNSUPPORTED");
        };
    }

    private UmlType resolvedVoidType() {
        return UmlType.VOID;
    }

    private void evaluateContracts(de.useweb.backend.domain.uml.UmlOperation operation, OperationConstraintKind kind,
            OperationContext context, List<OperationContractResultDto> target) {
        operation.contracts().stream().filter(UmlOperationContract::enabled)
                .filter(contract -> matches(contract, kind)).forEach(contract -> {
                    var parsed = contractParser.parse(contract.expression());
                    if (!parsed.success()) {
                        target.add(new OperationContractResultDto(contract.id(), contract.name(), contract.kind().name(),
                                "CONTEXT_ERROR", diagnosticMapper.toDto(parsed.diagnostics(), contract.id(),
                                        "OPERATION_CONTRACT", null)));
                        return;
                    }
                    OperationContract runtime = new OperationContract(new OperationContractId(contract.id()),
                            contract.name(), context.reference(), kind, contract.expression(), parsed.ast());
                    OperationContractResult evaluated = contractService.evaluate(context, runtime);
                    target.add(new OperationContractResultDto(contract.id(), contract.name(), contract.kind().name(),
                            evaluated.status().name(), diagnosticMapper.toDto(evaluated.diagnostics(), contract.id(),
                                    "OPERATION_CONTRACT", null)));
                });
    }

    private void addDisabledContracts(de.useweb.backend.domain.uml.UmlOperation operation, OperationConstraintKind kind,
            List<OperationContractResultDto> target) {
        operation.contracts().stream().filter(contract -> !contract.enabled()).filter(contract -> matches(contract, kind))
                .forEach(contract -> target.add(new OperationContractResultDto(contract.id(), contract.name(),
                        contract.kind().name(), "NOT_EVALUATED", List.of())));
    }

    private void addNotEvaluatedContracts(de.useweb.backend.domain.uml.UmlOperation operation,
            OperationConstraintKind kind, List<OperationContractResultDto> target) {
        operation.contracts().stream().filter(contract -> matches(contract, kind))
                .forEach(contract -> target.add(new OperationContractResultDto(contract.id(), contract.name(),
                        contract.kind().name(), "NOT_EVALUATED", List.of())));
    }

    private boolean matches(UmlOperationContract contract, OperationConstraintKind kind) {
        return contract.kind() == UmlOperationContract.Kind.PRE && kind == OperationConstraintKind.PRECONDITION
                || contract.kind() == UmlOperationContract.Kind.POST && kind == OperationConstraintKind.POSTCONDITION;
    }

    private boolean hasFailedContract(List<OperationContractResultDto> results, String kind) {
        return results.stream().filter(result -> result.kind().equals(kind))
                .anyMatch(result -> result.status().equals("VIOLATED") || result.status().equals("CONTEXT_ERROR"));
    }

    private List<String> contractCodes(List<OperationContractResultDto> results) {
        return results.stream().flatMap(result -> result.diagnostics().stream()).map(diagnostic -> diagnostic.code())
                .distinct().toList();
    }

    private OperationContext contractContext(String invocationId, Project project, ResolvedOperation resolved,
            ObjectInstance receiver, ObjectModel postState, Map<UmlParameterId, SlotValue> inputValues,
            SlotValue result, Map<UmlParameterId, SlotValue> outValues, OperationConstraintKind kind) {
        Map<UmlParameterId, OclValue> values = new LinkedHashMap<>();
        resolved.operation().parameters().forEach(parameter -> {
            SlotValue value = kind == OperationConstraintKind.POSTCONDITION && outValues.containsKey(parameter.id())
                    ? outValues.get(parameter.id()) : inputValues.get(parameter.id());
            ObjectModel selectedState = postState == null ? project.objectModel() : postState;
            values.put(parameter.id(), value == null ? OclVoidValue.INSTANCE : oclValue(value, project, selectedState));
        });
        OperationResultSlot slot = result == null ? OperationResultSlot.unavailable()
                : OperationResultSlot.of(oclValue(result, project,
                        postState == null ? project.objectModel() : postState));
        return new OperationContext(new OperationInvocationId(invocationId),
                new OperationContextReference(resolved.owner().id(), resolved.operation().id()), project.umlModel(),
                receiver.id(), project.objectModel(), postState, values, slot);
    }

    private OclValue oclValue(SlotValue value, Project project, ObjectModel state) {
        if (value.value() == null) return OclVoidValue.INSTANCE;
        if (value.valueType().equals(UmlType.BOOLEAN)) return new BooleanValue((Boolean) value.value());
        if (value.valueType().equals(UmlType.INTEGER)) return new IntegerValue(((Number) value.value()).intValue());
        if (value.valueType().equals(UmlType.UNLIMITED_NATURAL))
            return new IntegerValue(((Number) value.value()).intValue());
        if (value.valueType().equals(UmlType.REAL)) return new RealValue(((Number) value.value()).doubleValue());
        if (value.valueType().equals(UmlType.STRING)) return new StringValue(String.valueOf(value.value()));
        var enumeration = project.umlModel().findEnumerationByName(value.valueType().name());
        if (enumeration.isPresent()) {
            return new EnumValue(enumeration.get().id(), enumeration.get().name(), String.valueOf(value.value()));
        }
        var dataType = project.umlModel().findDataTypeByName(value.valueType().name());
        if (dataType.isPresent() && value.value() instanceof Map<?, ?> rawProperties) {
            Map<String, OclValue> properties = new LinkedHashMap<>();
            dataType.get().properties().forEach(property -> properties.put(property.name(),
                    rawProperties.containsKey(property.name())
                            ? oclValue(new SlotValue(rawProperties.get(property.name()), property.type()), project, state)
                            : OclVoidValue.INSTANCE));
            return new DataTypeValue(dataType.get().id(), dataType.get().name(), properties);
        }
        return state.findObject(new ObjectInstanceId(String.valueOf(value.value())))
                .<OclValue>map(ObjectValue::new).orElse(OclVoidValue.INSTANCE);
    }

    private Map<UmlParameterId, SlotValue> bindArguments(List<UmlParameter> parameters,
            List<OperationArgumentDto> supplied) {
        Map<String, OperationArgumentDto> byId = new LinkedHashMap<>();
        for (OperationArgumentDto argument : supplied == null ? List.<OperationArgumentDto>of() : supplied) {
            if (byId.put(argument.parameterId(), argument) != null) {
                throw error("DUPLICATE_ARGUMENT", "Operation argument is bound more than once.",
                        "Ein Operationsparameter wurde mehrfach belegt.", Map.of("parameterId", argument.parameterId()));
            }
        }
        Map<UmlParameterId, SlotValue> result = new LinkedHashMap<>();
        for (UmlParameter parameter : parameters) {
            OperationArgumentDto argument = byId.remove(parameter.id().value());
            if (parameter.direction() == ParameterDirection.OUT) {
                if (argument != null) throw argumentError("OUT_PARAMETER_HAS_INPUT", parameter, null);
                continue;
            }
            if (argument == null || argument.value() == null) throw argumentError("MISSING_ARGUMENT", parameter, null);
            SlotValue value = slotValue(argument.value());
            if (!compatible(parameter.type(), value.valueType())) {
                throw argumentError("ARGUMENT_TYPE_MISMATCH", parameter, value.valueType().name());
            }
            result.put(parameter.id(), value);
        }
        if (!byId.isEmpty()) throw error("UNKNOWN_ARGUMENT", "Unknown operation argument.",
                "Die Anfrage enthaelt einen unbekannten Operationsparameter.", Map.of("parameterIds", byId.keySet()));
        return Map.copyOf(result);
    }

    private void validateExecutionResult(Project project, ResolvedOperation resolved, OperationExecutionResult execution) {
        if (resolved.operation().query() && !sameState(project.objectModel(), execution.candidateState())) {
            throw new IllegalStateException("QUERY_STATE_MUTATION");
        }
        if (resolved.operation().returnType().equals(UmlType.VOID)) {
            if (execution.result() != null) throw new IllegalStateException("VOID_OPERATION_RETURNED_VALUE");
        } else if (execution.result() == null
                || !compatible(resolved.operation().returnType(), execution.result().valueType())) {
            throw new IllegalStateException("OPERATION_RESULT_TYPE_MISMATCH");
        }
        for (UmlParameter parameter : resolved.operation().parameters()) {
            SlotValue out = execution.outValues().get(parameter.id());
            if (parameter.direction() == ParameterDirection.IN && out != null) {
                throw new IllegalStateException("IN_PARAMETER_RETURNED_OUT_VALUE");
            }
            if (parameter.direction() != ParameterDirection.IN
                    && (out == null || !compatible(parameter.type(), out.valueType()))) {
                throw new IllegalStateException("OUT_VALUE_TYPE_MISMATCH");
            }
        }
    }

    private OperationInvocationResultDto result(String invocationId, String status, Project project,
            ObjectModel before, ObjectModel after, ObjectModel candidateAfter, ObjectInstance receiver,
            ResolvedOperation resolved, SlotValue value, Map<UmlParameterId, SlotValue> outValues, long revision,
            List<String> diagnostics, List<OperationContractResultDto> contractResults) {
        return new OperationInvocationResultDto(invocationId, status, reference(project, receiver),
                resolved.requested().id().value(), resolved.operation().id().value(), resolved.operation().name(),
                resolved.owner().id().value(), dto(value), outValues(resolved, outValues), lifecycle(project, before, after),
                before.id().value(), status.equals("SUCCEEDED") ? after.id().value() : null,
                candidateAfter == null ? null : candidateAfter.id().value(), revision, diagnostics, contractResults);
    }

    private List<NamedOperationValueDto> outValues(ResolvedOperation resolved, Map<UmlParameterId, SlotValue> values) {
        List<NamedOperationValueDto> result = new ArrayList<>();
        resolved.operation().parameters().stream().filter(parameter -> values.containsKey(parameter.id()))
                .forEach(parameter -> result.add(new NamedOperationValueDto(parameter.id().value(), parameter.name(),
                        dto(values.get(parameter.id())))));
        return List.copyOf(result);
    }

    private OperationLifecycleDiffDto lifecycle(Project project, ObjectModel before, ObjectModel after) {
        var created = after.objects().stream().filter(object -> before.findObject(object.id()).isEmpty())
                .map(object -> reference(project, object)).toList();
        var deleted = before.objects().stream().filter(object -> after.findObject(object.id()).isEmpty())
                .map(object -> reference(project, object)).toList();
        var changed = after.objects().stream().filter(object -> before.findObject(object.id())
                .filter(previous -> !previous.equals(object)).isPresent()).map(object -> reference(project, object)).toList();
        return new OperationLifecycleDiffDto(created, changed, deleted);
    }

    private NamedElementReferenceDto reference(Project project, ObjectInstance object) {
        String typeName = project.umlModel().findClass(object.classId()).map(type -> type.name()).orElse(object.classId().value());
        return new NamedElementReferenceDto(object.id().value(), object.name(), typeName);
    }

    private boolean sameState(ObjectModel left, ObjectModel right) {
        return left.objects().equals(right.objects()) && left.links().equals(right.links());
    }

    private boolean compatible(UmlType expected, UmlType actual) {
        return expected.equals(actual) || expected.equals(UmlType.REAL) && actual.equals(UmlType.INTEGER);
    }

    private SlotValue slotValue(SlotValueDto dto) {
        return new SlotValue(dto.value(), new UmlType(dto.type()));
    }

    private SlotValueDto dto(SlotValue value) {
        return value == null ? null : new SlotValueDto(value.valueType().name(), value.value());
    }

    private long revision(Project project) {
        return project.metadata().updatedAt().toEpochMilli();
    }

    private ObjectModelException argumentError(String code, UmlParameter parameter, String actualType) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("parameterId", parameter.id().value());
        details.put("parameterName", parameter.name());
        details.put("expectedType", parameter.type().name());
        if (actualType != null) details.put("actualType", actualType);
        return error(code, "Invalid operation argument for parameter '" + parameter.name() + "'.",
                "Das Argument fuer den Parameter '" + parameter.name() + "' ist ungueltig.", details);
    }

    private ObjectModelException error(String code, String message, String userMessage, Map<String, Object> details) {
        return new ObjectModelException(code, message, userMessage, details);
    }
}
