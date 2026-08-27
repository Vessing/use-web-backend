package de.useweb.backend.ocl.contract;

import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlOperationId;

public record OperationContextReference(UmlClassId ownerClassId, UmlOperationId operationId) {
    public OperationContextReference {
        if (ownerClassId == null || operationId == null) {
            throw new IllegalArgumentException("Operation context reference must be complete");
        }
    }
}
