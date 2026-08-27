package de.useweb.backend.ocl.definition;

import java.util.List;
import java.util.Optional;

import de.useweb.backend.ocl.diagnostics.OclDiagnostic;

public record OclDefinitionParseResult(OclDefinition definition, List<OclDiagnostic> diagnostics) {
    public OclDefinitionParseResult {
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }

    public boolean success() {
        return definition != null && diagnostics.isEmpty();
    }

    public Optional<OclDefinition> optionalDefinition() {
        return Optional.ofNullable(definition);
    }
}
