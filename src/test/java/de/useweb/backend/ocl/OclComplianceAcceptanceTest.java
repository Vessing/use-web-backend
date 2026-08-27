package de.useweb.backend.ocl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import de.useweb.backend.ocl.profile.OclComplianceProfile;
import de.useweb.backend.ocl.profile.OclFeatureStatus;

class OclComplianceAcceptanceTest {

    @Test
    void acceptsEveryPublishedProfileFeatureAgainstTheVersionedMatrix() throws Exception {
        List<Row> rows;
        try (var stream = getClass().getResourceAsStream("/compliance/ocl-2.4-profile-acceptance.csv")) {
            assertThat(stream).isNotNull();
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                rows = reader.lines().skip(1).filter(line -> !line.isBlank()).map(this::row).toList();
            }
        }

        OclComplianceProfile profile = OclComplianceProfile.current();
        Map<String, Row> byFeature = rows.stream().collect(Collectors.toMap(Row::featureId, row -> row));

        assertThat(profile.profileId()).isEqualTo("use-web-ocl-2.4-subset-v3");
        assertThat(profile.apiVersion()).isEqualTo("v1");
        assertThat(rows).extracting(Row::featureId).doesNotHaveDuplicates();
        assertThat(byFeature.keySet()).containsExactlyInAnyOrderElementsOf(
                profile.features().stream().map(feature -> feature.id()).toList());
        assertThat(rows).extracting(Row::area)
                .contains("SYNTAX", "EVALUATION", "OPTIONAL", "XMI");

        profile.features().forEach(feature -> {
            Row row = byFeature.get(feature.id());
            assertThat(row.matrixIds()).allMatch(id -> id.matches("CM-(OCL|LIB|CTX|UML|XMI)-[0-9]{3}"));
            assertThat(row.evidence()).isNotEmpty();
            assertThat(row.status()).isEqualTo(acceptanceStatus(feature.status()));
        });
    }

    private String acceptanceStatus(OclFeatureStatus status) {
        return status == OclFeatureStatus.SUPPORTED ? "VERIFIED" : status.name();
    }

    private Row row(String line) {
        String[] columns = line.split(",", -1);
        assertThat(columns).hasSize(5);
        return new Row(columns[0], columns[1], columns[2], List.of(columns[3].split(";")),
                List.of(columns[4].split(";")));
    }

    private record Row(String featureId, String area, String status,
            List<String> matrixIds, List<String> evidence) {
    }
}
