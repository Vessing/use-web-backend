package de.useweb.backend.validation.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.useweb.backend.domain.project.Project;
import de.useweb.backend.domain.snapshot.ObjectInstance;
import de.useweb.backend.domain.uml.UmlInvariant;
import de.useweb.backend.domain.validation.ElementTarget;
import de.useweb.backend.domain.validation.ElementType;
import de.useweb.backend.domain.validation.ValidationError;
import de.useweb.backend.domain.validation.ValidationErrorCode;
import de.useweb.backend.ocl.diagnostics.OclDiagnostic;
import de.useweb.backend.ocl.evaluation.EvaluationContext;
import de.useweb.backend.ocl.evaluation.OclEvaluator;
import de.useweb.backend.ocl.parser.OclParser;
import de.useweb.backend.ocl.typecheck.OclTypeChecker;
import de.useweb.backend.ocl.value.BooleanValue;
import de.useweb.backend.ocl.value.OclInvalidValue;
import de.useweb.backend.ocl.value.OclVoidValue;
import de.useweb.backend.ocl.value.OclValue;
import de.useweb.backend.ocl.value.ObjectValue;
import de.useweb.backend.validation.result.ValidationErrorFactory;

public class OclInvariantValidator {

    private final OclParser parser;
    private final OclTypeChecker typeChecker;
    private final OclEvaluator evaluator;

    public OclInvariantValidator() {
        this(new OclParser(), new OclTypeChecker(), new OclEvaluator());
    }

    public OclInvariantValidator(OclParser parser, OclTypeChecker typeChecker, OclEvaluator evaluator) {
        this.parser = parser;
        this.typeChecker = typeChecker;
        this.evaluator = evaluator;
    }

    public List<ValidationError> validate(Project project, ValidationErrorFactory errorFactory) {
        List<ValidationError> errors = new ArrayList<>();
        for (UmlInvariant invariant : project.umlModel().invariants()) {
            if (!invariant.enabled()) {
                continue;
            }
            var parseResult = parser.parse(invariant.expression().text());
            if (!parseResult.success()) {
                errors.addAll(parseResult.diagnostics().stream()
                        .map(diagnostic -> diagnosticError(errorFactory, invariant, diagnostic, ValidationErrorCode.SYNTAX_ERROR))
                        .toList());
                continue;
            }
            var typecheckResult = typeChecker.checkInvariant(project.umlModel(), invariant.contextClassId(),
                    parseResult.ast(), invariant.contextVariableNames());
            if (!typecheckResult.success()) {
                errors.addAll(typecheckResult.diagnostics().stream()
                        .map(diagnostic -> diagnosticError(errorFactory, invariant, diagnostic, codeOf(diagnostic)))
                        .toList());
                continue;
            }
            List<ObjectInstance> contextObjects = project.objectModel().objects().stream()
                    .filter(object -> project.umlModel().isSubtypeOf(object.classId(), invariant.contextClassId()))
                    .toList();
            List<InvariantEvaluationContext> evaluationContexts = evaluationContexts(
                    contextObjects, invariant.contextVariableNames());
            boolean existentialSatisfied = false;
            for (InvariantEvaluationContext context : evaluationContexts) {
                ObjectInstance contextObject = context.self();
                var evaluationResult = evaluator.evaluate(
                        parseResult.ast(),
                        new EvaluationContext(project.umlModel(), project.objectModel(), contextObject, context.variables()));
                if (!evaluationResult.success()) {
                    errors.addAll(evaluationResult.diagnostics().stream()
                            .map(diagnostic -> evaluationError(errorFactory, invariant, contextObject, diagnostic))
                            .toList());
                } else if (evaluationResult.value() instanceof OclInvalidValue || evaluationResult.value() instanceof OclVoidValue) {
                    errors.add(undefinedInvariantResult(errorFactory, invariant, contextObject, evaluationResult.value().valueKind()));
                } else if (evaluationResult.value() instanceof BooleanValue booleanValue) {
                    if (invariant.existential() && booleanValue.value()) {
                        existentialSatisfied = true;
                        break;
                    }
                    if (!invariant.existential() && !booleanValue.value()) {
                        errors.add(invariantViolation(errorFactory, invariant, contextObject));
                    }
                }
            }
            if (invariant.existential() && !existentialSatisfied) {
                errors.add(existentialInvariantViolation(errorFactory, invariant));
            }
        }
        return errors;
    }

