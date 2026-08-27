package de.useweb.backend.domain.validation;

import java.time.Instant;
import java.util.List;

import de.useweb.backend.domain.project.ProjectId;
import de.useweb.backend.domain.snapshot.ObjectModelId;

public record ValidationResult(
        ValidationResultId id,
        ProjectId projectId,
        ObjectModelId objectModelId,
        ValidationStatus status,
        Instant checkedAt,
        List<ValidationError> findings,
        ValidationSummary summary) {

    public ValidationResult {
        if (id == null) {
            throw new IllegalArgumentException("ValidationResult id must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("ValidationResult status must not be null");
        }
        checkedAt = checkedAt == null ? Instant.now() : checkedAt;
        findings = List.copyOf(findings == null ? List.of() : findings);
        summary = summary == null ? ValidationSummary.from(findings) : summary;
    }

    public static ValidationResult valid(
            ValidationResultId id,
            ProjectId projectId,
            ObjectModelId objectModelId) {
        return new ValidationResult(
                id,
                projectId,
                objectModelId,
                ValidationStatus.VALID,
                Instant.now(),
                List.of(),
                new ValidationSummary(0, 0, 0));
    }

    public static ValidationResult invalid(
            ValidationResultId id,
            ProjectId projectId,
            ObjectModelId objectModelId,
            List<ValidationError> findings) {
        return new ValidationResult(
                id,
                projectId,
                objectModelId,
                ValidationStatus.INVALID,
                Instant.now(),
                findings,
                ValidationSummary.from(findings));
    }
}
