package de.useweb.backend.api.dto.uml;

import de.useweb.backend.api.dto.ocl.OclExpressionDto;

public record UmlInvariantDto(
        String id,
        String name,
        String contextClassId,
        OclExpressionDto expression,
        boolean enabled
) {
}
