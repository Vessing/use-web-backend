# Original USE Test Resources

This directory contains unchanged test resources copied from the original USE
project for use as a reference corpus in the university project.

## Sources

| Area | Original path | Local reference path |
|---|---|---|
| Parser | `use/use-core/src/test/resources/org/tzi/use/parser/` | `parser/` |
| Shell | `use/use-gui/src/it/resources/testfiles/shell/` | `shell/` |

Only test resources are copied. No productive USE code, USE Core dependency,
USE GUI dependency, old test runner, or old test infrastructure is included.

## Purpose

The resources are the immutable input corpus for the separately executable
Original USE reference test suite. Converted metadata classifies source cases;
the original resources remain unchanged.

The reference suite uses the new backend's own parser and test harness. Expected
reference failures may be
reported as `FAILING_GAP`, `FAILING_FORMAT`, or `FAILING_INFRASTRUCTURE` without
blocking the normal CI pipeline.

## File Types

- `.use`: USE models and model/parser inputs.
- `.fail`: negative parser or validation reference inputs.
- `.in`: USE shell command and OCL evaluation inputs.
- Lines prefixed with `*` in `.in` files: expected USE shell output. These lines
  are reference expectations and must not automatically become exact string
  assertions in the new backend.
- Other files such as `.cmd`, `.assl`, `.clt`, and `.olt`: supporting resources
  retained to preserve the original test context.

## Integrity

The `checksums.sha256` file records each copied resource using its path relative
to `original-use/` and its SHA-256 digest. It is generated from the original
sources after copying and is used to verify that source and target trees contain
the same paths and bytes.

`inventory.json` is the complete machine-readable file inventory.
`inventory.md` summarizes areas, file extensions, subdirectories, heuristic
content types, and candidate counts. Both files can be regenerated with:

```powershell
./scripts/generate-original-use-inventory.ps1
```

The inventory's content types, feature tags, and candidate flags are heuristic
file-level hints. They are not reviewed Reference Cases and do not assign a
`PASSING` or `FAILING_*` execution status.

Parser-corpus source blocks are classified separately under:

```text
../converted/metadata/parser-reference-cases.json
../converted/metadata/parser-reference-cases.md
```

They can be regenerated with:

```powershell
./scripts/generate-original-use-parser-reference-cases.ps1
```

This metadata pairs `.fail` diagnostics with their `.use` models, classifies
import fixtures and OCL `context` blocks, and splits `test_expr.in` into stable
expression cases. It does not execute cases or assign `PASSING` status.

Shell OCL query metadata is generated under:

```text
../converted/metadata/shell-ocl-reference-cases.json
../converted/metadata/shell-ocl-reference-cases.md
```

Regenerate it with:

```powershell
./scripts/generate-original-use-shell-reference-cases.ps1
```

The generator analyzes every shell `.in` file, extracts `?` queries and directly
following `*` expectation blocks, and records model/command setup dependencies.
It does not execute USE shell commands or assign `PASSING` status.

## Separate Reference Test Suite

Run only the Original USE reference suite from the backend root with:

```powershell
mvn -Preference-tests test
```

The Maven profile selects the JUnit tag `original-use-reference`. A normal
`mvn test` explicitly excludes that tag, so known reference gaps cannot block
the normal CI test suite.

The three runners are:

- `OriginalUseReferenceParserTest`
- `OriginalUseReferenceShellOclTest`
- `OriginalUseReferenceGapReportTest`

Generated reports are written to `target/reference-reports/`. Metadata
integrity errors fail the reference job. Declared `FAILING_*` statuses remain
report data and do not fail JUnit. The harness currently parses isolated OCL
expressions, type-checks and evaluates context-free supported expressions, and
compares structured primitive values and types. Each result contains declared
and effective status, observed pipeline phase, diagnostics, classification
cause, primary gap, and roadmap step.

The reports are generated in JSON and Markdown. They include status counts,
pipeline outcomes, primary-gap counts, and status transitions from the previous
local report. The initial classified baseline contains 1,418 cases: 31
`PASSING`, 1,099 `FAILING_GAP`, 109 `FAILING_FORMAT`, 57
`FAILING_INFRASTRUCTURE`, and 122 `UNCLEAR`. These counts are planning signals,
not an OCL-compliance percentage; `UNCLEAR` and USE-compatibility cases still
require normative review against OCL 2.4.

The copied files must not be edited. Adapted metadata, normalized assertions,
reports, and executable reference cases belong outside `original-use/` in the
separate converted/reference-test structure planned by the analysis.
