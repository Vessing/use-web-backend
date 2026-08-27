package de.useweb.backend.ocl.definition;

import java.util.List;
import java.util.Optional;

import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.ocl.typecheck.OclType;

public interface OclDefinitionTypeResolver {
    Optional<OclType> propertyType(UmlClassId receiverClassId, String name);

    Optional<OclType> operationType(UmlClassId receiverClassId, String name, List<OclType> argumentTypes);

    default List<OclDefinitionSignature> propertySignatures(UmlClassId receiverClassId, String name) {
        return List.of();
    }

    default List<OclDefinitionSignature> operationSignatures(UmlClassId receiverClassId, String name,
            int argumentCount) {
        return List.of();
    }
}
