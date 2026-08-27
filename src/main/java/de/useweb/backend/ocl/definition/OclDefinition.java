package de.useweb.backend.ocl.definition;

import java.util.List;

import de.useweb.backend.domain.uml.UmlAttributeId;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlOperationId;
import de.useweb.backend.domain.uml.UmlParameter;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.ocl.ast.OclAstNode;

public record OclDefinition(
        OclDefinitionId id,
        OclDefinitionKind kind,
        UmlClassId ownerClassId,
        String featureName,
        UmlAttributeId attributeId,
        UmlOperationId operationId,
        UmlType resultType,
        List<UmlParameter> parameters,
        String expressionText,
        OclAstNode expression) {

    public OclDefinition {
        if (id == null || kind == null || ownerClassId == null || resultType == null || expression == null) {
            throw new IllegalArgumentException("Definition identity, context, type and expression are required");
        }
        if (featureName == null || featureName.isBlank() || expressionText == null || expressionText.isBlank()) {
            throw new IllegalArgumentException("Definition feature and expression must not be blank");
        }
        parameters = List.copyOf(parameters == null ? List.of() : parameters);
        if ((kind == OclDefinitionKind.DERIVE || kind == OclDefinitionKind.INIT) && attributeId == null) {
            throw new IllegalArgumentException("Attribute definition requires attributeId");
        }
        if (kind == OclDefinitionKind.BODY && operationId == null) {
            throw new IllegalArgumentException("Body definition requires operationId");
        }
    }
}
