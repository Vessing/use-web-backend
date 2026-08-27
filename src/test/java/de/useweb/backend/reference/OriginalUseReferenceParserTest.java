package de.useweb.backend.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("original-use-reference")
class OriginalUseReferenceParserTest {

    @Test
    void processesEveryClassifiedParserCase() throws Exception {
        List<ReferenceCaseResult> results = OriginalUseReferenceHarness.process(
                OriginalUseReferenceHarness.PARSER_METADATA);

        assertEquals(197, results.size());
        assertFalse(results.stream().anyMatch(result -> result.id().isBlank()));
        OriginalUseReferenceHarness.writeReport("original-use-parser-reference-report", results);
    }
}
