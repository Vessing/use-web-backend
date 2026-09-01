package de.useweb.backend.ocl.profile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record OclComplianceProfile(
        String profileId,
        String oclVersion,
        String complianceClaim,
        String apiVersion,
        List<String> enabledOptionalCompliancePoints,
        List<OclFeatureSupport> features,
        Map<String, Long> runtimeLimits) {

    public static final long MAX_ITERATOR_BINDINGS = 100_000L;
    public static final long MAX_TOKENS = 10_000L;
    public static final long MAX_SOURCE_CHARACTERS = 100_000L;
    public static final long MAX_DIAGNOSTICS = 32L;
    public static final long MAX_AST_DEPTH = 256L;
    public static final long MAX_EVALUATION_MILLIS = 2_000L;
    public static final long MAX_DEFINITION_RECURSION = 64L;
    public static final long MAX_RESULT_ELEMENTS = 1_000_000L;

    public OclComplianceProfile {
        enabledOptionalCompliancePoints = List.copyOf(enabledOptionalCompliancePoints);
        features = List.copyOf(features);
        runtimeLimits = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(runtimeLimits));
    }

    public static OclComplianceProfile current() {
        return new OclComplianceProfile(
                "use-web-ocl-2.4-subset-v3",
                "2.4",
                "OCL 2.4-based subset; no full syntax, evaluation, or XMI compliance claim",
                "v1",
                List.of("allInstances", "pre-values", "oclIsNew"),
                List.of(
                        feature("OCL-PROFILE-001", "Core expressions", OclFeatureStatus.PARTIAL,
                                "Clauses 8-10", "Core expressions are implemented; the published syntax matrix identifies remaining literal and name gaps."),
                        feature("OCL-PROFILE-002", "Diagnostics", OclFeatureStatus.SUPPORTED,
                                "Clauses 8-10", "Lexer, parser, typecheck and evaluation diagnostics carry source ranges."),
                        feature("OCL-PROFILE-003", "Collection types", OclFeatureStatus.SUPPORTED,
                                "Clause 11", "Set, Bag, Sequence and OrderedSet values and literals are supported."),
                        feature("OCL-PROFILE-004", "Collection operations", OclFeatureStatus.SUPPORTED,
                                "Clause 11", "Core queries and transformations implemented by the operation registry are supported."),
                        feature("OCL-PROFILE-005", "Iterator expressions", OclFeatureStatus.SUPPORTED,
                                "Clauses 8 and 11", "forAll, exists, select, reject, collect, any, one, isUnique, sortedBy, closure and iterate."),
                        feature("OCL-PROFILE-006", "Control and binding", OclFeatureStatus.SUPPORTED,
                                "Clauses 8-10", "if-then-else and let expressions are supported with lexical scopes."),
                        feature("OCL-PROFILE-007", "Model navigation", OclFeatureStatus.PARTIAL,
                                "Clauses 8-10", "Attribute and navigable association-end chains plus explicit attribute and operation redefinition dispatch are implemented; optional non-navigable access remains excluded."),
                        feature("OCL-PROFILE-008", "Extended types", OclFeatureStatus.SUPPORTED,
                                "Clauses 8 and 11", "Tuples, enumerations, UnlimitedNatural, inheritance and runtime type operations."),
                        feature("OCL-PROFILE-009", "allInstances", OclFeatureStatus.PARTIAL,
                                "Optional evaluation compliance point", "Enabled for model classifiers and the current snapshot; complete optional-point compliance is not claimed."),
                        feature("OCL-PROFILE-010", "Operation contracts", OclFeatureStatus.SUPPORTED,
                                "Clause 12", "Persisted pre/post contracts gate atomic invocations with result, @pre and oclIsNew."),
                        feature("OCL-PROFILE-011", "Derived, init, body and def", OclFeatureStatus.PARTIAL,
                                "Clause 12", "Backend definition parsing, checking and evaluation exist; full UML lifecycle integration remains limited."),
                        feature("OCL-PROFILE-012", "OclMessage", OclFeatureStatus.NOT_SUPPORTED,
                                "Optional evaluation compliance point", "Message expressions and message result access are not implemented."),
                        feature("OCL-PROFILE-013", "Non-navigable association access", OclFeatureStatus.NOT_SUPPORTED,
                                "Optional evaluation compliance point", "Only explicitly navigable model roles are resolved."),
                        feature("OCL-PROFILE-014", "XMI interchange", OclFeatureStatus.OUT_OF_SCOPE,
                                "XMI compliance", "The application uses its versioned REST/JSON project contract."),
                        feature("OCL-PROFILE-015", "Visibility bypass for non-public features",
                                OclFeatureStatus.NOT_SUPPORTED, "Optional evaluation compliance point",
                                "Private, protected and package visibility is enforced from the UML context; unrelated contexts cannot bypass it."),
                        feature("OCL-PROFILE-016", "State machines and oclInState",
                                OclFeatureStatus.OUT_OF_SCOPE, "Optional behavioral UML/OCL area",
                                "State-machine models, active states and oclInState are outside the product target profile.")),
                Map.of(
                        "maxIteratorBindings", MAX_ITERATOR_BINDINGS,
                        "maxTokens", MAX_TOKENS,
                        "maxSourceCharacters", MAX_SOURCE_CHARACTERS,
                        "maxDiagnostics", MAX_DIAGNOSTICS,
                        "maxAstDepth", MAX_AST_DEPTH,
                        "maxEvaluationMillis", MAX_EVALUATION_MILLIS,
                        "maxDefinitionRecursion", MAX_DEFINITION_RECURSION,
                        "maxResultElements", MAX_RESULT_ELEMENTS));
    }

    private static OclFeatureSupport feature(String id, String group, OclFeatureStatus status,
            String standardBasis, String notes) {
        return new OclFeatureSupport(id, group, status, standardBasis, notes);
    }
}
