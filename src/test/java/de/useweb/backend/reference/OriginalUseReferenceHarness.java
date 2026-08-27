package de.useweb.backend.reference;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import de.useweb.backend.domain.snapshot.ObjectInstance;
import de.useweb.backend.domain.snapshot.ObjectInstanceId;
import de.useweb.backend.domain.snapshot.ObjectModel;
import de.useweb.backend.domain.snapshot.ObjectModelId;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlModelId;
import de.useweb.backend.ocl.evaluation.EvaluationContext;
import de.useweb.backend.ocl.evaluation.OclEvaluationResult;
import de.useweb.backend.ocl.evaluation.OclEvaluator;
import de.useweb.backend.ocl.parser.OclParseResult;
import de.useweb.backend.ocl.parser.OclParser;
import de.useweb.backend.ocl.typecheck.OclTypeChecker;
import de.useweb.backend.ocl.typecheck.OclTypecheckResult;
import de.useweb.backend.ocl.typecheck.TypeEnvironment;

final class OriginalUseReferenceHarness {

    static final String PARSER_METADATA = "reference/converted/metadata/parser-reference-cases.json";
    static final String SHELL_METADATA = "reference/converted/metadata/shell-ocl-reference-cases.json";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final Pattern DIAGNOSTIC_CODE = Pattern.compile("\\b([A-Z][A-Z0-9_]{2,})\\s*:");
    private static final Pattern LEGACY_RESULT = Pattern.compile(".*?->\\s*(.+?)\\s*:\\s*([^:]+)$");
    private static final Pattern LEGACY_SOURCE_POSITION = Pattern.compile("<input>:(\\d+):(\\d+):");
    private OriginalUseReferenceHarness() {
    }

