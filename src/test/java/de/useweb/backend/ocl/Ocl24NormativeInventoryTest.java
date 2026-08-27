package de.useweb.backend.ocl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class Ocl24NormativeInventoryTest {

    private static final String RESOURCE = "/compliance/ocl-2.4-normative-inventory.csv";
    private static final Set<String> KINDS = Set.of(
            "SYNTAX", "WELL_FORMEDNESS", "SEMANTICS", "LIBRARY_SIGNATURE", "CONTEXT", "COMPLIANCE_POINT");
    private static final Set<String> STATUSES = Set.of("INVENTORIED", "REVIEW_REQUIRED", "OUT_OF_SCOPE", "RETIRED");

    @Test
    void providesAStableCompleteInventorySchema() throws IOException {
        List<Row> rows = readRows();

        assertThat(rows).isNotEmpty();
        assertThat(rows.stream().map(Row::kind).collect(java.util.stream.Collectors.toSet()))
                .containsExactlyInAnyOrderElementsOf(KINDS);

        Set<String> ids = new HashSet<>();
        for (Row row : rows) {
            assertThat(ids.add(row.id())).as("unique ID %s", row.id()).isTrue();
            assertThat(row.id()).startsWith(prefixFor(row.kind()));
            assertThat(row.group()).isNotBlank();
            assertThat(row.name()).isNotBlank();
            assertThat(row.specReference()).isNotBlank();
            assertThat(row.parentMatrixId()).matches("CM-(OCL|LIB|CTX)-[0-9]{3}");
            assertThat(row.requirement()).isNotBlank();
            assertThat(row.status()).isIn(STATUSES);

            if (row.kind().equals("LIBRARY_SIGNATURE")) {
                assertThat(row.signature()).isNotBlank().isNotEqualTo("-");
            } else {
                assertThat(row.signature()).isEqualTo("-");
            }
        }
    }

    @Test
    void coversEveryDetailedOclLibraryAndContextParent() throws IOException {
        Set<String> parentIds = readRows().stream().map(Row::parentMatrixId).collect(java.util.stream.Collectors.toSet());

        for (int id = 1; id <= 27; id++) {
            assertThat(parentIds).contains("CM-OCL-%03d".formatted(id));
        }
        for (int id = 1; id <= 17; id++) {
            assertThat(parentIds).contains("CM-LIB-%03d".formatted(id));
        }
        for (int id = 1; id <= 8; id++) {
            assertThat(parentIds).contains("CM-CTX-%03d".formatted(id));
        }
    }

    private List<Row> readRows() throws IOException {
        InputStream stream = getClass().getResourceAsStream(RESOURCE);
        assertThat(stream).as("inventory resource").isNotNull();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            List<String> lines = reader.lines().filter(line -> !line.isBlank()).toList();
            assertThat(lines.get(0)).isEqualTo(
                    "id,kind,group,name,signature,specReference,parentMatrixId,requirement,status");
            return lines.stream().skip(1).map(this::parseRow).toList();
        }
    }

    private Row parseRow(String line) {
        String[] columns = line.split(",", -1);
        assertThat(columns).as("nine CSV columns in %s", line).hasSize(9);
        return new Row(columns[0], columns[1], columns[2], columns[3], columns[4], columns[5], columns[6],
                columns[7], columns[8]);
    }

    private String prefixFor(String kind) {
        assertThat(kind).isIn(KINDS);
        return switch (kind) {
            case "SYNTAX" -> "OCL24-SYN-";
            case "WELL_FORMEDNESS" -> "OCL24-WFR-";
            case "SEMANTICS" -> "OCL24-SEM-";
            case "LIBRARY_SIGNATURE" -> "OCL24-LIB-";
            case "CONTEXT" -> "OCL24-CTX-";
            case "COMPLIANCE_POINT" -> "OCL24-CP-";
            default -> throw new IllegalArgumentException("Unknown inventory kind: " + kind);
        };
    }

    private record Row(String id, String kind, String group, String name, String signature,
            String specReference, String parentMatrixId, String requirement, String status) {
    }
}
