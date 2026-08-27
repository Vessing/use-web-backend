# Step 31 Complex Example Profiles

The fixtures are backend-owned adaptations of expressions from the listed USE examples. The original files remain unchanged.

| Profile | Source | Executed subset | Explicit exclusion |
|---|---|---|---|
| Demo | `examples/Documentation/Demo/Demo.use` | multi-context navigation, `allInstances`, multi-variable `forAll`, `implies`, `includesAll` | none for the four invariants |
| CarRental | `examples/Papers/1998/RichtersAndGogolla/CarRental.use` | inheritance, `select`, `substring`, query body returning `Set(Vehicle)` | aggregation ownership semantics |
| civstat | `examples/Papers/2006/GogollaBuettnerRichters/civstat.use` | enums, `let`, string navigation and uniqueness quantifier | imperative operation execution |
| Tree | `examples/Others/Tree/Tree.use` | recursive query body, navigation, `collect`, `flatten`, `union` | cyclic graph termination beyond the runtime guard |
| Employee | `examples/Documentation/Employee/Employee.use` | postcondition, `result`, `@pre` | SOIL invocation commands |
| DerivedProperties | `examples/Others/DerivedProperties/derived.use` | adapted lazy derived attribute using `select` | derived association ends, `subsets` metadata |

These profiles are normal integration tests. They do not copy productive USE code and do not add a USE dependency.
