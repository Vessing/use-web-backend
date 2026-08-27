package de.useweb.backend.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

@Tag("original-use-reference")
class OriginalUseReferenceGapReportTest {

    @Test
    void writesCompleteReferenceGapReport() throws Exception {
        List<ReferenceCaseResult> allResults = new ArrayList<>();
        allResults.addAll(OriginalUseReferenceHarness.process(OriginalUseReferenceHarness.PARSER_METADATA));
        allResults.addAll(OriginalUseReferenceHarness.process(OriginalUseReferenceHarness.SHELL_METADATA));

        assertEquals(1418, allResults.size());
        assertEquals(EnumSet.allOf(ReferenceStatus.class), EnumSet.copyOf(List.of(ReferenceStatus.values())));
        assertTrue(allResults.stream().allMatch(result -> result.effectiveStatus() != null));
        assertTrue(allResults.stream().allMatch(result -> !result.classificationCause().isBlank()));
        allResults.stream().filter(result -> result.effectiveStatus() == ReferenceStatus.FAILING_GAP)
                .forEach(result -> {
                    assertNotNull(result.primaryGapId(), result.id());
                    assertFalse(result.primaryGapId().isBlank(), result.id());
                    assertTrue(result.roadMapStep() > 0, result.id());
                });
        var formatCases = allResults.stream().filter(result -> result.effectiveStatus() == ReferenceStatus.FAILING_FORMAT)
                .map(ReferenceCaseResult::id).toList();
        var infrastructureCases = allResults.stream()
                .filter(result -> result.effectiveStatus() == ReferenceStatus.FAILING_INFRASTRUCTURE)
                .map(ReferenceCaseResult::id).toList();
        assertTrue(formatCases.isEmpty(), formatCases.toString());
        assertTrue(infrastructureCases.isEmpty(), infrastructureCases.toString());
        assertTrue(allResults.stream().noneMatch(result -> result.effectiveStatus() == ReferenceStatus.UNCLEAR));
        assertEquals(ReferenceStatus.PASSING, statusOf(allResults, "USE-SHELL-A1D603EE8C16-L000036"));
        assertEquals(ReferenceStatus.PASSING, statusOf(allResults, "USE-SHELL-04106E1ECB9E-L000022"));
        assertEquals(ReferenceStatus.NON_OCL_OR_SHELL_ONLY,
                statusOf(allResults, "USE-SHELL-E33829A9C98C-L000046"));
        assertEquals(ReferenceStatus.NON_OCL_OR_SHELL_ONLY,
                statusOf(allResults, "USE-PARSER-09D3B4F798AB-011"));
        List<ReferenceCaseResult> nonPassing = allResults.stream()
                .filter(result -> result.effectiveStatus() != ReferenceStatus.PASSING).toList();
        nonPassing.forEach(result -> {
            assertNotNull(result.complianceMatrixId(), result.id());
            assertTrue(result.complianceMatrixId().matches("CM-(OCL|LIB|CTX|UML)-[0-9]{3}"), result.id());
            assertNotNull(result.targetBackendStep(), result.id());
            assertTrue(result.targetBackendStep().matches("B([4-9]|1[0-9]|2[0-9]|3[0-3])"), result.id());
            assertNotNull(result.blockerClass(), result.id());
            assertFalse(result.blockerClass().isBlank(), result.id());
        });
        allResults.stream().filter(result -> !result.diagnostics().isEmpty())
                .forEach(result -> assertFalse(result.diagnosticCodes().isEmpty(), result.id()));
        allResults.stream().filter(result -> result.expectedDiagnosticRange() != null)
                .forEach(result -> assertTrue(result.expectedDiagnosticRange().matches("[0-9]+:[0-9]+"), result.id()));

        var reportPath = OriginalUseReferenceHarness.writeReport("original-use-reference-report", allResults);
        var report = new ObjectMapper().readTree(reportPath.toFile());
        assertEquals("1.4", report.path("schemaVersion").asText());
        assertTrue(report.path("pipelinePhaseCounts").isObject());
        assertTrue(report.path("classificationCauseCounts").isObject());
        assertTrue(report.path("diagnosticCodeCounts").isObject());
        assertTrue(report.path("failingFeatureTagCounts").isObject());
        assertTrue(report.path("blockingReasonCounts").isObject());
        assertTrue(report.path("roadMapStepCounts").isObject());
        assertTrue(report.path("complianceMatrixCounts").isObject());
        assertTrue(report.path("targetBackendStepCounts").isObject());
        assertTrue(report.path("blockerClassCounts").isObject());
        assertEquals(nonPassing.size(), sumCounts(report.path("complianceMatrixCounts")));
        assertEquals(nonPassing.size(), sumCounts(report.path("targetBackendStepCounts")));
        assertEquals(nonPassing.size(), sumCounts(report.path("blockerClassCounts")));
        assertTrue(report.path("prioritizedGapBacklog").isArray());
        assertTrue(report.path("prioritizedGapBacklog").isEmpty());
        assertEquals(0, report.path("classificationCauseCounts")
                .path("BACKEND_MODEL_OR_EVALUATION_EXCEPTION").asLong());
        assertEquals(0, report.path("classificationCauseCounts")
                .path("MINIMAL_FIXTURE_REVEALS_UML_OR_OCL_GAP").asLong());
        assertEquals(0, report.path("classificationCauseCounts")
                .path("REFERENCE_SETUP_OCL_EXPRESSION_NOT_SUPPORTED").asLong());
        assertEquals(0, report.path("classificationCauseCounts")
                .path("REFERENCE_MODEL_OCL_DEFINITION_DIAGNOSTIC").asLong());
        assertTrue(allResults.stream().noneMatch(result -> "B29".equals(result.targetBackendStep())
                && "OCL_TYPE_RULE_NOT_SUPPORTED".equals(result.classificationCause())));
        assertEquals(ReferenceStatus.PASSING,
                statusOf(allResults, "USE-SHELL-E33829A9C98C-L001459"));
        assertEquals(ReferenceStatus.PASSING,
                statusOf(allResults, "USE-SHELL-46751898C995-L000104"));
        assertEquals(ReferenceStatus.NON_OCL_OR_SHELL_ONLY,
                statusOf(allResults, "USE-SHELL-E33829A9C98C-L000403"));
        long classifiedGaps = allResults.stream()
                .filter(result -> result.effectiveStatus() == ReferenceStatus.FAILING_GAP).count();
        assertEquals(0, classifiedGaps);
        long reportedGaps = 0;
        for (var count : report.path("primaryGapCounts")) reportedGaps += count.asLong();
        assertEquals(classifiedGaps, reportedGaps);
    }

    private long sumCounts(com.fasterxml.jackson.databind.JsonNode counts) {
        long total = 0;
        for (var count : counts) total += count.asLong();
        return total;
    }

    private ReferenceStatus statusOf(List<ReferenceCaseResult> results, String id) {
        return results.stream().filter(result -> result.id().equals(id)).findFirst().orElseThrow().effectiveStatus();
    }
}
