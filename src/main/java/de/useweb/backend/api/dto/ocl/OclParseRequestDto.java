package de.useweb.backend.api.dto.ocl;

public record OclParseRequestDto(String expression, String sourceId, String sourceKind, Long documentVersion) {
    public OclParseRequestDto(String expression) {
        this(expression, null, null, null);
    }
}
