# Original USE Shell OCL Reference Cases

This report analyzes all shell `.in` files and extracts OCL `?` blocks without executing the USE shell.

## Summary

| Metric | Count |
|---|---:|
| Shell .in files | 129 |
| Files with OCL queries | 74 |
| Files without OCL queries | 55 |
| Extracted OCL query cases | 1221 |
| Cases with expected output | 1221 |
| Cases without expected output | 0 |
| Multiline queries | 7 |

## Categories

| Category | Count |
|---|---:|
| `OCL_CONTRACT_EVALUATION` | 8 |
| `OCL_DIAGNOSTIC` | 97 |
| `OCL_EVALUATION` | 1116 |

## Initial Statuses

| Status | Count | Reason |
|---|---:|---|
| FAILING_FORMAT | 0 | Shell values or diagnostics require review and structured normalization. |
| FAILING_INFRASTRUCTURE | 1221 | Model, snapshot, imports, or operation-trace setup is required. |
| UNCLEAR | 0 | Query has no directly associated expected * output. |

## Boundaries

- Shell commands are recorded as setup metadata but are not executed or converted.
- Only `?` query blocks become reference cases; shell-only files remain visible in the file analysis.
- `*` output remains available verbatim even when a conservative value/type summary is present.
- No case is marked `PASSING` or `FAILING_GAP` without the separate reference harness.
- `shell-ocl-reference-cases.json` is the complete machine-readable result.
