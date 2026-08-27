package de.useweb.backend.application.ocl;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import de.useweb.backend.api.dto.ocl.OclDiagnosticDto;
import de.useweb.backend.api.dto.ocl.SourceRangeDto;
import de.useweb.backend.api.dto.ocl.SourceReferenceDto;
import de.useweb.backend.ocl.diagnostics.OclDiagnostic;
import de.useweb.backend.ocl.diagnostics.SourceRange;

@Component
public class OclDiagnosticMapper {

    public List<OclDiagnosticDto> toDto(List<OclDiagnostic> diagnostics, String sourceId, String sourceKind, Long documentVersion) {
        return diagnostics.stream().map(diagnostic -> toDto(diagnostic, sourceId, sourceKind, documentVersion)).toList();
    }

    public OclDiagnosticDto toDto(OclDiagnostic diagnostic, String sourceId, String sourceKind, Long documentVersion) {
        SourceRangeDto range = toDto(diagnostic.sourceRange());
        SourceReferenceDto source = sourceId == null && sourceKind == null && documentVersion == null
                ? null
                : new SourceReferenceDto(sourceId, sourceKind, documentVersion, range);
        return new OclDiagnosticDto(
                null,
                "VALIDATION_ERROR",
                diagnostic.phase().name(),
                diagnostic.code(),
                diagnostic.severity(),
                diagnostic.message(),
                diagnostic.message(),
                diagnostic.message(),
                range,
                source,
                diagnostic.expected(),
                diagnostic.actual(),
                List.of(),
                Map.of(),
                null);
    }

    public SourceRangeDto toDto(SourceRange range) {
        if (range == null) {
            return null;
        }
        return new SourceRangeDto(
                range.start().line(), range.start().column(), range.start().offset(),
                range.end().line(), range.end().column(), range.end().offset());
    }
}