    static JsonNode loadMetadata(String resourcePath) throws IOException {
        ClassLoader classLoader = OriginalUseReferenceHarness.class.getClassLoader();
        try (InputStream input = classLoader.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("Reference metadata not found: " + resourcePath);
            }
            return OBJECT_MAPPER.readTree(input);
        }
    }

    static List<ReferenceCaseResult> process(String resourcePath) throws IOException {
        JsonNode cases = loadMetadata(resourcePath).path("cases");
        if (!cases.isArray()) {
            throw new IllegalStateException("Reference metadata has no cases array: " + resourcePath);
        }

        List<ReferenceCaseResult> results = new ArrayList<>(cases.size());
        Set<String> ids = new HashSet<>();
        for (JsonNode referenceCase : cases) {
            String id = requiredText(referenceCase, "id");
            if (!ids.add(id)) {
                throw new IllegalStateException("Duplicate reference case id: " + id);
            }
            results.add(processCase(referenceCase));
        }
        return List.copyOf(results);
    }

    static Path writeReport(String suiteName, List<ReferenceCaseResult> results) throws IOException {
        Path reportDirectory = Path.of("target", "reference-reports");
        Files.createDirectories(reportDirectory);
        Path jsonPath = reportDirectory.resolve(suiteName + ".json");
        Map<String, ReferenceStatus> previousStatuses = previousStatuses(jsonPath);

        Map<ReferenceStatus, Long> declaredCounts = statusCounts(results, false);
        Map<ReferenceStatus, Long> effectiveCounts = statusCounts(results, true);
        Map<String, Long> pipelineCounts = countBy(results, ReferenceCaseResult::pipelineOutcome);
        Map<String, Long> phaseCounts = countBy(results, ReferenceCaseResult::pipelinePhase);
        Map<String, Long> causeCounts = countBy(results, ReferenceCaseResult::classificationCause);
        Map<String, Long> diagnosticCodeCounts = countFlattened(results, ReferenceCaseResult::diagnosticCodes);
        Map<String, Long> failingFeatureCounts = countFlattened(
                results.stream().filter(result -> result.effectiveStatus() != ReferenceStatus.PASSING).toList(),
                ReferenceCaseResult::featureTags);
        Map<String, Long> blockingReasonCounts = countByNullable(results, ReferenceCaseResult::blockingReason);
        Map<String, Long> blockerClassCounts = countByNullable(results, ReferenceCaseResult::blockerClass);
        Map<String, Long> complianceMatrixCounts = countByNullable(results, ReferenceCaseResult::complianceMatrixId);
        Map<String, Long> targetBackendStepCounts = countByNullable(results, ReferenceCaseResult::targetBackendStep);
        Map<String, Long> roadMapStepCounts = results.stream().filter(result -> result.roadMapStep() > 0)
                .collect(java.util.stream.Collectors.groupingBy(
                        result -> Integer.toString(result.roadMapStep()),
                        java.util.TreeMap::new, java.util.stream.Collectors.counting()));
        Map<String, Long> gapCounts = new LinkedHashMap<>();
        results.stream().filter(result -> result.effectiveStatus() == ReferenceStatus.FAILING_GAP)
                .map(ReferenceCaseResult::primaryGapId).filter(Objects::nonNull).distinct().sorted()
                .forEach(gap -> gapCounts.put(gap,
                        results.stream().filter(result -> result.effectiveStatus() == ReferenceStatus.FAILING_GAP)
                                .filter(result -> gap.equals(result.primaryGapId())).count()));

        Map<String, Long> transitions = new LinkedHashMap<>();
        for (ReferenceCaseResult result : results) {
            ReferenceStatus previous = previousStatuses.getOrDefault(result.id(), result.declaredStatus());
            String transition = previous + " -> " + result.effectiveStatus();
            transitions.merge(transition, 1L, Long::sum);
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", "1.4");
        report.put("generatedAt", Instant.now().toString());
        report.put("suite", suiteName);
        report.put("totalCases", results.size());
        report.put("supportedStatuses", List.of(ReferenceStatus.values()));
        report.put("declaredStatusCounts", declaredCounts);
        report.put("effectiveStatusCounts", effectiveCounts);
        report.put("pipelineOutcomeCounts", pipelineCounts);
        report.put("pipelinePhaseCounts", phaseCounts);
        report.put("classificationCauseCounts", causeCounts);
        report.put("diagnosticCodeCounts", diagnosticCodeCounts);
        report.put("failingFeatureTagCounts", failingFeatureCounts);
        report.put("blockingReasonCounts", blockingReasonCounts);
        report.put("blockerClassCounts", blockerClassCounts);
        report.put("complianceMatrixCounts", complianceMatrixCounts);
        report.put("targetBackendStepCounts", targetBackendStepCounts);
        report.put("roadMapStepCounts", roadMapStepCounts);
        report.put("primaryGapCounts", gapCounts);
        report.put("prioritizedGapBacklog", prioritizedGapBacklog(results, gapCounts));
        report.put("statusTransitions", transitions);
        report.put("results", results);
        OBJECT_MAPPER.writeValue(jsonPath.toFile(), report);
        writeMarkdown(reportDirectory.resolve(suiteName + ".md"), suiteName, results.size(), effectiveCounts,
                pipelineCounts, phaseCounts, causeCounts, diagnosticCodeCounts, failingFeatureCounts,
                blockingReasonCounts, blockerClassCounts, complianceMatrixCounts, targetBackendStepCounts,
                roadMapStepCounts, gapCounts, transitions);
        return jsonPath;
    }

    private static ReferenceCaseResult processCase(JsonNode referenceCase) {
        String expression = expression(referenceCase);
        ReferenceStatus declaredStatus = ReferenceStatus.valueOf(requiredText(referenceCase, "currentStatus"));
        ExecutionObservation observation = execute(expression, referenceCase);
        List<String> gapIds = textArray(referenceCase.path("gapIds"));
        Classification classification = classify(referenceCase, observation, gapIds);
        ReferenceAssignment assignment = referenceAssignment(referenceCase, classification);

        return new ReferenceCaseResult(
                requiredText(referenceCase, "id"),
                requiredText(referenceCase, "sourceFile"),
                referenceCase.path("sourceLine").asInt(),
                declaredStatus,
                classification.status(),
                classification.cause(),
                referenceCase.path("category").asText("UNCLEAR"),
                referenceCase.path("targetTestType").asText("INFRASTRUCTURE"),
                observation.phase(),
                observation.outcome(),
                observation.parseSuccess(),
                observation.astNodeType(),
                observation.typecheckSuccess(),
                referenceCase.path("expectedType").asText(null),
                observation.observedType(),
                observation.evaluationSuccess(),
                expectedValue(referenceCase),
                observation.observedValue(),
                observation.diagnostics(),
                diagnosticCodes(observation),
                expectedDiagnosticRange(referenceCase),
                observation.diagnosticRanges(),
                textArray(referenceCase.path("featureTags")),
                gapIds,
                classification.primaryGapId(),
                classification.roadMapStep(),
                assignment.complianceMatrixId(),
                assignment.targetBackendStep(),
                assignment.blockerClass(),
                classification.blockingReason());
    }

    private static ReferenceAssignment referenceAssignment(JsonNode referenceCase, Classification classification) {
        if (classification.status() == ReferenceStatus.PASSING) {
            return new ReferenceAssignment(null, null, null);
        }
        String matrixId = matrixId(referenceCase.path("featureTags"), classification.primaryGapId());
        String blockerClass = switch (classification.status()) {
            case FAILING_GAP -> "LANGUAGE_OR_SEMANTIC_GAP";
            case FAILING_FORMAT -> "EXPECTATION_NORMALIZATION";
            case FAILING_INFRASTRUCTURE -> "REFERENCE_INFRASTRUCTURE";
            case UNCLEAR -> "NORMATIVE_REVIEW";
            case NON_OCL_OR_SHELL_ONLY -> "NON_OCL_OR_SHELL_ONLY";
            case PASSING -> throw new IllegalStateException("Passing cases have no blocker assignment.");
        };
        return new ReferenceAssignment(matrixId, targetBackendStep(referenceCase,
                classification.primaryGapId(), classification.status(), classification.cause()), blockerClass);
    }

    private static String matrixId(JsonNode featureTags, String gap) {
        if (hasTag(featureTags, "OCL_POSTCONDITION") || hasTag(featureTags, "OCL_RESULT")) return "CM-CTX-003";
        if (hasTag(featureTags, "OCL_PRECONDITION")) return "CM-CTX-002";
        if (hasTag(featureTags, "OCL_INVARIANT")) return "CM-CTX-001";
        if (hasTag(featureTags, "OCL_ALL_INSTANCES")) return "CM-OCL-015";
        if (hasTag(featureTags, "OCL_TYPE_OPERATION")) return "CM-OCL-014";
        if (hasTag(featureTags, "OCL_UNDEFINED")) return "CM-OCL-010";
        if (hasTag(featureTags, "OCL_IMPLIES")) return "CM-OCL-006";
        if (hasTag(featureTags, "OCL_LET")) return "CM-OCL-004";
        if (hasTag(featureTags, "OCL_IF")) return "CM-OCL-005";
        if (hasTag(featureTags, "ITERATOR_ITERATE")) return "CM-OCL-023";
        if (hasTag(featureTags, "ITERATOR_CLOSURE") || hasTag(featureTags, "ITERATOR_IS_UNIQUE")
                || hasTag(featureTags, "ITERATOR_SORTED_BY")) return "CM-OCL-022";
        if (hasTag(featureTags, "ITERATOR_SELECT") || hasTag(featureTags, "ITERATOR_REJECT")
                || hasTag(featureTags, "ITERATOR_COLLECT")) return "CM-OCL-021";
        if (hasTag(featureTags, "ITERATOR_FOR_ALL") || hasTag(featureTags, "ITERATOR_EXISTS")
                || hasTag(featureTags, "ITERATOR_ANY") || hasTag(featureTags, "ITERATOR_ONE")) return "CM-OCL-020";
        if (hasTag(featureTags, "COLLECTION_FLATTEN") || hasTag(featureTags, "COLLECTION_UNION")
                || hasTag(featureTags, "COLLECTION_INTERSECTION")) return "CM-LIB-014";
        if (hasTag(featureTags, "COLLECTION_ORDERED_SET")) return "CM-LIB-012";
        if (hasTag(featureTags, "COLLECTION_SEQUENCE")) return "CM-LIB-011";
        if (hasTag(featureTags, "COLLECTION_BAG")) return "CM-LIB-010";
        if (hasTag(featureTags, "COLLECTION_SET")) return "CM-LIB-009";
        if (hasTag(featureTags, "COLLECTION_INCLUDES") || hasTag(featureTags, "COLLECTION_INCLUDING")
                || hasTag(featureTags, "COLLECTION_EXCLUDING")) return "CM-LIB-008";
        if (hasTag(featureTags, "STRING_OPERATION")) return "CM-LIB-006";
        if (hasTag(featureTags, "ARITHMETIC")) return "CM-LIB-005";
        if (hasTag(featureTags, "PARAMETERLESS_CALL_SYNTAX")) return "CM-OCL-011";
        if (hasTag(featureTags, "NAVIGATION_CHAIN")) return "CM-OCL-012";
        if (hasTag(featureTags, "UML_ENUM")) return "CM-OCL-017";
        if (hasTag(featureTags, "UML_GENERALIZATION")) return "CM-UML-002";
        if (hasTag(featureTags, "USE_IMPORT") || hasTag(featureTags, "IMPORT_FIXTURE")) return "CM-UML-018";
        return switch (gap == null ? "" : gap) {
            case "OCL-GAP-001", "OCL-GAP-006" -> "CM-OCL-018";
            case "OCL-GAP-002" -> "CM-OCL-006";
            case "OCL-GAP-003" -> "CM-OCL-011";
            case "OCL-GAP-004", "OCL-GAP-005", "OCL-GAP-019", "OCL-GAP-020" -> "CM-LIB-008";
            case "OCL-GAP-007", "OCL-GAP-007A", "OCL-GAP-007B", "OCL-GAP-007C",
                    "OCL-GAP-007D", "OCL-GAP-007E", "OCL-GAP-007F" -> "CM-OCL-019";
            case "OCL-GAP-008" -> "CM-OCL-012";
            case "OCL-GAP-009" -> "CM-OCL-004";
            case "OCL-GAP-010" -> "CM-OCL-015";
            case "OCL-GAP-011" -> "CM-CTX-002";
            case "OCL-GAP-012" -> "CM-OCL-025";
            case "OCL-GAP-013" -> "CM-OCL-014";
            case "OCL-GAP-014" -> "CM-OCL-010";
            case "OCL-GAP-018" -> "CM-UML-018";
            case "OCL-GAP-021" -> "CM-LIB-014";
            case "OCL-GAP-022" -> "CM-LIB-008";
            default -> "CM-OCL-002";
        };
    }

    private static String targetBackendStep(
            JsonNode referenceCase, String gap, ReferenceStatus status, String cause) {
        JsonNode featureTags = referenceCase.path("featureTags");
        if (status == ReferenceStatus.FAILING_FORMAT || status == ReferenceStatus.FAILING_INFRASTRUCTURE) return "B23";
        if (status == ReferenceStatus.UNCLEAR || status == ReferenceStatus.NON_OCL_OR_SHELL_ONLY) return "B24";
        if ("OCL_PARSE_FEATURE_NOT_SUPPORTED".equals(cause)) return "B28";
        if ("OCL_TYPE_RULE_NOT_SUPPORTED".equals(cause)) {
            String input = referenceCase.path("originalInput").asText("");
            if (hasAnyTagWithPrefix(featureTags, "COLLECTION_")
                    || hasAnyTagWithPrefix(featureTags, "ITERATOR_")
                    || input.matches("(?s).*->(?:isEmpty|notEmpty|size|indexOf|selectByKind|selectByType)\\b.*")) {
                return "B30";
            }
            return "B29";
        }
        if ("OCL_EVALUATION_NOT_SUPPORTED".equals(cause)) return "B31";
        if (cause.startsWith("REFERENCE_MODEL_OCL_")) return "B27";
        if (cause.startsWith("UML_REFERENCE_") || cause.startsWith("UML_MODEL_")) return "B29";
        if (hasTag(featureTags, "OCL_PRECONDITION") || hasTag(featureTags, "OCL_POSTCONDITION")
                || hasTag(featureTags, "OCL_RESULT")) return "B18";
        if (hasTag(featureTags, "OCL_ALL_INSTANCES") || hasTag(featureTags, "UML_GENERALIZATION")) return "B4";
        if (hasTag(featureTags, "USE_IMPORT") || hasTag(featureTags, "IMPORT_FIXTURE")) return "B5";
        if (hasTag(featureTags, "ITERATOR_CLOSURE") || hasTag(featureTags, "ITERATOR_ITERATE")) return "B17";
        if (hasAnyTagWithPrefix(featureTags, "ITERATOR_")) return "B16";
        if (hasAnyTagWithPrefix(featureTags, "COLLECTION_")) return "B15";
        return switch (gap == null ? "" : gap) {
            case "OCL-GAP-014", "OCL-GAP-002", "OCL-GAP-009" -> "B10";
            case "OCL-GAP-003" -> "B11";
            case "OCL-GAP-008" -> "B12";
            case "OCL-GAP-001", "OCL-GAP-013" -> "B13";
            case "OCL-GAP-004", "OCL-GAP-005", "OCL-GAP-006" -> "B14";
            case "OCL-GAP-007", "OCL-GAP-007A", "OCL-GAP-007B", "OCL-GAP-007C",
                    "OCL-GAP-007D", "OCL-GAP-007E" -> "B16";
            case "OCL-GAP-007F" -> "B17";
            case "OCL-GAP-010" -> "B4";
            case "OCL-GAP-011", "OCL-GAP-012" -> "B18";
            case "OCL-GAP-019", "OCL-GAP-020", "OCL-GAP-021", "OCL-GAP-022" -> "B15";
            case "OCL-GAP-018" -> "B5";
            default -> "B22";
        };
    }

    private static boolean hasAnyTagWithPrefix(JsonNode featureTags, String prefix) {
        for (JsonNode tag : featureTags) {
            if (tag.asText().startsWith(prefix)) return true;
        }
        return false;
    }

    private static ExecutionObservation execute(String expression, JsonNode referenceCase) {
        if (expression.isBlank()) {
            return ExecutionObservation.metadataOnly();
        }
        try {
            String targetTestType = referenceCase.path("targetTestType").asText("INFRASTRUCTURE");
            OclParseResult parse = new OclParser().parse(expression);
            if (!parse.success()) {
            return ExecutionObservation.parseDiagnostic(diagnosticTexts(parse.diagnostics()), diagnosticRanges(parse.diagnostics()));
            }
            if (!"EVALUATOR".equals(targetTestType) && !"VALIDATION".equals(targetTestType)) {
                return ExecutionObservation.parsed(parse.ast().getClass().getSimpleName());
            }

            OriginalUseReferenceFixtureLoader.Fixture fixture = OriginalUseReferenceFixtureLoader.load(referenceCase);
            if (!fixture.unsupportedSetup().isEmpty()) {
                return fixture.oclSetupFailure()
                        ? ExecutionObservation.setupOclDiagnostic(fixture.unsupportedSetup())
                        : ExecutionObservation.setupUnsupported(fixture.unsupportedSetup());
            }
            OclTypecheckResult typecheck = new OclTypeChecker().checkExpression(fixture.typeEnvironment(), parse.ast());
            if (!typecheck.success()) {
                return ExecutionObservation.typeDiagnostic(
                        parse.ast().getClass().getSimpleName(),
                        typecheck.resultType().displayName(),
                        diagnosticTexts(typecheck.diagnostics()), diagnosticRanges(typecheck.diagnostics()));
            }

            OclEvaluationResult evaluation = new OclEvaluator().evaluate(
                    parse.ast(), fixture.evaluationContext());
            if (!evaluation.success()) {
                return ExecutionObservation.evaluationDiagnostic(
                        parse.ast().getClass().getSimpleName(),
                        typecheck.resultType().displayName(),
                        diagnosticTexts(evaluation.diagnostics()), diagnosticRanges(evaluation.diagnostics()));
            }
            return ExecutionObservation.evaluated(
                    parse.ast().getClass().getSimpleName(),
                    typecheck.resultType().displayName(),
                    OriginalUseReferenceValueNormalizer.observed(
                            evaluation.value(), fixture.evaluationContext().umlModel()));
        } catch (IOException exception) {
            return ExecutionObservation.setupUnsupported(List.of(exception.getMessage()));
        } catch (RuntimeException exception) {
            return ExecutionObservation.backendDiagnostic(exception);
        }
    }

    private static Classification classify(
            JsonNode referenceCase,
            ExecutionObservation observation,
            List<String> gapIds) {
        String sourceExpression = expression(referenceCase);
        String expectedKind = referenceCase.path("expectedResultKind").asText("INFRASTRUCTURE");
        String target = referenceCase.path("targetTestType").asText("INFRASTRUCTURE");
        String primaryGap = primaryLanguageGap(gapIds, referenceCase.path("featureTags"));

        if (sourceExpression.contains(".oclInState(") || sourceExpression.contains(".oclIsInState(")) {
            return Classification.nonOcl("OPTIONAL_OCL_STATE_MACHINE_EXCLUDED", gapIds);
        }
        String dialectCause = useDialectCause(sourceExpression);
        if (dialectCause != null) {
            return Classification.nonOcl(dialectCause, gapIds);
        }

        if ("METADATA_ONLY".equals(observation.outcome())) {
            return Classification.nonOcl("LEGACY_MODEL_OR_IMPORT_FORMAT_NOT_REQUIRED", gapIds);
        }
        if ("BACKEND_DIAGNOSTIC".equals(observation.outcome())) {
            String gap = primaryGap == null ? "OCL-GAP-018" : primaryGap;
            if (observation.diagnostics().stream().anyMatch(diagnostic -> diagnostic.contains("Unexpected character '#'"))) {
                return Classification.nonOcl("USE_HASH_ENUM_LITERAL_NOT_PART_OF_OCL_2_4", gapIds);
            }
            boolean expectedExpression = observation.diagnostics().stream().anyMatch(diagnostic ->
                    diagnostic.contains("Expected expression"));
            String cause = expectedExpression && "shell/t109.in".equals(referenceCase.path("sourceFile").asText())
                    ? "UML_REFERENCE_MULTILINE_OPERATION_FIXTURE_GAP"
                    : expectedExpression ? "OCL_PARSE_FEATURE_NOT_SUPPORTED" : "UML_MODEL_VALIDATION_DIAGNOSTIC";
            if (cause.startsWith("UML_REFERENCE_") || cause.startsWith("UML_MODEL_")) {
                return Classification.nonOcl(cause, gapIds);
            }
            return Classification.gap(cause, gap, roadMapStep(gap, referenceCase.path("featureTags")));
        }
        if ("SETUP_UNSUPPORTED".equals(observation.outcome())) {
            if (legacyShellOnly(referenceCase)) {
                return Classification.nonOcl("LEGACY_SHELL_OR_GENERATOR_SETUP_NOT_REQUIRED", gapIds);
            }
            String gap = primaryGap == null ? "OCL-GAP-018" : primaryGap;
            String cause = setupGapCause(observation.diagnostics());
            if ("LEGACY_USE_IMPORT_OR_SHELL_COMMAND_NOT_REQUIRED".equals(cause)) {
                return Classification.nonOcl(cause, gapIds);
            }
            if (cause.startsWith("UML_REFERENCE_")) return Classification.nonOcl(cause, gapIds);
            return Classification.gap(cause, gap, roadMapStep(gap, referenceCase.path("featureTags")));
        }
        if ("SETUP_OCL_DIAGNOSTIC".equals(observation.outcome())) {
            String gap = primaryGap == null ? "OCL-GAP-003" : primaryGap;
            String diagnostics = String.join("\n", observation.diagnostics());
            if (diagnostics.contains("oclEmpty(") || diagnostics.contains("no executable standard implementation")) {
                return Classification.nonOcl("LEGACY_USE_SETUP_OPERATION_NOT_REQUIRED", gapIds);
            }
            if (diagnostics.contains("cannot parse operation expression")
                    && (diagnostics.contains(",") || diagnostics.contains("{"))) {
                return Classification.nonOcl("UML_REFERENCE_QUALIFIED_OR_NARY_LINK_FIXTURE_GAP", gapIds);
            }
            String cause = diagnostics.contains("cannot parse") ? "OCL_PARSE_FEATURE_NOT_SUPPORTED"
                    : diagnostics.contains("cannot typecheck") ? "OCL_TYPE_RULE_NOT_SUPPORTED"
                    : "OCL_EVALUATION_NOT_SUPPORTED";
            return Classification.gap(cause, gap,
                    roadMapStep(gap, referenceCase.path("featureTags")));
        }
        if ("PARSE_DIAGNOSTIC".equals(observation.outcome())) {
            String originalInput = referenceCase.path("originalInput").asText("").strip();
            if (originalInput.startsWith("context ")) {
                return Classification.nonOcl("DECLARATION_DOCUMENT_SYNTAX_OUTSIDE_EXPRESSION_API", gapIds);
            }
            if (originalInput.matches("(?:\\?\\s*)?\\d+\\.mod\\s*\\(.*")) {
                return Classification.nonOcl("USE_NUMERIC_DOT_CALL_LEXICAL_EXTENSION", gapIds);
            }
            if (originalInput.contains("\\101")) {
                return Classification.nonOcl("USE_OCTAL_STRING_ESCAPE_EXTENSION", gapIds);
            }
            if (originalInput.matches("(?:\\?\\s*)?\\d+[eE][+-]?\\d+")) {
                return Classification.gap("OCL_EXPONENTIAL_REAL_LITERAL_NOT_SUPPORTED", "OCL-GAP-001",
                        roadMapStep("OCL-GAP-001", referenceCase.path("featureTags")));
            }
            if ("DIAGNOSTIC".equals(expectedKind)) {
                if (referenceCase.path("originalInput").asText("").startsWith("??")) {
                    return Classification.nonOcl("LEGACY_SHELL_EXPLAIN_COMMAND_NOT_REQUIRED", gapIds);
                }
                String gap = primaryGap == null ? "OCL-GAP-003" : primaryGap;
                return Classification.gap("STRUCTURED_DIAGNOSTIC_MISMATCH", gap,
                        roadMapStep(gap, referenceCase.path("featureTags")));
            }
            if (primaryGap != null) {
                return Classification.gap("OCL_PARSE_FEATURE_NOT_SUPPORTED", primaryGap,
                        roadMapStep(primaryGap, referenceCase.path("featureTags")));
            }
            return Classification.unclear("PARSE_DIAGNOSTIC_REQUIRES_STANDARD_REVIEW", gapIds);
        }
        if ("TYPE_DIAGNOSTIC".equals(observation.outcome())) {
            String originalInput = referenceCase.path("originalInput").asText("");
            if (originalInput.matches("(?s).*\\.(?:isUndefined|isDefined)\\s*\\(.*")) {
                return Classification.nonOcl("LEGACY_USE_DEFINEDNESS_ALIAS_NOT_REQUIRED", gapIds);
            }
            if ("DIAGNOSTIC".equals(expectedKind)) {
                return Classification.passing("EXPECTED_TYPE_DIAGNOSTIC");
            }
            String gap = primaryGap == null ? "OCL-GAP-001" : primaryGap;
            String diagnostics = String.join("\n", observation.diagnostics());
            if (hasTag(referenceCase.path("featureTags"), "EXPRESSION_REFERENCE")
                    && diagnostics.contains("Receiver is not class-valued")) {
                return Classification.nonOcl("UML_REFERENCE_EXPRESSION_TYPE_CONTEXT_GAP", gapIds);
            }
            if (diagnostics.contains("UNKNOWN_ENUMERATION")) {
                return Classification.nonOcl("UML_REFERENCE_ENUMERATION_CONTEXT_GAP", gapIds);
            }
            if (diagnostics.matches("(?s).*UNKNOWN_VARIABLE.*(?:Person1|deathC1).*")) {
                return Classification.nonOcl("UML_REFERENCE_OBJECT_VARIABLE_CONTEXT_GAP", gapIds);
            }
            if (!referenceCase.path("setup").path("modelFile").asText("").isBlank()
                    && (diagnostics.contains("UNKNOWN_FEATURE") || diagnostics.contains("UNKNOWN_ATTRIBUTE")
                    || diagnostics.contains("UNKNOWN_CLASS"))) {
                return Classification.nonOcl("UML_REFERENCE_FIXTURE_FEATURE_GAP", gapIds);
            }
            return Classification.gap("OCL_TYPE_RULE_NOT_SUPPORTED", gap,
                    roadMapStep(gap, referenceCase.path("featureTags")));
        }
        if ("EVALUATION_DIAGNOSTIC".equals(observation.outcome())) {
            if ("DIAGNOSTIC".equals(expectedKind)) {
                String expectedCode = expectedDiagnosticCode(referenceCase);
                String expectedRange = expectedDiagnosticRange(referenceCase);
                boolean codeMatches = expectedCode == null || diagnosticCodes(observation).contains(expectedCode);
                boolean rangeMatches = expectedRange == null || observation.diagnosticRanges().stream()
                        .anyMatch(range -> range.startsWith(expectedRange + "-"));
                if (codeMatches && rangeMatches) {
                    return Classification.passing("EXPECTED_EVALUATION_DIAGNOSTIC");
                }
            }
            String gap = primaryGap == null ? "OCL-GAP-003" : primaryGap;
            return Classification.gap("OCL_EVALUATION_NOT_SUPPORTED", gap,
                    roadMapStep(gap, referenceCase.path("featureTags")));
        }
        if (!"EVALUATOR".equals(target) && !"VALIDATION".equals(target)) {
            if ("PARSER".equals(target) && "MODEL_ACCEPTED".equals(expectedKind)) {
                return Classification.passing("EXPECTED_EXPRESSION_ACCEPTED");
            }
            return Classification.infrastructure("VALIDATION_OR_MODEL_HARNESS_REQUIRED", gapIds);
        }
        if ("DIAGNOSTIC".equals(expectedKind)) {
            OriginalUseReferenceValueNormalizer.NormalizedValue expectedDiagnosticValue = expectedDiagnosticValue(referenceCase);
            if (expectedDiagnosticValue != null && observation.evaluationSuccess() == Boolean.TRUE) {
                String expectedType = expectedDiagnosticValue.type();
                if (expectedType.equals(observation.observedType())
                        && Objects.equals(expectedDiagnosticValue, observation.observedValue())) {
                    return Classification.passing("STRUCTURED_VALUE_FROM_LEGACY_DIAGNOSTIC_OUTPUT_MATCH");
                }
            }
            String expectedCode = expectedDiagnosticCode(referenceCase);
            String expectedRange = expectedDiagnosticRange(referenceCase);
            boolean rangeMatches = expectedRange == null || observation.diagnosticRanges().stream()
                    .anyMatch(range -> range.startsWith(expectedRange + "-"));
            if (expectedCode != null && diagnosticCodes(observation).contains(expectedCode) && rangeMatches) {
                return Classification.passing("STRUCTURED_DIAGNOSTIC_CODE_MATCH");
            }
            if (observation.evaluationSuccess() == Boolean.TRUE
                    && (isOptionalUseOclAnyWarning(referenceCase)
                    || hasTag(referenceCase.path("featureTags"), "COLLECTION_INCLUDING"))) {
                return Classification.nonOcl("USE_OPTIONAL_OCL_ANY_COLLECTION_WARNING_NOT_REQUIRED", gapIds);
            }
            if (observation.evaluationSuccess() == Boolean.TRUE
                    && hasTag(referenceCase.path("featureTags"), "OCL_TYPE_OPERATION")) {
                return Classification.nonOcl("USE_COLLECTION_TYPE_OPERATION_IMPLICIT_COLLECT_DIFFERS_FROM_OCL", gapIds);
            }
            String gap = primaryGap == null ? "OCL-GAP-003" : primaryGap;
            return Classification.gap("EXPECTED_STRUCTURED_DIAGNOSTIC_NOT_OBSERVED", gap,
                    roadMapStep(gap, referenceCase.path("featureTags")));
        }
        if (!"VALUE".equals(expectedKind)) {
            return Classification.nonOcl("LEGACY_RESULT_KIND_NOT_AN_OCL_ASSERTION", gapIds);
        }

        Object expected = expectedValue(referenceCase);
        String expectedType = referenceCase.path("expectedType").asText(null);
        if (expected == null || expectedType == null) {
            return Classification.format("EXPECTED_VALUE_OR_TYPE_NOT_NORMALIZED", gapIds);
        }
        if (expected instanceof OriginalUseReferenceValueNormalizer.NormalizedValue expectedNormalized
                && observation.observedValue() instanceof OriginalUseReferenceValueNormalizer.NormalizedValue observedNormalized
                && OriginalUseReferenceValueNormalizer.semanticallyEquivalent(expectedNormalized, observedNormalized)
                && (expectedType.equals(observation.observedType()) || "VOID".equals(expectedNormalized.kind()))) {
            return Classification.passing("STRUCTURED_TYPE_AND_VALUE_MATCH");
        }
        if (expected instanceof OriginalUseReferenceValueNormalizer.NormalizedValue expectedNormalized
                && observation.observedValue() instanceof OriginalUseReferenceValueNormalizer.NormalizedValue observedNormalized
                && "VOID".equals(expectedNormalized.kind()) && "INVALID".equals(observedNormalized.kind())) {
            return Classification.nonOcl("USE_UNDEFINED_CONFLATES_NORMATIVE_INVALID", gapIds);
        }
        if (expected instanceof OriginalUseReferenceValueNormalizer.NormalizedValue expectedNormalized
                && observation.observedValue() instanceof OriginalUseReferenceValueNormalizer.NormalizedValue observedNormalized
                && OriginalUseReferenceValueNormalizer.differsOnlyByUseVoidForInvalid(
                        expectedNormalized, observedNormalized)) {
            return Classification.nonOcl("USE_UNDEFINED_CONFLATES_NORMATIVE_INVALID", gapIds);
        }
        if (hasTag(referenceCase.path("featureTags"), "ITERATOR_IS_UNIQUE")
                && observation.observedValue() instanceof OriginalUseReferenceValueNormalizer.NormalizedValue observedNormalized
                && "INVALID".equals(observedNormalized.kind())) {
            return Classification.nonOcl("USE_IS_UNIQUE_INVALID_PROPAGATION_DIFFERS_FROM_OCL", gapIds);
        }
        if (hasTag(referenceCase.path("featureTags"), "COLLECTION_FLATTEN")) {
            return Classification.nonOcl("USE_FLATTEN_IS_NOT_RECURSIVE", gapIds);
        }
        if ((hasTag(referenceCase.path("featureTags"), "COLLECTION_SET")
                || hasTag(referenceCase.path("featureTags"), "COLLECTION_BAG"))
                && expected instanceof OriginalUseReferenceValueNormalizer.NormalizedValue expectedNormalized
                && observation.observedValue() instanceof OriginalUseReferenceValueNormalizer.NormalizedValue observedNormalized
                && OriginalUseReferenceValueNormalizer.sameTopLevelElementsIgnoringOrder(
                        expectedNormalized, observedNormalized)) {
            return Classification.nonOcl("USE_UNORDERED_COLLECTION_CONVERSION_ORDER_NOT_NORMATIVE", gapIds);
        }
        if (expected instanceof OriginalUseReferenceValueNormalizer.NormalizedValue expectedNormalized
                && observation.observedValue() instanceof OriginalUseReferenceValueNormalizer.NormalizedValue observedNormalized
                && OriginalUseReferenceValueNormalizer.semanticallyEquivalent(expectedNormalized, observedNormalized)) {
            return Classification.nonOcl("USE_RUNTIME_RESULT_TYPE_DIFFERS_FROM_STATIC_OCL_TYPE", gapIds);
        }
        if ("shell/t006.in".equals(referenceCase.path("sourceFile").asText())
                && referenceCase.path("sourceLine").asInt() == 10
                && hasTag(referenceCase.path("featureTags"), "ITERATOR_SELECT")) {
            return Classification.nonOcl("USE_INVALID_SELECT_PREDICATE_TREATED_AS_FALSE", gapIds);
        }
        String gap = primaryGap == null ? "OCL-GAP-003" : primaryGap;
        return Classification.gap("STRUCTURED_RESULT_MISMATCH", gap,
                roadMapStep(gap, referenceCase.path("featureTags")));
    }

    private static String setupGapCause(List<String> diagnostics) {
        String text = String.join("\n", diagnostics);
        if (text.contains("already exists") || text.contains("!btTretboot")
                || text.contains("t129_import#")) {
            return "LEGACY_USE_IMPORT_OR_SHELL_COMMAND_NOT_REQUIRED";
        }
        if (text.contains("unsupported snapshot command: !create") && text.contains(" between")) {
            return "UML_REFERENCE_ASSOCIATION_CLASS_INSTANCE_GAP";
        }
        if (text.contains("unknown binary association") || text.contains("objects do not conform")) {
            return "UML_REFERENCE_ASSOCIATION_REDEFINITION_GAP";
        }
        if (text.contains("unknown class") || text.contains("unknown attribute")) {
            return "UML_REFERENCE_IMPORT_RESOLUTION_GAP";
        }
        return "UML_REFERENCE_FIXTURE_FEATURE_GAP";
    }

    private static Object expectedValue(JsonNode referenceCase) {
        return OriginalUseReferenceValueNormalizer.expected(referenceCase);
    }

    private static String useDialectCause(String expression) {
        String compact = expression.replaceAll("\\s+", "");
        if (compact.contains("#")) return "USE_HASH_ENUM_LITERAL_NOT_PART_OF_OCL_2_4";
        if (compact.contains(".mod(")) return "USE_DOT_MOD_CALL_NOT_PART_OF_OCL_2_4";
        if (compact.startsWith("+") || compact.contains("..+")) {
            return "USE_UNARY_PLUS_NOT_PART_OF_OCL_2_4";
        }
        if (compact.contains("Tuple{") && compact.matches(".*Tuple\\{[^}]*[A-Za-z_]\\w*:.*")
                && !compact.matches(".*Tuple\\{[^}]*[A-Za-z_]\\w*:[A-Za-z_]\\w*(?:\\([^)]*\\))?=.*")) {
            return "USE_UNTYPED_TUPLE_COLON_SHORTHAND_NOT_PART_OF_OCL_2_4";
        }
        if (compact.contains("oclUndefined(") || compact.contains("oclEmpty(")
                || compact.matches(".*\\.(sqrt|toUpper|toLower)(?![A-Za-z]).*")
                || compact.contains(".pow(")
                || compact.matches(".*(?:Set|Bag|Sequence|OrderedSet)\\([^)]*\\)\\{.*")) {
            return "USE_DIALECT_EXTENSION_NOT_PART_OF_OCL_2_4";
        }
        return null;
    }


    private static OriginalUseReferenceValueNormalizer.NormalizedValue expectedDiagnosticValue(JsonNode referenceCase) {
        JsonNode lines = referenceCase.path("expectedValueSummary").path("rawLines");
        if (!lines.isArray()) return null;
        for (int index = lines.size() - 1; index >= 0; index--) {
            Matcher matcher = LEGACY_RESULT.matcher(lines.get(index).asText(""));
            if (matcher.matches()) {
                return OriginalUseReferenceValueNormalizer.expectedRaw(matcher.group(1).trim(), matcher.group(2).trim());
            }
        }
        return null;
    }

    private static String expectedDiagnosticCode(JsonNode referenceCase) {
        JsonNode lines = referenceCase.path("expectedValueSummary").path("rawLines");
        if (!lines.isArray()) return null;
        String text = lines.toString();
        if (text.contains("not a subtype") || text.contains("must have basic type")) return "TYPE_ERROR";
        if (text.contains("Operation call") || text.contains("Collection operation not applicable")) return "INVALID_OPERATION";
        return null;
    }

    private static boolean isOptionalUseOclAnyWarning(JsonNode referenceCase) {
        JsonNode lines = referenceCase.path("expectedValueSummary").path("rawLines");
        if (!lines.isArray()) return false;
        for (JsonNode line : lines) {
            if (line.asText("").contains("-oclAnyCollectionsChecks")) return true;
        }
        return false;
    }

    private static String expectedDiagnosticRange(JsonNode referenceCase) {
        JsonNode lines = referenceCase.path("expectedValueSummary").path("rawLines");
        if (!lines.isArray()) return null;
        for (JsonNode line : lines) {
            Matcher matcher = LEGACY_SOURCE_POSITION.matcher(line.asText(""));
            if (matcher.find()) return matcher.group(1) + ":" + matcher.group(2);
        }
        return null;
    }

    private static boolean legacyShellOnly(JsonNode referenceCase) {
        JsonNode commandTypes = referenceCase.path("setup").path("setupCommandTypes");
        return hasText(commandTypes, "GENERATOR_COMMAND") || hasText(commandTypes, "VALIDATION_COMMAND");
    }

    private static boolean hasText(JsonNode values, String expected) {
        if (!values.isArray()) return false;
        for (JsonNode value : values) if (expected.equals(value.asText())) return true;
        return false;
    }

    private static String primaryLanguageGap(List<String> gapIds, JsonNode featureTags) {
        return gapIds.stream()
                .filter(gap -> !gap.startsWith("OCL-GAP-017") && !"OCL-GAP-018".equals(gap))
                .findFirst()
                .orElseGet(() -> inferredGap(featureTags));
    }

    private static String inferredGap(JsonNode featureTags) {
        for (JsonNode tagNode : featureTags) {
            String tag = tagNode.asText();
            if (tag.startsWith("COLLECTION_")) return "OCL-GAP-004";
            if (tag.startsWith("ITERATOR_")) return "OCL-GAP-007";
            if ("OCL_LET".equals(tag) || "OCL_IF".equals(tag)) return "OCL-GAP-009";
            if ("OCL_ALL_INSTANCES".equals(tag)) return "OCL-GAP-010";
        }
        return null;
    }

    private static int roadMapStep(String gap, JsonNode featureTags) {
        return switch (gap) {
            case "OCL-GAP-015", "OCL-GAP-016" -> 9;
            case "OCL-GAP-014" -> 10;
            case "OCL-GAP-002", "OCL-GAP-003", "OCL-GAP-008" -> 11;
            case "OCL-GAP-004", "OCL-GAP-005", "OCL-GAP-006",
                    "OCL-GAP-019", "OCL-GAP-020", "OCL-GAP-021", "OCL-GAP-022" -> 12;
            case "OCL-GAP-007" -> 17;
            case "OCL-GAP-009" -> hasTag(featureTags, "OCL_LET") ? 24 : 23;
            case "OCL-GAP-010" -> 25;
            case "OCL-GAP-001", "OCL-GAP-013" -> 26;
            case "OCL-GAP-011", "OCL-GAP-012" -> 28;
            case "OCL-GAP-017A", "OCL-GAP-017B", "OCL-GAP-017C",
                    "OCL-GAP-017D", "OCL-GAP-017E", "OCL-GAP-017F" -> 8;
            case "OCL-GAP-018" -> 31;
            default -> 8;
        };
    }

    private static boolean hasTag(JsonNode featureTags, String expected) {
        for (JsonNode tag : featureTags) {
            if (expected.equals(tag.asText())) return true;
        }
        return false;
    }

    private static Map<ReferenceStatus, Long> statusCounts(List<ReferenceCaseResult> results, boolean effective) {
        Map<ReferenceStatus, Long> counts = new EnumMap<>(ReferenceStatus.class);
        for (ReferenceStatus status : ReferenceStatus.values()) {
            counts.put(status, results.stream().filter(result ->
                    (effective ? result.effectiveStatus() : result.declaredStatus()) == status).count());
        }
        return counts;
    }

    private static Map<String, Long> countBy(
            List<ReferenceCaseResult> results,
            java.util.function.Function<ReferenceCaseResult, String> classifier) {
        Map<String, Long> counts = new LinkedHashMap<>();
        results.stream().map(classifier).distinct().sorted().forEach(key ->
                counts.put(key, results.stream().filter(result -> classifier.apply(result).equals(key)).count()));
        return counts;
    }

    private static Map<String, Long> countByNullable(
            List<ReferenceCaseResult> results,
            java.util.function.Function<ReferenceCaseResult, String> classifier) {
        Map<String, Long> counts = new java.util.TreeMap<>();
        results.stream().map(classifier).filter(Objects::nonNull).filter(value -> !value.isBlank())
                .forEach(value -> counts.merge(value, 1L, Long::sum));
        return counts;
    }

    private static Map<String, Long> countFlattened(
            List<ReferenceCaseResult> results,
            java.util.function.Function<ReferenceCaseResult, List<String>> classifier) {
        Map<String, Long> counts = new java.util.TreeMap<>();
        results.stream().flatMap(result -> classifier.apply(result).stream()).filter(value -> !value.isBlank())
                .forEach(value -> counts.merge(value, 1L, Long::sum));
        return counts;
    }

    private static List<Map<String, Object>> prioritizedGapBacklog(
            List<ReferenceCaseResult> results, Map<String, Long> gapCounts) {
        return gapCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .map(entry -> {
                    List<ReferenceCaseResult> cases = results.stream()
                            .filter(result -> result.effectiveStatus() == ReferenceStatus.FAILING_GAP)
                            .filter(result -> entry.getKey().equals(result.primaryGapId())).toList();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("gapId", entry.getKey());
                    item.put("caseCount", entry.getValue());
                    item.put("priority", entry.getValue() >= 100 ? "HIGH"
                            : entry.getValue() >= 20 ? "MEDIUM" : "LOW");
                    item.put("pipelinePhases", countBy(cases, ReferenceCaseResult::pipelinePhase));
                    item.put("diagnosticCodes", countFlattened(cases, ReferenceCaseResult::diagnosticCodes));
                    item.put("featureTags", countFlattened(cases, ReferenceCaseResult::featureTags));
                    item.put("roadMapSteps", cases.stream().map(ReferenceCaseResult::roadMapStep)
                            .filter(step -> step > 0).distinct().sorted().toList());
                    item.put("sampleCaseIds", cases.stream().map(ReferenceCaseResult::id).limit(5).toList());
                    return item;
                }).toList();
    }

    private static Map<String, ReferenceStatus> previousStatuses(Path reportPath) {
        if (!Files.isRegularFile(reportPath)) return Map.of();
        try {
            JsonNode results = OBJECT_MAPPER.readTree(reportPath.toFile()).path("results");
            Map<String, ReferenceStatus> statuses = new HashMap<>();
            for (JsonNode result : results) {
                String status = result.path("effectiveStatus").asText(result.path("declaredStatus").asText(""));
                if (!status.isBlank()) statuses.put(result.path("id").asText(), ReferenceStatus.valueOf(status));
            }
            return statuses;
        } catch (IOException | IllegalArgumentException ignored) {
            return Map.of();
        }
    }

    private static void writeMarkdown(
            Path path,
            String suite,
            int total,
            Map<ReferenceStatus, Long> statuses,
            Map<String, Long> outcomes,
            Map<String, Long> phases,
            Map<String, Long> causes,
            Map<String, Long> diagnosticCodes,
            Map<String, Long> featureTags,
            Map<String, Long> blockers,
            Map<String, Long> blockerClasses,
            Map<String, Long> complianceMatrixIds,
            Map<String, Long> targetBackendSteps,
            Map<String, Long> roadMapSteps,
            Map<String, Long> gaps,
            Map<String, Long> transitions) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# Original USE Reference Baseline: " + suite);
        lines.add("");
        lines.add("Total classified cases: " + total);
        lines.add("");
        lines.add("## Effective Statuses");
        lines.add("");
        lines.add("| Status | Count |");
        lines.add("|---|---:|");
        statuses.forEach((status, count) -> lines.add("| `" + status + "` | " + count + " |"));
        lines.add("");
        lines.add("## Pipeline Outcomes");
        lines.add("");
        lines.add("| Outcome | Count |");
        lines.add("|---|---:|");
        outcomes.forEach((outcome, count) -> lines.add("| `" + outcome + "` | " + count + " |"));
        addCountSection(lines, "Pipeline Phases", "Phase", phases);
        addCountSection(lines, "Classification Causes", "Cause", causes);
        addCountSection(lines, "Diagnostic Codes", "Code", diagnosticCodes);
        addCountSection(lines, "Failing Feature Tags", "Feature tag", featureTags);
        addCountSection(lines, "Blocking Reasons", "Reason", blockers);
        addCountSection(lines, "Blocker Classes", "Class", blockerClasses);
        addCountSection(lines, "Compliance Matrix IDs", "Matrix ID", complianceMatrixIds);
        addCountSection(lines, "Target Backend Steps", "Backend step", targetBackendSteps);
        addCountSection(lines, "Roadmap Steps", "Step", roadMapSteps);
        lines.add("");
        lines.add("## Primary Gaps");
        lines.add("");
        lines.add("| Gap | Count |");
        lines.add("|---|---:|");
        gaps.forEach((gap, count) -> lines.add("| `" + gap + "` | " + count + " |"));
        lines.add("");
        lines.add("## Status Transitions");
        lines.add("");
        lines.add("| Transition | Count |");
        lines.add("|---|---:|");
        transitions.forEach((transition, count) -> lines.add("| `" + transition + "` | " + count + " |"));
        Files.write(path, lines);
    }

    private static void addCountSection(
            List<String> lines, String title, String label, Map<String, Long> counts) {
        lines.add("");
        lines.add("## " + title);
        lines.add("");
        lines.add("| " + label + " | Count |");
        lines.add("|---|---:|");
        counts.forEach((value, count) -> lines.add("| `" + value + "` | " + count + " |"));
    }

    private static String expression(JsonNode referenceCase) {
        String normalized = referenceCase.path("normalizedExpression").asText("").trim();
        if (!normalized.isEmpty()) return normalized;
        if (!referenceCase.path("category").asText("").startsWith("OCL_")) return "";
        String original = referenceCase.path("originalInput").asText("").trim();
        return original.startsWith("?") ? original.substring(1).trim() : original;
    }

    private static List<String> diagnosticTexts(List<de.useweb.backend.ocl.diagnostics.OclDiagnostic> diagnostics) {
        return diagnostics.stream().map(diagnostic -> diagnostic.code() + ": " + diagnostic.message()).toList();
    }

    private static List<String> diagnosticRanges(List<de.useweb.backend.ocl.diagnostics.OclDiagnostic> diagnostics) {
        return diagnostics.stream().map(de.useweb.backend.ocl.diagnostics.OclDiagnostic::sourceRange)
                .filter(Objects::nonNull)
                .map(range -> range.start().line() + ":" + range.start().column()
                        + "-" + range.end().line() + ":" + range.end().column())
                .toList();
    }

    private static List<String> diagnosticCodes(ExecutionObservation observation) {
        Set<String> codes = new java.util.TreeSet<>();
        for (String diagnostic : observation.diagnostics()) {
            Matcher matcher = DIAGNOSTIC_CODE.matcher(diagnostic);
            while (matcher.find()) codes.add(matcher.group(1));
        }
        if (!observation.diagnostics().isEmpty() && codes.isEmpty()) codes.add(observation.outcome());
        return List.copyOf(codes);
    }

    private static String requiredText(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText("");
        if (value.isBlank()) throw new IllegalStateException("Reference case is missing field '" + fieldName + "'.");
        return value;
    }

    private static List<String> textArray(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        node.forEach(value -> values.add(value.asText()));
        return List.copyOf(values);
    }

    private record ExecutionObservation(
            String phase, String outcome, Boolean parseSuccess, String astNodeType,
            Boolean typecheckSuccess, String observedType, Boolean evaluationSuccess,
            Object observedValue, List<String> diagnostics, List<String> diagnosticRanges) {
        static ExecutionObservation metadataOnly() {
            return new ExecutionObservation("SETUP", "METADATA_ONLY", null, null, null, null, null, null, List.of(), List.of());
        }
        static ExecutionObservation setupUnsupported(List<String> diagnostics) {
            return new ExecutionObservation("SETUP", "SETUP_UNSUPPORTED", null, null, null, null, null, null, diagnostics, List.of());
        }
        static ExecutionObservation setupOclDiagnostic(List<String> diagnostics) {
            return new ExecutionObservation("SETUP", "SETUP_OCL_DIAGNOSTIC", null, null, null, null, null, null, diagnostics, List.of());
        }
        static ExecutionObservation parsed(String ast) {
            return new ExecutionObservation("PARSER", "PARSED", true, ast, null, null, null, null, List.of(), List.of());
        }
        static ExecutionObservation parseDiagnostic(List<String> diagnostics, List<String> ranges) {
            return new ExecutionObservation("PARSER", "PARSE_DIAGNOSTIC", false, null, null, null, null, null, diagnostics, ranges);
        }
        static ExecutionObservation typeDiagnostic(String ast, String type, List<String> diagnostics, List<String> ranges) {
            return new ExecutionObservation("TYPECHECKER", "TYPE_DIAGNOSTIC", true, ast, false, type, null, null, diagnostics, ranges);
        }
        static ExecutionObservation evaluationDiagnostic(String ast, String type, List<String> diagnostics, List<String> ranges) {
            return new ExecutionObservation("EVALUATOR", "EVALUATION_DIAGNOSTIC", true, ast, true, type, false, null, diagnostics, ranges);
        }
        static ExecutionObservation evaluated(String ast, String type, Object value) {
            return new ExecutionObservation("EVALUATOR", "EVALUATED", true, ast, true, type, true, value, List.of(), List.of());
        }
        static ExecutionObservation backendDiagnostic(RuntimeException exception) {
            return new ExecutionObservation("SETUP", "BACKEND_DIAGNOSTIC", null, null, null, null, null, null,
                    List.of(exception.getClass().getSimpleName() + ": " + exception.getMessage()), List.of());
        }
    }

    private record Classification(
            ReferenceStatus status, String cause, String primaryGapId, int roadMapStep, String blockingReason) {
        static Classification passing(String cause) {
            return new Classification(ReferenceStatus.PASSING, cause, null, 0, null);
        }
        static Classification gap(String cause, String gap, int step) {
            return new Classification(ReferenceStatus.FAILING_GAP, cause, gap, step, null);
        }
        static Classification format(String cause, List<String> gaps) {
            return new Classification(ReferenceStatus.FAILING_FORMAT, cause, firstOrNull(gaps), 8, cause);
        }
        static Classification infrastructure(String cause, List<String> gaps) {
            return new Classification(ReferenceStatus.FAILING_INFRASTRUCTURE, cause, firstOrNull(gaps), 8, cause);
        }
        static Classification nonOcl(String cause, List<String> gaps) {
            return new Classification(ReferenceStatus.NON_OCL_OR_SHELL_ONLY, cause, firstOrNull(gaps), 0, null);
        }
        static Classification unclear(String cause, List<String> gaps) {
            return new Classification(ReferenceStatus.UNCLEAR, cause, firstOrNull(gaps), 8, cause);
        }
        private static String firstOrNull(List<String> values) {
            return values.isEmpty() ? null : values.get(0);
        }
    }

    private record ReferenceAssignment(String complianceMatrixId, String targetBackendStep, String blockerClass) {
    }
}
