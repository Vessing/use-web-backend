package de.useweb.backend.ocl.resolution;

import de.useweb.backend.ocl.typecheck.OclType;

public record OclCallResolution(OclCallKind kind, OclType resultType, String featureId,
        String qualifiedName) {

    public OclCallResolution {
        if (kind == null || resultType == null || resultType.isInvalid()) {
            throw new IllegalArgumentException("A resolved call requires a kind and valid result type");
        }
        featureId = featureId == null ? "" : featureId;
        qualifiedName = qualifiedName == null ? "" : qualifiedName;
    }
}
