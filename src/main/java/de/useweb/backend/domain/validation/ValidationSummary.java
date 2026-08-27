package de.useweb.backend.domain.validation;

import java.util.Collection;

public record ValidationSummary(int errorCount, int warningCount, int infoCount) {

    public static ValidationSummary from(Collection<ValidationError> findings) {
        int errors = 0;
        int warnings = 0;
        int infos = 0;
        for (ValidationError finding : findings) {
            if (finding.severity() == ValidationSeverity.ERROR) {
                errors++;
            } else if (finding.severity() == ValidationSeverity.WARNING) {
                warnings++;
            } else if (finding.severity() == ValidationSeverity.INFO) {
                infos++;
            }
        }
        return new ValidationSummary(errors, warnings, infos);
    }
}
