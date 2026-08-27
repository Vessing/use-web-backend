package de.useweb.backend.reference;

import java.util.List;

record ReferenceCaseResult(
        String id,
        String sourceFile,
        int sourceLine,
        ReferenceStatus declaredStatus,
        ReferenceStatus effectiveStatus,
        String classificationCause,
        String category,
        String targetTestType,
        String pipelinePhase,
        String pipelineOutcome,
        Boolean parseSuccess,
        String astNodeType,
        Boolean typecheckSuccess,
        String expectedType,
        String observedType,
        Boolean evaluationSuccess,
        Object expectedValue,
        Object observedValue,
        List<String> diagnostics,
        List<String> diagnosticCodes,
        String expectedDiagnosticRange,
        List<String> observedDiagnosticRanges,
        List<String> featureTags,
        List<String> gapIds,
        String primaryGapId,
        int roadMapStep,
        String complianceMatrixId,
        String targetBackendStep,
        String blockerClass,
        String blockingReason) {
}
