# Original USE Test Resource Inventory

Generated from the unchanged resources below `parser/` and `shell/`.
This is a file inventory, not a classification of executable reference cases.

## Summary

| Metric | Count |
|---|---:|
| Total files | 387 |
| Parser files | 90 |
| Shell files | 297 |
| Heuristic reference-suite candidates | 368 |
| Supporting or unclear resources | 19 |

## File Extensions

| Extension | Count |
|---|---:|
| `.assl` | 8 |
| `.clt` | 5 |
| `.cmd` | 8 |
| `.fail` | 32 |
| `.in` | 130 |
| `.olt` | 2 |
| `.use` | 202 |

## Subdirectories

- `parser/imports`
- `shell/imports`
- `shell/relativepath`
- `shell/relativepath/Book`
- `shell/relativepath/Copy`
- `shell/relativepath/Szenario1`
- `shell/relativepath/User`

## Heuristic Categories

| Content type | Files |
|---|---:|
| `ASSL_SCRIPT` | 8 |
| `CLASS_LAYOUT` | 5 |
| `COMMAND_SCRIPT` | 8 |
| `EXPECTED_SHELL_OUTPUT` | 127 |
| `IMPORT_REFERENCE` | 26 |
| `NEGATIVE_PARSER_INPUT` | 32 |
| `OBJECT_LAYOUT` | 2 |
| `OCL_DECLARATION` | 68 |
| `OCL_QUERY` | 74 |
| `SHELL_INPUT` | 130 |
| `USE_MODEL` | 202 |

## Important Limitations

- Tags and candidate flags are heuristic and may contain false positives or false negatives.
- No `.use`, `.fail`, or `.in` blocks are extracted in this roadmap step.
- No `PASSING` or `FAILING_*` status is assigned without an executable reference test.
- `inventory.json` is the complete machine-readable per-file inventory.
