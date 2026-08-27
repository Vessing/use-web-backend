package de.useweb.backend.ocl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import de.useweb.backend.ocl.parser.OclParser;
import de.useweb.backend.ocl.profile.OclComplianceProfile;

class OclParserRobustnessTest {

    private final OclParser parser = new OclParser();

    @Test
    void rejectsNullAndOversizedSourcesWithStructuredDiagnostics() {
        var nullResult = parser.parse(null);
        var oversizedResult = parser.parse("x".repeat(
                Math.toIntExact(OclComplianceProfile.MAX_SOURCE_CHARACTERS + 1)));

        assertThat(nullResult.diagnostics()).singleElement().extracting("code")
                .isEqualTo("INVALID_OCL_INPUT");
        assertThat(oversizedResult.diagnostics()).singleElement().extracting("code")
                .isEqualTo("SOURCE_LIMIT_EXCEEDED");
    }

    @Test
    void boundsRecoveryDiagnosticsAndRemainsReusableAfterMalformedInput() {
        var malformed = parser.parse("#".repeat(100));
        var recovered = parser.parse("if true then 1 else 2 endif");

        assertThat(malformed.success()).isFalse();
        assertThat(malformed.diagnostics()).hasSizeLessThanOrEqualTo(
                Math.toIntExact(OclComplianceProfile.MAX_DIAGNOSTICS));
        assertThat(malformed.diagnostics()).anyMatch(diagnostic ->
                diagnostic.code().equals("DIAGNOSTIC_LIMIT_EXCEEDED"));
        assertThat(recovered.success()).isTrue();
    }

    @Test
    void grammarBasedMutationsAndDeterministicFuzzInputsNeverEscapeAsExceptions() {
        List<String> seeds = List.of(
                "if true then 1 else 2 endif",
                "Sequence{1, 2, 3}->select(i | i > 1)",
                "let x : Integer = 1 in x + 1",
                "Tuple{answer = 42}.answer");
        Random random = new Random(22L);

        for (String seed : seeds) {
            for (int index = 0; index < seed.length(); index += Math.max(1, seed.length() / 7)) {
                String removed = seed.substring(0, index) + seed.substring(Math.min(seed.length(), index + 1));
                assertThatCode(() -> parser.parse(removed)).doesNotThrowAnyException();
            }
        }
        for (int sample = 0; sample < 500; sample++) {
            int length = random.nextInt(128);
            StringBuilder input = new StringBuilder(length);
            for (int index = 0; index < length; index++) {
                input.append((char) (32 + random.nextInt(95)));
            }
            String expression = input.toString();
            assertThatCode(() -> parser.parse(expression)).doesNotThrowAnyException();
        }
    }

    @Test
    void sharedParserKeepsConcurrentRequestsIsolated() {
        List<Boolean> outcomes = IntStream.range(0, 500).parallel()
                .mapToObj(index -> parser.parse(index % 2 == 0
                        ? "Sequence{1, 2, 3}->size() = 3"
                        : "if true then 1 else 2 endif").success())
                .toList();

        assertThat(outcomes).containsOnly(true);
    }
}
