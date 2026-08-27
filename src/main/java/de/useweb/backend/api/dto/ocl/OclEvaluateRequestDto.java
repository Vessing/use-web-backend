package de.useweb.backend.api.dto.ocl;

public record OclEvaluateRequestDto(String expression, String contextObjectId, String sourceId, String sourceKind, Long documentVersion) {
    public OclEvaluateRequestDto(String expression, String contextObjectId) {
        this(expression, contextObjectId, null, null, null);
    }
}