    private List<InvariantEvaluationContext> evaluationContexts(List<ObjectInstance> objects,
            List<String> variableNames) {
        if (variableNames == null || variableNames.isEmpty()) {
            return objects.stream().map(object -> new InvariantEvaluationContext(object, Map.of())).toList();
        }
        List<InvariantEvaluationContext> result = new ArrayList<>();
        buildEvaluationContexts(objects, variableNames, 0, new LinkedHashMap<>(), result);
        return result;
    }

    private void buildEvaluationContexts(List<ObjectInstance> objects, List<String> variableNames, int index,
            Map<String, OclValue> bindings, List<InvariantEvaluationContext> result) {
        if (index == variableNames.size()) {
            ObjectInstance self = ((ObjectValue) bindings.get(variableNames.getFirst())).object();
            result.add(new InvariantEvaluationContext(self, Map.copyOf(bindings)));
            return;
        }
        for (ObjectInstance object : objects) {
            bindings.put(variableNames.get(index), new ObjectValue(object));
            buildEvaluationContexts(objects, variableNames, index + 1, bindings, result);
        }
        bindings.remove(variableNames.get(index));
    }

    private ValidationError existentialInvariantViolation(ValidationErrorFactory errorFactory,
            UmlInvariant invariant) {
        return errorFactory.error(
                ValidationErrorCode.INVARIANT_VIOLATION,
                "Existential invariant '" + invariant.name() + "' has no satisfying context tuple.",
                List.of(ElementTarget.invariant(invariant.id().value()),
                        new ElementTarget(ElementType.CLASS, invariant.contextClassId().value(), null)),
                Map.of("phase", "OCL_EVALUATION", "contextClassId", invariant.contextClassId().value(),
                        "invariantId", invariant.id().value(), "invariantName", invariant.name(),
                        "expression", invariant.expression().text(), "existential", true));
    }

    private record InvariantEvaluationContext(ObjectInstance self, Map<String, OclValue> variables) {
    }

    private ValidationError undefinedInvariantResult(
            ValidationErrorFactory errorFactory,
            UmlInvariant invariant,
            ObjectInstance contextObject,
            String valueKind) {
        return errorFactory.error(
                ValidationErrorCode.EVALUATION_ERROR,
                "Invariant '" + invariant.name() + "' evaluated to " + valueKind.toLowerCase() + " for object '" + contextObject.name() + "'.",
                List.of(
                        ElementTarget.object(contextObject.id().value()),
                        ElementTarget.invariant(invariant.id().value()),
                        new ElementTarget(ElementType.OCL_EXPRESSION, invariant.id().value(), null)),
                Map.of(
                        "phase", "EVALUATION",
                        "diagnosticCode", "UNDEFINED_INVARIANT_RESULT",
                        "contextClassId", invariant.contextClassId().value(),
                        "contextObjectId", contextObject.id().value(),
                        "contextObjectName", contextObject.name(),
                        "invariantId", invariant.id().value(),
                        "invariantName", invariant.name(),
                        "expression", invariant.expression().text(),
                        "valueKind", valueKind));
    }

