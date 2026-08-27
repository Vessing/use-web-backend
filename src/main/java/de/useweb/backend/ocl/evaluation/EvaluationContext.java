package de.useweb.backend.ocl.evaluation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.stream.Collectors;

import de.useweb.backend.domain.snapshot.ObjectInstance;
import de.useweb.backend.domain.snapshot.ObjectModel;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.ocl.value.OclValue;
import de.useweb.backend.ocl.definition.OclDefinitionRuntime;
import de.useweb.backend.ocl.definition.OclDefinitionEvaluationTrace;

public record EvaluationContext(
        UmlModel umlModel,
        ObjectModel objectModel,
        ObjectInstance self,
        Map<String, OclValue> variables,
        Map<UmlClassId, List<ObjectInstance>> objectsByClassId,
        ObjectModel preState,
        OclDefinitionRuntime definitionRuntime,
        List<String> definitionStack,
        OclDefinitionEvaluationTrace definitionTrace,
        Map<String, String> declaredVariableTypes,
        OclValue implicitSource) {

    public EvaluationContext(UmlModel umlModel, ObjectModel objectModel, ObjectInstance self,
            Map<String, OclValue> variables, Map<UmlClassId, List<ObjectInstance>> objectsByClassId,
            ObjectModel preState, OclDefinitionRuntime definitionRuntime, List<String> definitionStack) {
        this(umlModel, objectModel, self, variables, objectsByClassId, preState, definitionRuntime,
                definitionStack, new OclDefinitionEvaluationTrace(), Map.of(), null);
    }

    public EvaluationContext(UmlModel umlModel, ObjectModel objectModel, ObjectInstance self) {
        this(umlModel, objectModel, self, Map.of(), index(objectModel), objectModel, null, List.of(),
                new OclDefinitionEvaluationTrace(), Map.of(), null);
    }

    public EvaluationContext(UmlModel umlModel, ObjectModel objectModel, ObjectInstance self,
            Map<String, OclValue> variables) {
        this(umlModel, objectModel, self, variables, index(objectModel), objectModel, null, List.of(),
                new OclDefinitionEvaluationTrace(), Map.of(), null);
    }

    public EvaluationContext(UmlModel umlModel, ObjectModel objectModel, ObjectInstance self,
            Map<String, OclValue> variables, ObjectModel preState) {
        this(umlModel, objectModel, self, variables, index(objectModel), preState, null, List.of(),
                new OclDefinitionEvaluationTrace(), Map.of(), null);
    }

    public EvaluationContext(UmlModel umlModel, ObjectModel objectModel, ObjectInstance self,
            Map<String, OclValue> variables, ObjectModel preState, OclDefinitionRuntime definitionRuntime) {
        this(umlModel, objectModel, self, variables, index(objectModel), preState, definitionRuntime, List.of(),
                new OclDefinitionEvaluationTrace(), Map.of(), null);
    }

    public EvaluationContext {
        if (umlModel == null) {
            throw new IllegalArgumentException("umlModel must not be null");
        }
        if (objectModel == null) {
            throw new IllegalArgumentException("objectModel must not be null");
        }
        if (self == null) {
            throw new IllegalArgumentException("self must not be null");
        }
        variables = Map.copyOf(variables == null ? Map.of() : variables);
        objectsByClassId = Map.copyOf(objectsByClassId == null ? index(objectModel) : objectsByClassId);
        preState = preState == null ? objectModel : preState;
        definitionStack = List.copyOf(definitionStack == null ? List.of() : definitionStack);
        definitionTrace = definitionTrace == null ? new OclDefinitionEvaluationTrace() : definitionTrace;
        declaredVariableTypes = Map.copyOf(declaredVariableTypes == null ? Map.of() : declaredVariableTypes);
    }

    public Optional<OclValue> findVariable(String name) {
        return Optional.ofNullable(variables.get(name));
    }

    public EvaluationContext child(Map<String, OclValue> bindings) {
        Map<String, OclValue> childVariables = new LinkedHashMap<>(variables);
        childVariables.putAll(bindings);
        return new EvaluationContext(umlModel, objectModel, self, childVariables, objectsByClassId, preState,
                definitionRuntime, definitionStack, definitionTrace, declaredVariableTypes, implicitSource);
    }

    public EvaluationContext child(Map<String, OclValue> bindings, Map<String, String> declaredTypes) {
        Map<String, OclValue> childVariables = new LinkedHashMap<>(variables);
        childVariables.putAll(bindings);
        Map<String, String> childTypes = new LinkedHashMap<>(declaredVariableTypes);
        childTypes.putAll(declaredTypes);
        return new EvaluationContext(umlModel, objectModel, self, childVariables, objectsByClassId, preState,
                definitionRuntime, definitionStack, definitionTrace, childTypes, implicitSource);
    }

    public Optional<String> findDeclaredVariableType(String name) {
        return Optional.ofNullable(declaredVariableTypes.get(name));
    }

    public Optional<OclValue> implicitSourceValue() {
        return Optional.ofNullable(implicitSource);
    }

    public EvaluationContext withImplicitSource(OclValue value) {
        return new EvaluationContext(umlModel, objectModel, self, variables, objectsByClassId, preState,
                definitionRuntime, definitionStack, definitionTrace, declaredVariableTypes, value);
    }

    public EvaluationContext atPre() {
        ObjectInstance preSelf = preState.findObject(self.id())
                .orElseThrow(() -> new IllegalStateException("Receiver does not exist in pre-state"));
        return new EvaluationContext(umlModel, preState, preSelf, variables, index(preState), preState,
                definitionRuntime, definitionStack, definitionTrace, declaredVariableTypes, implicitSource);
    }

    public EvaluationContext forReceiver(ObjectInstance receiver, Map<String, OclValue> bindings, String definitionKey) {
        if (definitionStack.contains(definitionKey)) {
            throw new de.useweb.backend.ocl.definition.OclDefinitionEvaluationException(
                    "DERIVATION_CYCLE", "Cyclic OCL definition: " + definitionKey);
        }
        if (definitionStack.size() >= de.useweb.backend.ocl.profile.OclComplianceProfile.MAX_DEFINITION_RECURSION) {
            throw new de.useweb.backend.ocl.definition.OclDefinitionEvaluationException(
                    "DEFINITION_RECURSION_LIMIT", "OCL definition recursion limit exceeded.");
        }
        Map<String, OclValue> scoped = new LinkedHashMap<>(bindings == null ? Map.of() : bindings);
        List<String> stack = new java.util.ArrayList<>(definitionStack);
        definitionTrace.dependsOn(stack.isEmpty() ? null : stack.getLast(), definitionKey);
        stack.add(definitionKey);
        return new EvaluationContext(umlModel, objectModel, receiver, scoped, objectsByClassId, preState,
                definitionRuntime, stack, definitionTrace, Map.of(), null);
    }

    public List<ObjectInstance> objectsOfClass(UmlClassId classId) {
        return objectsByClassId.getOrDefault(classId, List.of());
    }

    public List<ObjectInstance> objectsOfType(UmlClassId classId) {
        return umlModel.concreteSubtypesOf(classId).stream()
                .flatMap(umlClass -> objectsOfClass(umlClass.id()).stream())
                .toList();
    }

    private static Map<UmlClassId, List<ObjectInstance>> index(ObjectModel objectModel) {
        if (objectModel == null) {
            return Map.of();
        }
        return objectModel.objects().stream().collect(Collectors.groupingBy(
                ObjectInstance::classId,
                LinkedHashMap::new,
                Collectors.collectingAndThen(Collectors.toList(), List::copyOf)));
    }
}
