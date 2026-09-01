package de.useweb.backend.api.dto.modeltext;

import java.util.List;

public record ModelTextSourceProvenanceDto(
        String sourcePath,
        String importedBy,
        List<String> selectedNames,
        int depth,
        String sha256) {
}
