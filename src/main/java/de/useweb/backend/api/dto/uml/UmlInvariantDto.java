package de.useweb.backend.api.dto.uml;

import java.util.List;

import de.useweb.backend.api.dto.ocl.OclExpressionDto;

public record UmlInvariantDto(
        String id,
        String name,
        String contextClassId,
        OclExpressionDto expression,
        boolean enabled,
        List<String> contextVariableNames,
        Boolean existential
) {
    public UmlInvariantDto(String id, String name, String contextClassId, OclExpressionDto expression,
            boolean enabled) {
        this(id, name, contextClassId, expression, enabled, List.of(), false);
    }
}
