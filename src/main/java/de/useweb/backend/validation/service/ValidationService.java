package de.useweb.backend.validation.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import de.useweb.backend.domain.project.Project;
import de.useweb.backend.domain.validation.ValidationError;
import de.useweb.backend.domain.validation.ValidationResult;
import de.useweb.backend.domain.validation.ValidationResultId;
import de.useweb.backend.validation.result.ValidationErrorFactory;
import de.useweb.backend.validation.rules.LinkValidator;
import de.useweb.backend.validation.rules.MultiplicityValidator;
import de.useweb.backend.validation.rules.OclInvariantValidator;
import de.useweb.backend.validation.rules.SnapshotValidator;
import de.useweb.backend.validation.rules.UmlStructureValidator;

@Service
public class ValidationService {

    private final UmlStructureValidator umlStructureValidator;
    private final SnapshotValidator snapshotValidator;
    private final LinkValidator linkValidator;
    private final MultiplicityValidator multiplicityValidator;
    private final OclInvariantValidator oclInvariantValidator;

    public ValidationService() {
        this(
                new UmlStructureValidator(),
                new SnapshotValidator(),
                new LinkValidator(),
                new MultiplicityValidator(),
                new OclInvariantValidator());
    }

    public ValidationService(
            UmlStructureValidator umlStructureValidator,
            SnapshotValidator snapshotValidator,
            LinkValidator linkValidator,
            MultiplicityValidator multiplicityValidator,
            OclInvariantValidator oclInvariantValidator) {
        this.umlStructureValidator = umlStructureValidator;
        this.snapshotValidator = snapshotValidator;
        this.linkValidator = linkValidator;
        this.multiplicityValidator = multiplicityValidator;
        this.oclInvariantValidator = oclInvariantValidator;
    }

    public ValidationResult validate(Project project) {
        ValidationErrorFactory errorFactory = new ValidationErrorFactory();
        List<ValidationError> findings = new ArrayList<>();
        findings.addAll(umlStructureValidator.validate(project.umlModel(), errorFactory));
        findings.addAll(snapshotValidator.validate(project.umlModel(), project.objectModel(), errorFactory));
        findings.addAll(linkValidator.validate(project.umlModel(), project.objectModel(), errorFactory));
        findings.addAll(multiplicityValidator.validate(project.umlModel(), project.objectModel(), errorFactory));
        findings.addAll(oclInvariantValidator.validate(project, errorFactory));

        ValidationResultId resultId = new ValidationResultId("validation-" + project.id().value());
        return findings.isEmpty()
                ? ValidationResult.valid(resultId, project.id(), project.objectModel().id())
                : ValidationResult.invalid(resultId, project.id(), project.objectModel().id(), findings);
    }
}
