# Original USE Parser Reference Cases

This report classifies parser-corpus files and source blocks. It does not execute them.

## Summary

| Metric | Count |
|---|---:|
| Parser source files | 90 |
| .use files | 57 |
| .fail files paired as expectations | 32 |
| Model parser cases | 49 |
| Import fixture cases | 8 |
| OCL declaration blocks | 19 |
| `test_expr.in` expression cases | 121 |
| Total reference cases | 197 |

## Initial Statuses

| Status | Count | Reason |
|---|---:|---|
| FAILING_FORMAT | 32 | Original compiler diagnostics require structured normalization. |
| FAILING_INFRASTRUCTURE | 165 | Parser/model/import/evaluation reference harness is not part of this step. |
| UNCLEAR | 0 | Reserved for cases requiring review. |

## Boundaries

- No shell corpus file is included.
- No original USE reference case is executed or marked `PASSING`.
- `context` blocks are source-level units; splitting individual invariants/contracts is later review work.
- Feature tags and OCL 2.4 compatibility are preliminary classifications.
- `parser-reference-cases.json` is the complete machine-readable case list.
