package de.useweb.backend.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("original-use-reference")
class OriginalUseReferenceShellOclTest {

    @Test
    void processesEveryClassifiedShellOclCase() throws Exception {
        List<ReferenceCaseResult> results = OriginalUseReferenceHarness.process(
                OriginalUseReferenceHarness.SHELL_METADATA);

        assertEquals(1221, results.size());
        assertTrue(results.stream().allMatch(result -> result.sourceFile().startsWith("shell/")));
        OriginalUseReferenceHarness.writeReport("original-use-shell-ocl-reference-report", results);
    }
}
