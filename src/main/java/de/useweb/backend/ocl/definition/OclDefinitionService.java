package de.useweb.backend.ocl.definition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.useweb.backend.domain.snapshot.ObjectInstance;
import de.useweb.backend.domain.snapshot.ObjectModel;
import de.useweb.backend.domain.uml.UmlAttributeId;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.ocl.diagnostics.OclDiagnostic;
import de.useweb.backend.ocl.evaluation.EvaluationContext;
import de.useweb.backend.ocl.evaluation.OclEvaluationResult;
import de.useweb.backend.ocl.evaluation.OclEvaluator;
import de.useweb.backend.ocl.typecheck.OclType;
import de.useweb.backend.ocl.typecheck.OclTypeChecker;
import de.useweb.backend.ocl.typecheck.TypeEnvironment;
import de.useweb.backend.ocl.value.ObjectValue;
import de.useweb.backend.ocl.value.OclValue;

/** Runtime for declarative OCL definitions; it owns no mutable UML or snapshot state. */
public final class OclDefinitionService implements OclDefinitionRuntime, OclDefinitionTypeResolver {
    private final UmlModel model;
    private final List<OclDefinition> definitions;
    private final OclTypeChecker typeChecker = new OclTypeChecker();
    private final OclEvaluator evaluator = new OclEvaluator();

    public OclDefinitionService(UmlModel model, List<OclDefinition> definitions) {
        this.model = java.util.Objects.requireNonNull(model, "model");
        this.definitions = List.copyOf(definitions == null ? List.of() : definitions);
        long distinctIds = this.definitions.stream().map(OclDefinition::id).distinct().count();
        if (distinctIds != this.definitions.size()) {
            throw new IllegalArgumentException("OCL definition ids must be unique");
        }
    }

    public OclDefinitionCheckResult check(OclDefinition definition, UmlModel model) {
        UmlClass owner = model.findClass(definition.ownerClassId()).orElseThrow();
        Map<String, OclType> bindings = new LinkedHashMap<>();
        TypeEnvironment base = new TypeEnvironment(model, owner);
        definition.parameters().forEach(parameter ->
                bindings.put(parameter.name(), OclType.fromUmlType(parameter.type(), base)));
        TypeEnvironment environment = new TypeEnvironment(model, owner, bindings, null, this);
        var checked = typeChecker.checkExpression(environment, definition.expression());
        OclType expected = OclType.fromUmlType(definition.resultType(), environment);
        if (!checked.success()) {
            return new OclDefinitionCheckResult(false, checked.resultType(), checked.diagnostics());
        }
        if (!checked.resultType().conformsTo(expected)) {
            String code = switch (definition.kind()) {
                case DERIVE -> "DERIVED_TYPE_MISMATCH";
                case INIT -> "INIT_TYPE_MISMATCH";
                case BODY -> "BODY_TYPE_MISMATCH";
                case PROPERTY_DEF, OPERATION_DEF -> "DEFINITION_TYPE_MISMATCH";
            };
            OclDiagnostic diagnostic = new OclDiagnostic(
                    de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.TYPECHECK, code, "ERROR",
                    "Definition result type '" + checked.resultType().displayName()
                            + "' does not conform to '" + expected.displayName() + "'.",
                    definition.expression().sourceRange(), List.of(expected.displayName()),
                    checked.resultType().displayName());
            return new OclDefinitionCheckResult(false, checked.resultType(), List.of(diagnostic));
        }
        return new OclDefinitionCheckResult(true, checked.resultType(), List.of());
    }

    @Override
    public Optional<OclType> propertyType(de.useweb.backend.domain.uml.UmlClassId receiverClassId, String name) {
        return propertySignatures(receiverClassId, name).stream().findFirst()
                .map(OclDefinitionSignature::resultType);
    }

    @Override
    public Optional<OclType> operationType(de.useweb.backend.domain.uml.UmlClassId receiverClassId, String name,
            List<OclType> argumentTypes) {
        return operationSignatures(receiverClassId, name, argumentTypes.size()).stream()
                .filter(signature -> {
                    for (int index = 0; index < argumentTypes.size(); index++) {
                        if (!argumentTypes.get(index).conformsTo(signature.parameterTypes().get(index))) return false;
                    }
                    return true;
                }).findFirst().map(OclDefinitionSignature::resultType);
    }

