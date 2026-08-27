package de.useweb.backend.ocl.definition;

import java.util.List;

import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.ocl.typecheck.OclType;

public record OclDefinitionSignature(OclDefinitionId id, OclDefinitionKind kind,
        UmlClassId ownerClassId, String name, List<OclType> parameterTypes, OclType resultType) {
    public OclDefinitionSignature {
        parameterTypes = List.copyOf(parameterTypes == null ? List.of() : parameterTypes);
    }
}
