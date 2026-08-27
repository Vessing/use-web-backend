package de.useweb.backend.ocl.contract;

import java.util.List;

import de.useweb.backend.ocl.diagnostics.OclDiagnostic;
import de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase;
import de.useweb.backend.ocl.evaluation.OclEvaluator;
import de.useweb.backend.ocl.typecheck.OclType;
import de.useweb.backend.ocl.typecheck.OclTypeChecker;
import de.useweb.backend.ocl.value.BooleanValue;

public final class OperationContractService {
    private final OclTypeChecker typeChecker;
    private final OclEvaluator evaluator;

    public OperationContractService() {
        this(new OclTypeChecker(), new OclEvaluator());
    }

    public OperationContractService(OclTypeChecker typeChecker, OclEvaluator evaluator) {
        this.typeChecker = typeChecker;
        this.evaluator = evaluator;
    }

    public OperationContractResult evaluate(OperationContext context, OperationContract contract) {
        if (!context.reference().equals(contract.reference())) {
            return error(context, contract, "CONTRACT_CONTEXT_MISMATCH",
                    "Contract does not belong to the invoked operation.", List.of());
        }
        var typecheck = typeChecker.checkExpression(context.typeEnvironment(contract.kind()), contract.expression());
        if (!typecheck.success()) {
            return error(context, contract, "CONTRACT_TYPE_ERROR",
                    "Operation contract did not pass type checking.", typecheck.diagnostics());
        }
        if (!typecheck.resultType().sameTypeAs(OclType.BOOLEAN)) {
            OclDiagnostic diagnostic = new OclDiagnostic(OclDiagnosticPhase.TYPECHECK,
                    "CONTRACT_NOT_BOOLEAN", "ERROR", "Operation contract must return Boolean.",
                    contract.expression().sourceRange(), List.of("Boolean"), typecheck.resultType().displayName());
            return error(context, contract, "CONTRACT_NOT_BOOLEAN", diagnostic.message(), List.of(diagnostic));
        }
        try {
            var evaluation = evaluator.evaluate(contract.expression(), context.evaluationContext(contract.kind()));
            if (!evaluation.success() || !(evaluation.value() instanceof BooleanValue booleanValue)) {
                return error(context, contract, "CONTRACT_EVALUATION_ERROR",
                        "Operation contract evaluation failed.", evaluation.diagnostics());
            }
            if (booleanValue.value()) {
                return result(context, contract, OperationContractResult.Status.SATISFIED, List.of());
            }
            String code = contract.kind() == OperationConstraintKind.PRECONDITION
                    ? "PRECONDITION_VIOLATION" : "POSTCONDITION_VIOLATION";
            String message = contract.kind() == OperationConstraintKind.PRECONDITION
                    ? "Precondition '" + contract.name() + "' is not satisfied."
                    : "Postcondition '" + contract.name() + "' is not satisfied.";
            OclDiagnostic violation = new OclDiagnostic(OclDiagnosticPhase.EVALUATION, code, "ERROR", message,
                    contract.expression().sourceRange(), List.of("true"), "false");
            return result(context, contract, OperationContractResult.Status.VIOLATED, List.of(violation));
        } catch (IllegalStateException exception) {
            OclDiagnostic diagnostic = new OclDiagnostic(OclDiagnosticPhase.EVALUATION,
                    "CONTRACT_CONTEXT_ERROR", "ERROR", exception.getMessage(), contract.expression().sourceRange(),
                    List.of("complete operation context"), "incomplete context");
            return error(context, contract, "CONTRACT_CONTEXT_ERROR", exception.getMessage(), List.of(diagnostic));
        }
    }

    private OperationContractResult error(OperationContext context, OperationContract contract, String code,
            String message, List<OclDiagnostic> diagnostics) {
        List<OclDiagnostic> effective = diagnostics.isEmpty()
                ? List.of(new OclDiagnostic(OclDiagnosticPhase.EVALUATION, code, "ERROR", message,
                        contract.expression().sourceRange(), List.of(), null))
                : diagnostics;
        return result(context, contract, OperationContractResult.Status.CONTEXT_ERROR, effective);
    }

    private OperationContractResult result(OperationContext context, OperationContract contract,
            OperationContractResult.Status status, List<OclDiagnostic> diagnostics) {
        return new OperationContractResult(context.invocationId(), contract.id(), context.reference(),
                context.receiverId(), contract.kind(), status, diagnostics);
    }
}
