package de.useweb.backend.api.dto.ocl;

public record OclTypecheckRequestDto(String expression, String contextClassId, String sourceId, String sourceKind, Long documentVersion) {
    public OclTypecheckRequestDto(String expression, String contextClassId) {
        this(expression, contextClassId, null, null, null);
    }
}