    @Override
    public List<OclDefinitionSignature> propertySignatures(
            de.useweb.backend.domain.uml.UmlClassId receiverClassId, String name) {
        return definitions.stream()
                .filter(definition -> definition.kind() == OclDefinitionKind.PROPERTY_DEF)
                .filter(definition -> definition.featureName().equals(name))
                .filter(definition -> model.typeConformanceOrder(receiverClassId).contains(definition.ownerClassId()))
                .map(this::signature).toList();
    }

    @Override
    public List<OclDefinitionSignature> operationSignatures(
            de.useweb.backend.domain.uml.UmlClassId receiverClassId, String name, int argumentCount) {
        return definitions.stream()
                .filter(definition -> definition.kind() == OclDefinitionKind.OPERATION_DEF)
                .filter(definition -> definition.featureName().equals(name))
                .filter(definition -> definition.parameters().size() == argumentCount)
                .filter(definition -> model.typeConformanceOrder(receiverClassId).contains(definition.ownerClassId()))
                .map(this::signature).toList();
    }

    public OclEvaluationResult evaluate(OclDefinition definition, UmlModel model, ObjectModel snapshot,
            ObjectInstance self, Map<String, OclValue> bindings) {
        OclDefinitionCheckResult checked = check(definition, model);
        if (!checked.success()) return OclEvaluationResult.failure(checked.diagnostics());
        EvaluationContext context = new EvaluationContext(model, snapshot, self,
                bindings == null ? Map.of() : bindings, snapshot, this);
        try {
            return evaluateNested(definition, context, bindings == null ? Map.of() : bindings);
        } catch (OclDefinitionEvaluationException exception) {
            return OclEvaluationResult.failure(List.of(OclDiagnostic.evaluationError(
                    exception.code(), exception.getMessage(), definition.expression().sourceRange())));
        }
    }

    public Map<UmlAttributeId, OclValue> initialValues(UmlModel model, ObjectModel creationSnapshot,
            ObjectInstance draft) {
        Map<UmlAttributeId, OclValue> values = new LinkedHashMap<>();
        applicable(draft, model, OclDefinitionKind.INIT).forEach(definition -> {
            OclEvaluationResult evaluated = evaluate(definition, model, creationSnapshot, draft, Map.of());
            if (!evaluated.success()) {
                OclDiagnostic diagnostic = evaluated.diagnostics().getFirst();
                throw new OclDefinitionEvaluationException(diagnostic.code(), diagnostic.message());
            }
            values.put(definition.attributeId(), evaluated.value());
        });
        return Map.copyOf(values);
    }

    @Override
    public Optional<OclValue> property(ObjectValue receiver, String propertyName, EvaluationContext context) {
        return definitions.stream()
                .filter(definition -> definition.kind() == OclDefinitionKind.DERIVE
                        || definition.kind() == OclDefinitionKind.PROPERTY_DEF)
                .filter(definition -> definition.featureName().equals(propertyName))
                .filter(definition -> conforms(receiver.object(), definition, context.umlModel()))
                .findFirst()
                .map(definition -> requiredValue(evaluateNested(definition,
                        context.forReceiver(receiver.object(), Map.of(), definitionKey(definition, receiver.object())), Map.of())));
    }

    @Override
    public Optional<OclValue> property(OclDefinitionId definitionId, ObjectValue receiver,
            EvaluationContext context) {
        return definitions.stream().filter(definition -> definition.id().equals(definitionId))
                .filter(definition -> definition.kind() == OclDefinitionKind.DERIVE
                        || definition.kind() == OclDefinitionKind.PROPERTY_DEF)
                .filter(definition -> conforms(receiver.object(), definition, context.umlModel()))
                .findFirst().map(definition -> requiredValue(evaluateNested(definition,
                        context.forReceiver(receiver.object(), Map.of(), definitionKey(definition, receiver.object())),
                        Map.of())));
    }

    @Override
    public Optional<OclValue> operation(ObjectValue receiver, String operationName, List<OclValue> arguments,
            EvaluationContext context) {
        return definitions.stream()
                .filter(definition -> definition.kind() == OclDefinitionKind.BODY
                        || definition.kind() == OclDefinitionKind.OPERATION_DEF)
                .filter(definition -> definition.featureName().equals(operationName))
                .filter(definition -> definition.parameters().size() == arguments.size())
                .filter(definition -> conforms(receiver.object(), definition, context.umlModel()))
                .findFirst()
                .map(definition -> {
                    Map<String, OclValue> bindings = new LinkedHashMap<>();
                    for (int index = 0; index < arguments.size(); index++) {
                        bindings.put(definition.parameters().get(index).name(), arguments.get(index));
                    }
                    return requiredValue(evaluateNested(definition,
                            context.forReceiver(receiver.object(), bindings, definitionKey(definition, receiver.object())), bindings));
                });
    }