    private ValidationError invariantViolation(
            ValidationErrorFactory errorFactory,
            UmlInvariant invariant,
            ObjectInstance contextObject) {
        return errorFactory.error(
                ValidationErrorCode.INVARIANT_VIOLATION,
                "Invariant '" + invariant.name() + "' evaluated to false for object '" + contextObject.name() + "'.",
                List.of(
                        ElementTarget.object(contextObject.id().value()),
                        ElementTarget.invariant(invariant.id().value()),
                        new ElementTarget(ElementType.CLASS, invariant.contextClassId().value(), null)),
                Map.of(
                        "phase", "OCL_EVALUATION",
                        "contextClassId", invariant.contextClassId().value(),
                        "contextObjectId", contextObject.id().value(),
                        "contextObjectName", contextObject.name(),
                        "invariantId", invariant.id().value(),
                        "invariantName", invariant.name(),
                        "expression", invariant.expression().text(),
                        "actualValue", false));
    }

    private ValidationError evaluationError(
            ValidationErrorFactory errorFactory,
            UmlInvariant invariant,
            ObjectInstance contextObject,
            OclDiagnostic diagnostic) {
        return errorFactory.error(
                ValidationErrorCode.EVALUATION_ERROR,
                diagnostic.message(),
                List.of(
                        ElementTarget.object(contextObject.id().value()),
                        ElementTarget.invariant(invariant.id().value()),
                        new ElementTarget(ElementType.OCL_EXPRESSION, invariant.id().value(), null)),
                diagnosticDetails(invariant, diagnostic, contextObject.id().value()));
    }

    private ValidationError diagnosticError(
            ValidationErrorFactory errorFactory,
            UmlInvariant invariant,
            OclDiagnostic diagnostic,
            ValidationErrorCode code) {
        return errorFactory.error(
                code,
                diagnostic.message(),
                List.of(
                        ElementTarget.invariant(invariant.id().value()),
                        new ElementTarget(ElementType.OCL_EXPRESSION, invariant.id().value(), null),
                        new ElementTarget(ElementType.CLASS, invariant.contextClassId().value(), null)),
                diagnosticDetails(invariant, diagnostic, null));
    }

    private Map<String, Object> diagnosticDetails(UmlInvariant invariant, OclDiagnostic diagnostic, String contextObjectId) {
        var builder = new java.util.LinkedHashMap<String, Object>();
        builder.put("phase", diagnostic.phase().name());
        builder.put("diagnosticCode", diagnostic.code());
        builder.put("sourceId", invariant.id().value());
        builder.put("sourceKind", "INVARIANT_EXPRESSION");
        builder.put("invariantId", invariant.id().value());
        builder.put("invariantName", invariant.name());
        builder.put("contextClassId", invariant.contextClassId().value());
        builder.put("expression", invariant.expression().text());
        if (contextObjectId != null) {
            builder.put("contextObjectId", contextObjectId);
        }
        if (diagnostic.sourceRange() != null) {
            builder.put("startLine", diagnostic.sourceRange().start().line());
            builder.put("startColumn", diagnostic.sourceRange().start().column());
            builder.put("endLine", diagnostic.sourceRange().end().line());
            builder.put("endColumn", diagnostic.sourceRange().end().column());
            builder.put("startOffset", diagnostic.sourceRange().start().offset());
            builder.put("endOffset", diagnostic.sourceRange().end().offset());
        }
        if (!diagnostic.expected().isEmpty()) {
            builder.put("expected", diagnostic.expected());
        }
        if (diagnostic.actual() != null) {
            builder.put("actual", diagnostic.actual());
        }
        return Map.copyOf(builder);
    }

    private ValidationErrorCode codeOf(OclDiagnostic diagnostic) {
        return switch (diagnostic.code()) {
            case "UNKNOWN_CLASS" -> ValidationErrorCode.UNKNOWN_CLASS;
            case "UNKNOWN_ATTRIBUTE" -> ValidationErrorCode.UNKNOWN_ATTRIBUTE;
            case "TYPE_ERROR" -> ValidationErrorCode.TYPE_ERROR;
            default -> ValidationErrorCode.TYPE_ERROR;
        };
    }
}
