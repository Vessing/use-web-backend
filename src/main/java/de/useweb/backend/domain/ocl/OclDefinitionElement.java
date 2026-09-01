package de.useweb.backend.domain.ocl;

import java.util.List;

import de.useweb.backend.domain.uml.UmlParameter;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.ocl.diagnostics.SourceRange;

public record OclDefinitionElement(
        OclDefinitionElementId id,
        Kind kind,
        OwnerKind ownerKind,
        String ownerId,
        String name,
        UmlType resultType,
        List<UmlParameter> parameters,
        String expression,
        SourceRange sourceRange) {

    public enum Kind { PROPERTY_DEF, OPERATION_DEF }
    public enum OwnerKind { CLASS, PACKAGE }

    public OclDefinitionElement(OclDefinitionElementId id, Kind kind, OwnerKind ownerKind, String ownerId,
            String name, UmlType resultType, List<UmlParameter> parameters, String expression) {
        this(id, kind, ownerKind, ownerId, name, resultType, parameters, expression, null);
    }

    public OclDefinitionElement {
        if (id == null || kind == null || ownerKind == null || resultType == null) {
            throw new IllegalArgumentException("Definition identity, kind, owner and result type are required");
        }
        if (ownerId == null || ownerId.isBlank() || name == null || name.isBlank()
                || expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Definition owner, name and expression must not be blank");
        }
        parameters = List.copyOf(parameters == null ? List.of() : parameters);
        if (kind == Kind.PROPERTY_DEF && !parameters.isEmpty()) {
            throw new IllegalArgumentException("Property definitions must not declare parameters");
        }
    }
}