    @Override
    public Optional<OclValue> operation(OclDefinitionId definitionId, ObjectValue receiver,
            List<OclValue> arguments, EvaluationContext context) {
        return definitions.stream().filter(definition -> definition.id().equals(definitionId))
                .filter(definition -> definition.kind() == OclDefinitionKind.BODY
                        || definition.kind() == OclDefinitionKind.OPERATION_DEF)
                .filter(definition -> definition.parameters().size() == arguments.size())
                .filter(definition -> conforms(receiver.object(), definition, context.umlModel()))
                .findFirst().map(definition -> evaluateOperation(definition, receiver, arguments, context));
    }

    @Override
    public Optional<OclValue> operationForFeature(String operationId, ObjectValue receiver,
            List<OclValue> arguments, EvaluationContext context) {
        return definitions.stream()
                .filter(definition -> definition.operationId() != null
                        && definition.operationId().value().equals(operationId))
                .filter(definition -> definition.kind() == OclDefinitionKind.BODY)
                .filter(definition -> definition.parameters().size() == arguments.size())
                .filter(definition -> conforms(receiver.object(), definition, context.umlModel()))
                .findFirst().map(definition -> evaluateOperation(definition, receiver, arguments, context));
    }

    private OclValue evaluateOperation(OclDefinition definition, ObjectValue receiver, List<OclValue> arguments,
            EvaluationContext context) {
        Map<String, OclValue> bindings = new LinkedHashMap<>();
        for (int index = 0; index < arguments.size(); index++) {
            bindings.put(definition.parameters().get(index).name(), arguments.get(index));
        }
        return requiredValue(evaluateNested(definition,
                context.forReceiver(receiver.object(), bindings, definitionKey(definition, receiver.object())), bindings));
    }

    private OclDefinitionSignature signature(OclDefinition definition) {
        TypeEnvironment ownerEnvironment = new TypeEnvironment(model,
                model.findClass(definition.ownerClassId()).orElseThrow());
        List<OclType> parameters = definition.parameters().stream()
                .map(parameter -> OclType.fromUmlType(parameter.type(), ownerEnvironment)).toList();
        return new OclDefinitionSignature(definition.id(), definition.kind(), definition.ownerClassId(),
                definition.featureName(), parameters, OclType.fromUmlType(definition.resultType(), ownerEnvironment));
    }

    private OclEvaluationResult evaluateNested(OclDefinition definition, EvaluationContext context,
            Map<String, OclValue> bindings) {
        String key = definitionKey(definition, context.self());
        boolean cacheable = bindings.isEmpty()
                && (definition.kind() == OclDefinitionKind.DERIVE
                        || definition.kind() == OclDefinitionKind.PROPERTY_DEF);
        if (cacheable) {
            Optional<OclValue> cached = context.definitionTrace().cached(key);
            if (cached.isPresent()) return OclEvaluationResult.ok(cached.get());
        }
        EvaluationContext scoped = context.definitionStack().contains(key)
                ? context : context.forReceiver(context.self(), bindings, key);
        OclEvaluationResult result = evaluator.evaluate(definition.expression(), scoped);
        if (cacheable && result.success()) context.definitionTrace().cache(key, result.value());
        return result;
    }

    public Map<String, java.util.Set<String>> dependencyGraph(EvaluationContext context) {
        return context.definitionTrace().dependencyGraph();
    }

    private String definitionKey(OclDefinition definition, ObjectInstance receiver) {
        return definition.id().value() + "@" + receiver.id().value();
    }

    private OclValue requiredValue(OclEvaluationResult result) {
        if (!result.success()) {
            OclDiagnostic diagnostic = result.diagnostics().getFirst();
            throw new OclDefinitionEvaluationException(diagnostic.code(), diagnostic.message());
        }
        return result.value();
    }

    private List<OclDefinition> applicable(ObjectInstance object, UmlModel model, OclDefinitionKind kind) {
        return definitions.stream().filter(definition -> definition.kind() == kind)
                .filter(definition -> conforms(object, definition, model)).toList();
    }

    private boolean conforms(ObjectInstance object, OclDefinition definition, UmlModel model) {
        return model.typeConformanceOrder(object.classId()).contains(definition.ownerClassId());
    }
}
