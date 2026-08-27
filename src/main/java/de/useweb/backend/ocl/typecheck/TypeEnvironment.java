package de.useweb.backend.ocl.typecheck;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlEnumeration;
import de.useweb.backend.domain.uml.UmlDataType;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.ocl.contract.OperationConstraintKind;
import de.useweb.backend.ocl.definition.OclDefinitionTypeResolver;

public record TypeEnvironment(UmlModel umlModel, UmlClass contextClass, Map<String, OclType> variables,
        OperationConstraintKind contractKind, OclDefinitionTypeResolver definitionTypeResolver) {

    public TypeEnvironment(UmlModel umlModel, UmlClass contextClass) {
        this(umlModel, contextClass, Map.of(), null, null);
    }

    public TypeEnvironment(UmlModel umlModel, UmlClass contextClass, Map<String, OclType> variables) {
        this(umlModel, contextClass, variables, null, null);
    }

    public TypeEnvironment(UmlModel umlModel, UmlClass contextClass, Map<String, OclType> variables,
            OperationConstraintKind contractKind) {
        this(umlModel, contextClass, variables, contractKind, null);
    }

    public TypeEnvironment {
        if (umlModel == null) {
            throw new IllegalArgumentException("umlModel must not be null");
        }
        if (contextClass == null) {
            throw new IllegalArgumentException("contextClass must not be null");
        }
        variables = Map.copyOf(variables == null ? Map.of() : variables);
    }

    public Optional<UmlClass> findClass(UmlClassId classId) {
        return umlModel.findClass(classId);
    }

    public Optional<UmlClass> findClassByName(String className) {
        return umlModel.resolveClass(className, contextClass);
    }

    public Optional<UmlEnumeration> findEnumerationByName(String enumerationName) {
        return umlModel.resolveEnumeration(enumerationName, contextClass);
    }

    public Optional<UmlDataType> findDataTypeByName(String dataTypeName) {
        return umlModel.resolveDataType(dataTypeName, contextClass);
    }

    public OclType classType(UmlClass umlClass) {
        return OclType.classType(umlClass, umlModel);
    }

    public Optional<OclType> findVariable(String name) {
        return Optional.ofNullable(variables.get(name));
    }

    public TypeEnvironment child(Map<String, OclType> bindings) {
        Map<String, OclType> childVariables = new LinkedHashMap<>(variables);
        childVariables.putAll(bindings);
        return new TypeEnvironment(umlModel, contextClass, childVariables, contractKind, definitionTypeResolver);
    }
}
