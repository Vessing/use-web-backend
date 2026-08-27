package de.useweb.backend.api.dto.uml;

import java.util.List;

public record UmlOperationDto(
        String id,
        String name,
        String returnType,
        List<UmlParameterDto> parameters,
        String bodyExpression,
        String visibility,
        Boolean abstractOperation,
        Boolean query,
        List<UmlOperationContractDto> contracts
) {
    public UmlOperationDto(String id, String name, String returnType, List<UmlParameterDto> parameters,
            String bodyExpression) {
        this(id, name, returnType, parameters, bodyExpression, "PUBLIC", false, false, List.of());
    }
    public UmlOperationDto(String id, String name, String returnType, List<UmlParameterDto> parameters,
            String bodyExpression, String visibility) {
        this(id, name, returnType, parameters, bodyExpression, visibility, false, false, List.of());
    }
    public UmlOperationDto(String id, String name, String returnType, List<UmlParameterDto> parameters) {
        this(id, name, returnType, parameters, null, "PUBLIC", false, false, List.of());
    }
    public UmlOperationDto(String id, String name, String returnType, List<UmlParameterDto> parameters,
            String bodyExpression, String visibility, Boolean abstractOperation, Boolean query) {
        this(id, name, returnType, parameters, bodyExpression, visibility, abstractOperation, query, List.of());
    }
}
