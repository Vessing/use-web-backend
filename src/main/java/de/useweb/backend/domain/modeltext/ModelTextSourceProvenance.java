package de.useweb.backend.domain.modeltext;

import java.util.List;

public record ModelTextSourceProvenance(
        String sourcePath,
        String importedBy,
        List<String> selectedNames,
        int depth,
        String sha256) {

    public ModelTextSourceProvenance {
        if (sourcePath == null || sourcePath.isBlank()) {
            throw new IllegalArgumentException("sourcePath must not be blank");
        }
        importedBy = importedBy == null || importedBy.isBlank() ? null : importedBy;
        selectedNames = List.copyOf(selectedNames == null ? List.of() : selectedNames);
        if (depth < 0) {
            throw new IllegalArgumentException("depth must not be negative");
        }
        if (sha256 == null || sha256.isBlank()) {
            throw new IllegalArgumentException("sha256 must not be blank");
        }
    }
}
