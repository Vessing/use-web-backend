package de.useweb.backend.reference;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

import de.useweb.backend.domain.snapshot.ObjectInstance;
import de.useweb.backend.domain.snapshot.ObjectInstanceId;
import de.useweb.backend.domain.snapshot.ObjectLink;
import de.useweb.backend.domain.snapshot.ObjectLinkEnd;
import de.useweb.backend.domain.snapshot.ObjectLinkId;
import de.useweb.backend.domain.snapshot.ObjectModel;
import de.useweb.backend.domain.snapshot.ObjectModelId;
import de.useweb.backend.domain.snapshot.Slot;
import de.useweb.backend.domain.snapshot.SlotId;
import de.useweb.backend.domain.snapshot.SlotValue;
import de.useweb.backend.domain.uml.Multiplicity;
import de.useweb.backend.domain.uml.UmlAssociation;
import de.useweb.backend.domain.uml.UmlAssociationEnd;
import de.useweb.backend.domain.uml.UmlAssociationEndId;
import de.useweb.backend.domain.uml.UmlAssociationId;
import de.useweb.backend.domain.uml.UmlAttribute;
import de.useweb.backend.domain.uml.UmlAttributeId;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlModelId;
import de.useweb.backend.domain.uml.UmlOperation;
import de.useweb.backend.domain.uml.UmlOperationId;
import de.useweb.backend.domain.uml.UmlParameter;
import de.useweb.backend.domain.uml.UmlParameterId;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.ocl.contract.OperationConstraintKind;
import de.useweb.backend.ocl.contract.OperationContext;
import de.useweb.backend.ocl.contract.OperationContextReference;
import de.useweb.backend.ocl.contract.OperationContract;
import de.useweb.backend.ocl.contract.OperationContractId;
import de.useweb.backend.ocl.contract.OperationContractParser;
import de.useweb.backend.ocl.contract.OperationContractResult;
import de.useweb.backend.ocl.contract.OperationContractService;
import de.useweb.backend.ocl.contract.OperationInvocationId;
import de.useweb.backend.ocl.contract.OperationResultSlot;
import de.useweb.backend.ocl.definition.OclDefinitionService;
import de.useweb.backend.ocl.definition.OclModelDefinitionFactory;
import de.useweb.backend.ocl.evaluation.EvaluationContext;
import de.useweb.backend.ocl.evaluation.OclEvaluationResult;
import de.useweb.backend.ocl.evaluation.OclEvaluator;
import de.useweb.backend.ocl.parser.OclParseResult;
import de.useweb.backend.ocl.parser.OclParser;
import de.useweb.backend.ocl.typecheck.OclType;
import de.useweb.backend.ocl.typecheck.OclTypeChecker;
import de.useweb.backend.ocl.typecheck.OclTypecheckResult;
import de.useweb.backend.ocl.typecheck.TypeEnvironment;
import de.useweb.backend.ocl.value.ObjectValue;
import de.useweb.backend.ocl.value.OclValue;
import de.useweb.backend.ocl.value.BooleanValue;
import de.useweb.backend.ocl.value.IntegerValue;
import de.useweb.backend.ocl.value.RealValue;
import de.useweb.backend.ocl.value.StringValue;

/** Builds isolated test fixtures from copied USE resources without depending on USE code. */
final class OriginalUseReferenceFixtureLoader {

    private static final String RESOURCE_ROOT = "reference/original-use/";
    private static final Pattern CLASS = Pattern.compile("^(?:abstract\\s+)?class\\s+(\\w+)(?:\\s*<\\s*(.+))?$");
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "^(\\w+)\\s*:\\s*([\\w()]+)(?:\\s+init\\s*:\\s*(.+))?$");
    private static final Pattern OPERATION = Pattern.compile(
            "^(\\w+)\\s*\\(([^)]*)\\)\\s*(?::\\s*([\\w()]+))?\\s*(?:=\\s*(.+))?$");
    private static final Pattern IMPERATIVE_OPERATION = Pattern.compile(
            "^(\\w+)\\s*\\(([^)]*)\\)\\s*(?::\\s*([\\w()]+))?\\s+begin(?:\\s+(.*))?$");
    private static final Pattern CONTRACT = Pattern.compile("^(pre|post)(?:\\s+(\\w+))?\\s*:\\s*(.+)$");
    private static final Pattern ASSOCIATION = Pattern.compile("^(?:association|aggregation|composition)\\s+(\\w+)\\s+between.*$");
    private static final Pattern ASSOCIATION_END = Pattern.compile(
            "^(\\w+)\\s*\\[([^]]+)](?:\\s+role\\s+(\\w+))?(.*)$");
    private static final String OBJECT_NAME = "[@A-Za-z_]\\w*";
    private static final Pattern CREATE = Pattern.compile("^!create\\s+(.+?)\\s*:\\s*(\\w+)\\s*$");
    private static final Pattern SET = Pattern.compile("^!set\\s+(.+?)\\s*:=\\s*(.+)$");
    private static final Pattern INSERT = Pattern.compile("^!insert\\s*\\(([^,]+),([^)]*)\\)\\s+into\\s+(\\w+)\\s*$");
    private static final Pattern DELETE = Pattern.compile("^!delete\\s*\\(([^,]+),([^)]*)\\)\\s+from\\s+(\\w+)\\s*$");
    private static final Pattern DESTROY = Pattern.compile("^!destroy\\s+(.+?)\\s*$");
    private static final Pattern LET = Pattern.compile("^!let\\s+(\\w+)(?:\\s*:\\s*([\\w()]+))?\\s*=\\s*(.+)$");
    private static final Pattern ASSIGN_CREATE = Pattern.compile("^!assign\\s+(\\w+)\\s*:=\\s*create\\s+(\\w+)\\s*$");
    private static final Pattern ASSIGN_NEW = Pattern.compile(
            "^!(\\w+)\\s*:=\\s*new\\s+(\\w+)(?:\\s*\\(\\s*'([^']+)'\\s*\\))?\\s*$");
    private static final Pattern VARIABLE_ASSIGN = Pattern.compile("^!(\\w+)\\s*:=\\s*(.+)$");
    private static final Pattern OPENTER = Pattern.compile("^!openter\\s+([^\\s]+)\\s+(\\w+)\\((.*)\\)\\s*$");
    private static final Pattern OPEXIT = Pattern.compile("^!opexit(?:\\s+(.+))?$");
    private static final Pattern NEW = Pattern.compile("^!new\\s+(\\w+)(?:\\s*\\(\\s*'([^']+)'\\s*\\))?\\s*$");
    private static final Pattern OPERATION_CALL = Pattern.compile("^!([^\\s.]+)\\.(\\w+)\\((.*)\\)\\s*$");
    private static final int MAX_REPLAY_COMMANDS = 10_000;
    private static final int MAX_SEQUENCE_COMMANDS = 100;
    private static final int MAX_OPERATION_BODY_COMMANDS = 100;
    private static final int MAX_OBJECTS = 5_000;

    private OriginalUseReferenceFixtureLoader() {
    }

    static Fixture load(JsonNode referenceCase) throws IOException {
        JsonNode setup = referenceCase.path("setup");
        String modelFile = setup.path("modelFile").asText("");
        String replayFile = setup.path("replaySourceFile").asText("");
        int replayThroughLine = setup.path("replayThroughLine").asInt(0);
        String modelSource = modelFile.isBlank() ? "" : read(modelFile);
        String replaySource = replayFile.isBlank() ? "" : read(replayFile);
        return loadFromSources(modelSource, replaySource, replayFile, replayThroughLine);
    }

    static Fixture loadFromSources(String modelSource, String replaySource, String replayFile, int replayThroughLine) {
        ModelBuilder modelBuilder = modelSource == null || modelSource.isBlank()
                ? ModelBuilder.fallback()
                : parseModel(modelSource);
        UmlModel model = modelBuilder.build();
        SnapshotBuilder snapshot = new SnapshotBuilder(model, modelBuilder.contracts(model),
                modelBuilder.imperativeBodies(model), modelBuilder.initialValues(model));
        List<String> unsupported = replaySource == null || replaySource.isBlank()
                ? List.of()
                : snapshot.replay(replaySource, replayFile, replayThroughLine);
        return snapshot.fixture(unsupported);
    }

    private static String read(String relativePath) throws IOException {
        try (InputStream input = OriginalUseReferenceFixtureLoader.class.getClassLoader()
                .getResourceAsStream(RESOURCE_ROOT + relativePath.replace('\\', '/'))) {
            if (input == null) throw new IOException("Reference resource not found: " + relativePath);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ModelBuilder parseModel(String source) {
        ModelBuilder model = new ModelBuilder();
        String section = "";
        ClassSpec currentClass = null;
        OperationSpec currentOperation = null;
        AssociationSpec currentAssociation = null;
        boolean operationBody = false;
        for (String raw : source.replace("\r", "").split("\n")) {
            String line = clean(raw);
            if (line.isBlank()) continue;
            if (operationBody) {
                if ("end".equals(line)) {
                    operationBody = false;
                } else {
                    boolean closesBody = line.endsWith(" end");
                    String command = closesBody ? line.substring(0, line.length() - 4).strip() : line;
                    if (!command.isBlank()) currentOperation.bodyCommands.add(command);
                    if (closesBody) {
                        operationBody = false;
                    }
                }
                continue;
            }
            Matcher classMatcher = CLASS.matcher(line);
            Matcher associationMatcher = ASSOCIATION.matcher(line);
            if (line.startsWith("model ")) {
                model.name = line.substring(6).trim();
            } else if (classMatcher.matches()) {
                currentAssociation = null;
                currentClass = model.addClass(classMatcher.group(1), raw.stripLeading().startsWith("abstract "), classMatcher.group(2));
                currentOperation = null;
                section = "";
            } else if (associationMatcher.matches()) {
                currentClass = null;
                currentOperation = null;
                currentAssociation = model.addAssociation(associationMatcher.group(1));
                section = "association";
            } else if ("attributes".equals(line) && currentClass != null) {
                section = "attributes";
            } else if ("operations".equals(line) && currentClass != null) {
                section = "operations";
            } else if ("end".equals(line)) {
                currentClass = null;
                currentAssociation = null;
                section = "";
            } else if ("attributes".equals(section) && currentClass != null) {
                Matcher attribute = ATTRIBUTE.matcher(line);
                if (attribute.matches()) {
                    currentClass.attributes.put(attribute.group(1), attribute.group(2));
                    if (attribute.group(3) != null) currentClass.initialValues.put(attribute.group(1), attribute.group(3));
                }
            } else if ("operations".equals(section) && currentClass != null) {
                Matcher imperativeOperation = IMPERATIVE_OPERATION.matcher(line);
                Matcher operation = OPERATION.matcher(line);
                Matcher contract = CONTRACT.matcher(line);
                if (currentOperation != null && (line.equals("begin") || line.startsWith("begin "))) {
                    String inlineBody = line.length() == 5 ? null : line.substring(6).strip();
                    if (inlineBody != null && inlineBody.endsWith(" end")) {
                        String command = inlineBody.substring(0, inlineBody.length() - 4).strip();
                        if (!command.isBlank()) currentOperation.bodyCommands.add(command);
                    } else {
                        if (inlineBody != null && !inlineBody.isBlank()) currentOperation.bodyCommands.add(inlineBody);
                        operationBody = true;
                    }
                } else if (imperativeOperation.matches()) {
                    currentOperation = currentClass.addOperation(imperativeOperation.group(1),
                            imperativeOperation.group(2), imperativeOperation.group(3), null);
                    String inlineBody = imperativeOperation.group(4);
                    if (inlineBody != null && inlineBody.endsWith(" end")) {
                        String command = inlineBody.substring(0, inlineBody.length() - 4).strip();
                        if (!command.isBlank()) currentOperation.bodyCommands.add(command);
                    } else {
                        if (inlineBody != null && !inlineBody.isBlank()) currentOperation.bodyCommands.add(inlineBody);
                        operationBody = true;
                    }
                } else if (operation.matches()) {
                    currentOperation = currentClass.addOperation(operation.group(1), operation.group(2),
                            operation.group(3), operation.group(4));
                } else if (contract.matches() && currentOperation != null) {
                    currentOperation.contracts.add(new ContractSpec(contract.group(1), contract.group(2), contract.group(3)));
                }
            } else if (currentAssociation != null) {
                Matcher end = ASSOCIATION_END.matcher(line);
                if (end.matches()) currentAssociation.ends.add(new EndSpec(
                        end.group(1), end.group(2), end.group(3), end.group(4).contains("ordered")));
            }
        }
        return model.classes.isEmpty() ? ModelBuilder.fallback() : model;
    }

    private static String clean(String raw) {
        String line = raw.strip();
        int comment = line.indexOf("--");
        if (comment >= 0) line = line.substring(0, comment).strip();
        if (line.startsWith("#")) return "";
        return line;
    }

    record Fixture(TypeEnvironment typeEnvironment, EvaluationContext evaluationContext,
            List<String> unsupportedSetup, boolean oclSetupFailure,
            List<OperationContractResult> operationResults) {
    }

    private static final class ModelBuilder {
        private String name = "ReferenceModel";
            private final Map<String, ClassSpec> classes = new LinkedHashMap<>();
        private final List<AssociationSpec> associations = new ArrayList<>();

        static ModelBuilder fallback() {
            ModelBuilder builder = new ModelBuilder();
            builder.addClass("ReferenceContext", false, null);
            return builder;
        }

        ClassSpec addClass(String name, boolean abstractClass, String parents) {
            ClassSpec spec = new ClassSpec(name, abstractClass,
                    parents == null ? List.of() : List.of(parents.split("\\s*,\\s*")));
            classes.put(name, spec);
            return spec;
        }

        AssociationSpec addAssociation(String name) {
            AssociationSpec spec = new AssociationSpec(name);
            associations.add(spec);
            return spec;
        }

        UmlModel build() {
            List<UmlClass> umlClasses = classes.values().stream().map(spec -> new UmlClass(
                    classId(spec.name), spec.name,
                    spec.attributes.entrySet().stream().map(entry -> new UmlAttribute(
                            attributeId(spec.name, entry.getKey()), entry.getKey(), type(entry.getValue()))).toList(),
                    spec.operations.stream().map(operation -> new UmlOperation(
                            operationId(spec.name, operation.name), operation.name,
                            type(operation.returnType == null ? "Void" : operation.returnType),
                            operation.parameters.stream().map(parameter -> new UmlParameter(
                                    parameterId(spec.name, operation.name, parameter.name), parameter.name,
                                    type(parameter.type))).toList(), operation.bodyExpression)).toList(), spec.abstractClass,
                    spec.parents.stream().filter(classes::containsKey).map(ModelBuilder::classId).toList())).toList();
            List<UmlAssociation> umlAssociations = associations.stream()
                    .filter(spec -> spec.ends.size() == 2 && spec.ends.stream().allMatch(end -> classes.containsKey(end.className)))
                    .map(spec -> new UmlAssociation(associationId(spec.name), spec.name,
                            List.of(end(spec, spec.ends.get(0), 0), end(spec, spec.ends.get(1), 1))))
                    .toList();
            return new UmlModel(new UmlModelId("reference-model-" + key(name)), name, umlClasses, umlAssociations, List.of());
        }

        List<OperationContract> contracts(UmlModel model) {
            OperationContractParser parser = new OperationContractParser();
            List<OperationContract> parsed = new ArrayList<>();
            for (ClassSpec owner : classes.values()) {
                for (OperationSpec operation : owner.operations) {
                    for (int index = 0; index < operation.contracts.size(); index++) {
                        ContractSpec contract = operation.contracts.get(index);
                        String name = contract.name == null ? contract.kind + (index + 1) : contract.name;
                        String source = "context " + owner.name + "::" + operation.name + "() "
                                + contract.kind + " " + name + ": " + contract.expression;
                        var result = parser.parse(new OperationContractId(
                                "contract-" + key(owner.name) + "-" + key(operation.name) + "-" + index),
                                source, model);
                        if (result.success()) parsed.add(result.contract());
                    }
                }
            }
            return List.copyOf(parsed);
        }

        Map<UmlOperationId, List<String>> imperativeBodies(UmlModel model) {
            Map<UmlOperationId, List<String>> bodies = new LinkedHashMap<>();
            for (ClassSpec ownerSpec : classes.values()) {
                UmlClass owner = model.findClassByName(ownerSpec.name).orElse(null);
                if (owner == null) continue;
                for (OperationSpec operationSpec : ownerSpec.operations) {
                    if (operationSpec.bodyCommands.isEmpty()) continue;
                    owner.operations().stream()
                            .filter(operation -> operation.name().equals(operationSpec.name)
                                    && operation.parameters().size() == operationSpec.parameters.size())
                            .findFirst()
                            .ifPresent(operation -> bodies.put(operation.id(),
                                    List.copyOf(operationSpec.bodyCommands)));
                }
            }
            return Map.copyOf(bodies);
        }

        Map<UmlClassId, Map<String, String>> initialValues(UmlModel model) {
            Map<UmlClassId, Map<String, String>> result = new LinkedHashMap<>();
            for (ClassSpec classSpec : classes.values()) {
                model.findClassByName(classSpec.name).ifPresent(umlClass ->
                        result.put(umlClass.id(), Map.copyOf(classSpec.initialValues)));
            }
            return Map.copyOf(result);
        }

        private static UmlAssociationEnd end(AssociationSpec association, EndSpec spec, int index) {
            String role = spec.role == null ? lowerCamel(spec.className) : spec.role;
            return new UmlAssociationEnd(new UmlAssociationEndId("end-" + key(association.name) + "-" + index),
                    classId(spec.className), role, multiplicity(spec.multiplicity), true,
                    spec.ordered, true, false, false, List.of(), List.of());
        }

        private static UmlType type(String name) {
            return switch (name) {
                case "Integer" -> UmlType.INTEGER;
                case "Real" -> UmlType.REAL;
                case "Boolean" -> UmlType.BOOLEAN;
                case "String" -> UmlType.STRING;
                case "Void" -> UmlType.VOID;
                default -> UmlType.classType(name);
            };
        }

        private static Multiplicity multiplicity(String raw) {
            String value = raw.replaceAll("\\s", "");
            if ("*".equals(value)) return Multiplicity.zeroToMany();
            if (value.endsWith("..*")) return new Multiplicity(Integer.parseInt(value.substring(0, value.length() - 3)), null, true, value);
            if (value.contains("..")) {
                String[] bounds = value.split("\\.\\.", 2);
                return new Multiplicity(Integer.parseInt(bounds[0]), Integer.parseInt(bounds[1]), false, value);
            }
            int exact = Integer.parseInt(value);
            return new Multiplicity(exact, exact, false, value);
        }

        private static UmlClassId classId(String name) { return new UmlClassId("class-" + key(name)); }
        private static UmlAttributeId attributeId(String owner, String name) { return new UmlAttributeId("attr-" + key(owner) + "-" + key(name)); }
        private static UmlAssociationId associationId(String name) { return new UmlAssociationId("assoc-" + key(name)); }
        private static UmlOperationId operationId(String owner, String name) {
            return new UmlOperationId("operation-" + key(owner) + "-" + key(name));
        }
        private static UmlParameterId parameterId(String owner, String operation, String name) {
            return new UmlParameterId("parameter-" + key(owner) + "-" + key(operation) + "-" + key(name));
        }
        private static String key(String value) { return value.replaceAll("[^A-Za-z0-9]+", "-").toLowerCase(Locale.ROOT); }
        private static String lowerCamel(String value) { return value.substring(0, 1).toLowerCase(Locale.ROOT) + value.substring(1); }
    }

    private static final class SnapshotBuilder {
        private final UmlModel model;
        private final Map<String, MutableObject> objects = new LinkedHashMap<>();
        private final List<LinkSpec> links = new ArrayList<>();
        private final Map<String, OclType> shellTypes = new LinkedHashMap<>();
        private final Map<String, OclValue> shellValues = new LinkedHashMap<>();
        private final Map<String, String> shellObjectAliases = new LinkedHashMap<>();
        private final OclDefinitionService definitionService;
        private final List<OperationContract> contracts;
        private final Map<UmlOperationId, List<String>> imperativeBodies;
        private final Map<UmlClassId, Map<String, String>> initialValues;
        private final List<OperationContractResult> operationResults = new ArrayList<>();
        private OperationFrame activeOperation;
        private String replaySource = "<reference>";
        private boolean oclSetupFailure;

        SnapshotBuilder(UmlModel model, List<OperationContract> contracts,
                Map<UmlOperationId, List<String>> imperativeBodies,
                Map<UmlClassId, Map<String, String>> initialValues) {
            this.model = model;
            this.definitionService = new OclDefinitionService(model, new OclModelDefinitionFactory().definitions(model));
            this.contracts = List.copyOf(contracts);
            this.imperativeBodies = Map.copyOf(imperativeBodies);
            this.initialValues = Map.copyOf(initialValues);
        }

        List<String> replay(String source, String sourceFile, int throughLine) {
            replaySource = sourceFile;
            List<String> setupErrors = new ArrayList<>();
            String[] lines = source.replace("\r", "").split("\n");
            int executedCommands = 0;
            for (int index = 0; index < Math.min(throughLine, lines.length); index++) {
                String line = clean(lines[index]);
                if (!line.startsWith("!")) continue;
                List<String> commands = splitCommands(line);
                if (commands.size() > MAX_SEQUENCE_COMMANDS) {
                    setupErrors.add(message(index, "command sequence exceeds limit of " + MAX_SEQUENCE_COMMANDS));
                    break;
                }
                for (String command : commands) {
                    if (++executedCommands > MAX_REPLAY_COMMANDS) {
                        setupErrors.add(message(index, "replay exceeds command limit of " + MAX_REPLAY_COMMANDS));
                        break;
                    }
                    executeCommand(command, index, setupErrors);
                    if (!setupErrors.isEmpty()) break;
                }
                if (!setupErrors.isEmpty()) break;
            }
            return List.copyOf(setupErrors);
        }

        private void executeCommand(String rawCommand, int lineIndex, List<String> errors) {
            String line = rawCommand.startsWith("!") ? rawCommand : "!" + rawCommand;
            Matcher openter = OPENTER.matcher(line);
            Matcher opexit = OPEXIT.matcher(line);
            Matcher newObject = NEW.matcher(line);
            Matcher create = CREATE.matcher(line);
            Matcher set = SET.matcher(line);
            Matcher insert = INSERT.matcher(line);
            Matcher delete = DELETE.matcher(line);
            Matcher destroy = DESTROY.matcher(line);
            Matcher let = LET.matcher(line);
            Matcher assignCreate = ASSIGN_CREATE.matcher(line);
            Matcher assignNew = ASSIGN_NEW.matcher(line);
            Matcher operationCall = OPERATION_CALL.matcher(line);
            Matcher variableAssign = VARIABLE_ASSIGN.matcher(line);
            if (openter.matches()) applyOpenter(openter, lineIndex, errors);
            else if (opexit.matches()) applyOpexit(opexit, lineIndex, errors);
            else if (newObject.matches()) applyNew(newObject, lineIndex, errors);
            else if (create.matches()) applyCreate(create, lineIndex, errors);
            else if (set.matches()) applySet(set, lineIndex, errors);
            else if (insert.matches()) applyInsert(insert, lineIndex, errors);
            else if (delete.matches()) applyDelete(delete, lineIndex, errors);
            else if (destroy.matches()) applyDestroy(destroy, lineIndex, errors);
            else if (let.matches()) applyLet(let, lineIndex, errors);
            else if (assignCreate.matches()) applyAssignCreate(assignCreate, lineIndex, errors);
            else if (assignNew.matches()) applyAssignNew(assignNew, lineIndex, errors);
            else if (operationCall.matches()) applyOperationCall(operationCall, lineIndex, errors);
            else if (variableAssign.matches()) applyVariableAssignment(variableAssign, lineIndex, errors);
            else if (!line.startsWith("!info") && !line.startsWith("!check"))
                errors.add(message(lineIndex, "unsupported snapshot command: " + line));
        }

        private static List<String> splitCommands(String line) {
            List<String> commands = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            boolean quoted = false;
            for (int index = 0; index < line.length(); index++) {
                char character = line.charAt(index);
                if (character == '\'' && (index + 1 >= line.length() || line.charAt(index + 1) != '\'')) quoted = !quoted;
                if (character == ';' && !quoted) {
                    if (!current.toString().isBlank()) commands.add(current.toString().strip());
                    current.setLength(0);
                } else {
                    current.append(character);
                }
            }
            if (!current.toString().isBlank()) commands.add(current.toString().strip());
            return commands;
        }

        private void applyNew(Matcher matcher, int lineIndex, List<String> errors) {
            String className = matcher.group(1);
            if (model.findClassByName(className).isEmpty()) {
                errors.add(message(lineIndex, "unknown class '" + className + "'"));
                return;
            }
            if (objects.size() >= MAX_OBJECTS) {
                errors.add(message(lineIndex, "object limit of " + MAX_OBJECTS + " exceeded"));
                return;
            }
            String name = matcher.group(2);
            if (name == null) {
                int suffix = 1;
                while (objects.containsKey("@" + className + suffix)) suffix++;
                name = "@" + className + suffix;
            }
            if (!name.matches(OBJECT_NAME)) {
                errors.add(message(lineIndex, "unsupported object name '" + name + "'"));
            } else if (objects.containsKey(name)) {
                errors.add(message(lineIndex, "object '" + name + "' already exists"));
            } else {
                objects.put(name, newMutableObject(name, className));
            }
        }

        private void applyOperationCall(Matcher matcher, int lineIndex, List<String> errors) {
            Map<String, MutableObject> objectsBefore = copyMutableObjects();
            List<LinkSpec> linksBefore = new ArrayList<>(links);
            Matcher syntheticEnter = OPENTER.matcher("!openter " + matcher.group(1) + " "
                    + matcher.group(2) + "(" + matcher.group(3) + ")");
            if (!syntheticEnter.matches()) throw new IllegalStateException("Generated openter command is invalid");
            applyOpenter(syntheticEnter, lineIndex, errors);
            if (!errors.isEmpty()) return;
            List<String> body = imperativeBodies.get(activeOperation.operation().id());
            if (body == null) {
                restoreMutableState(objectsBefore, linksBefore);
                restoreOperationFrame();
                errors.add(message(lineIndex, "operation '" + matcher.group(2)
                        + "' has no supported imperative body"));
                return;
            }
            List<String> bodyCommands = body.stream().flatMap(command -> splitCommands(command).stream()).toList();
            if (bodyCommands.size() > MAX_OPERATION_BODY_COMMANDS) {
                restoreMutableState(objectsBefore, linksBefore);
                restoreOperationFrame();
                errors.add(message(lineIndex, "operation body exceeds command limit of "
                        + MAX_OPERATION_BODY_COMMANDS));
                return;
            }
            for (String command : bodyCommands) {
                applyOperationBodyCommand(command, lineIndex, errors);
                if (!errors.isEmpty()) {
                    restoreMutableState(objectsBefore, linksBefore);
                    restoreOperationFrame();
                    return;
                }
            }
            Matcher syntheticExit = OPEXIT.matcher("!opexit");
            if (!syntheticExit.matches()) throw new IllegalStateException("Generated opexit command is invalid");
            applyOpexit(syntheticExit, lineIndex, errors);
        }

        private void applyOperationBodyCommand(String command, int lineIndex, List<String> errors) {
            Matcher assignment = Pattern.compile("^(.+?)\\s*:=\\s*(.+)$").matcher(command.strip());
            if (!assignment.matches()) {
                errors.add(message(lineIndex, "unsupported operation body command: " + command));
                return;
            }
            OclValue value = evaluateExpression(assignment.group(2), lineIndex, errors);
            if (value == null) return;
            Matcher set = SET.matcher("!set " + assignment.group(1) + " := " + sourceLiteral(value));
            if (!set.matches()) throw new IllegalStateException("Generated set command is invalid");
            applySet(set, lineIndex, errors);
        }

        private static String sourceLiteral(OclValue value) {
            if (value instanceof IntegerValue integer) return Integer.toString(integer.value());
            if (value instanceof RealValue real) return Double.toString(real.value());
            if (value instanceof BooleanValue bool) return Boolean.toString(bool.value());
            if (value instanceof StringValue string) return "'" + string.value().replace("'", "''") + "'";
            if (value instanceof ObjectValue object) return object.object().name();
            throw new IllegalArgumentException("Unsupported assignment value " + value.getClass().getSimpleName());
        }

        private Map<String, MutableObject> copyMutableObjects() {
            Map<String, MutableObject> copy = new LinkedHashMap<>();
            for (MutableObject object : objects.values()) {
                MutableObject cloned = new MutableObject(object.name, object.className);
                cloned.values.putAll(object.values);
                copy.put(cloned.name, cloned);
            }
            return copy;
        }

        private MutableObject newMutableObject(String name, String className) {
            MutableObject object = new MutableObject(name, className);
            model.findClassByName(className).ifPresent(umlClass ->
                    model.typeConformanceOrder(umlClass.id()).stream()
                            .map(initialValues::get)
                            .filter(java.util.Objects::nonNull)
                            .forEach(object.values::putAll));
            return object;
        }

        private void restoreMutableState(Map<String, MutableObject> objectState, List<LinkSpec> linkState) {
            objects.clear(); objects.putAll(objectState);
            links.clear(); links.addAll(linkState);
        }

        private void restoreOperationFrame() {
            if (activeOperation == null) return;
            shellTypes.clear(); shellTypes.putAll(activeOperation.previousTypes());
            shellValues.clear(); shellValues.putAll(activeOperation.previousValues());
            shellObjectAliases.clear(); shellObjectAliases.putAll(activeOperation.previousAliases());
            activeOperation = null;
        }

        private void applyOpenter(Matcher matcher, int lineIndex, List<String> errors) {
            if (activeOperation != null) {
                errors.add(message(lineIndex, "nested operation frames are not supported"));
                return;
            }
            String receiverName = resolveObjectName(matcher.group(1));
            MutableObject receiver = objects.get(receiverName);
            if (receiver == null) {
                errors.add(message(lineIndex, "unknown operation receiver '" + matcher.group(1) + "'"));
                return;
            }
            List<String> argumentExpressions = splitArguments(matcher.group(3));
            UmlClass receiverClass = model.findClassByName(receiver.className).orElseThrow();
            UmlClass owner = model.typeConformanceOrder(receiverClass.id()).stream()
                    .map(model::findClass).flatMap(java.util.Optional::stream)
                    .filter(candidate -> candidate.operations().stream().anyMatch(operation ->
                            operation.name().equals(matcher.group(2))
                                    && operation.parameters().size() == argumentExpressions.size()))
                    .findFirst().orElse(null);
            if (owner == null) {
                errors.add(message(lineIndex, "unknown operation '" + matcher.group(2) + "' on '"
                        + receiver.className + "'"));
                return;
            }
            UmlOperation operation = owner.operations().stream().filter(candidate ->
                    candidate.name().equals(matcher.group(2))
                            && candidate.parameters().size() == argumentExpressions.size()).findFirst().orElseThrow();
            Map<UmlParameterId, OclValue> arguments = new LinkedHashMap<>();
            for (int index = 0; index < argumentExpressions.size(); index++) {
                OclValue value = evaluateExpression(argumentExpressions.get(index), lineIndex, errors);
                if (value == null) return;
                arguments.put(operation.parameters().get(index).id(), value);
            }
            Fixture before = fixture(List.of());
            ObjectModel preState = copySnapshot(before.evaluationContext().objectModel(),
                    "operation-pre-" + (lineIndex + 1));
            ObjectInstance preReceiver = preState.objects().stream()
                    .filter(object -> object.name().equals(receiverName)).findFirst().orElseThrow();
            OperationContextReference reference = new OperationContextReference(owner.id(), operation.id());
            OperationContext context = new OperationContext(new OperationInvocationId(
                    "reference-invocation-" + (lineIndex + 1)), reference, model, preReceiver.id(), preState,
                    null, arguments, OperationResultSlot.unavailable());
            evaluateContracts(context, OperationConstraintKind.PRECONDITION, lineIndex, errors);
            if (!errors.isEmpty()) return;

            activeOperation = new OperationFrame(context.invocationId(), reference, receiverName, operation,
                    Map.copyOf(arguments), preState, new LinkedHashMap<>(shellTypes),
                    new LinkedHashMap<>(shellValues), new LinkedHashMap<>(shellObjectAliases));
            shellTypes.put("self", OclType.classType(receiverClass, model));
            shellObjectAliases.put("self", receiverName);
            for (UmlParameter parameter : operation.parameters()) {
                bindFrameValue(parameter.name(), OclType.fromUmlType(parameter.type(), before.typeEnvironment()),
                        arguments.get(parameter.id()));
            }
        }

        private void applyOpexit(Matcher matcher, int lineIndex, List<String> errors) {
            if (activeOperation == null) {
                errors.add(message(lineIndex, "opexit without active operation frame"));
                return;
            }
            OclValue result = matcher.group(1) == null ? null
                    : evaluateExpression(matcher.group(1).trim(), lineIndex, errors);
            if (matcher.group(1) != null && result == null) return;
            OperationFrame frame = activeOperation;
            ObjectModel postState = copySnapshot(fixture(List.of()).evaluationContext().objectModel(),
                    "operation-post-" + (lineIndex + 1));
            ObjectInstance receiver = frame.preState().objects().stream()
                    .filter(object -> object.name().equals(frame.receiverName())).findFirst().orElseThrow();
            OperationContext context = new OperationContext(frame.invocationId(), frame.reference(), model,
                    receiver.id(), frame.preState(), postState, frame.arguments(),
                    result == null ? OperationResultSlot.unavailable() : OperationResultSlot.of(result));
            evaluateContracts(context, OperationConstraintKind.POSTCONDITION, lineIndex, errors);
            shellTypes.clear(); shellTypes.putAll(frame.previousTypes());
            shellValues.clear(); shellValues.putAll(frame.previousValues());
            shellObjectAliases.clear(); shellObjectAliases.putAll(frame.previousAliases());
            activeOperation = null;
        }

        private void evaluateContracts(OperationContext context, OperationConstraintKind kind,
                int lineIndex, List<String> errors) {
            for (OperationContract contract : contracts) {
                if (!contract.reference().equals(context.reference()) || contract.kind() != kind) continue;
                OperationContractResult result = new OperationContractService().evaluate(context, contract);
                operationResults.add(result);
                if (result.status() == OperationContractResult.Status.CONTEXT_ERROR) {
                    addOclSetupError(lineIndex, "cannot evaluate operation contract", result.diagnostics(), errors);
                    return;
                }
            }
        }

        private void bindFrameValue(String name, OclType type, OclValue value) {
            shellTypes.put(name, type);
            if (value instanceof ObjectValue objectValue) {
                shellObjectAliases.put(name, objectValue.object().name());
                shellValues.remove(name);
            } else {
                shellValues.put(name, value);
                shellObjectAliases.remove(name);
            }
        }

        private OclValue evaluateExpression(String expression, int lineIndex, List<String> errors) {
            Fixture current = fixture(List.of());
            OclParseResult parse = new OclParser().parse(expression);
            if (!parse.success()) {
                addOclSetupError(lineIndex, "cannot parse operation expression '" + expression + "'",
                        parse.diagnostics(), errors);
                return null;
            }
            OclTypecheckResult checked = new OclTypeChecker().checkExpression(current.typeEnvironment(), parse.ast());
            if (!checked.success()) {
                addOclSetupError(lineIndex, "cannot typecheck operation expression", checked.diagnostics(), errors);
                return null;
            }
            OclEvaluationResult evaluated = new OclEvaluator().evaluate(parse.ast(), current.evaluationContext());
            if (!evaluated.success()) {
                addOclSetupError(lineIndex, "cannot evaluate operation expression", evaluated.diagnostics(), errors);
                return null;
            }
            return evaluated.value();
        }

        private List<String> splitArguments(String source) {
            if (source == null || source.isBlank()) return List.of();
            List<String> arguments = new ArrayList<>();
            int depth = 0;
            boolean string = false;
            int start = 0;
            for (int index = 0; index < source.length(); index++) {
                char character = source.charAt(index);
                if (character == '\'' && (index + 1 >= source.length() || source.charAt(index + 1) != '\'')) string = !string;
                if (string) continue;
                if ("({[".indexOf(character) >= 0) depth++;
                if (")}]".indexOf(character) >= 0) depth--;
                if (character == ',' && depth == 0) {
                    arguments.add(source.substring(start, index).trim());
                    start = index + 1;
                }
            }
            arguments.add(source.substring(start).trim());
            return List.copyOf(arguments);
        }

        private void applyLet(Matcher let, int lineIndex, List<String> errors) {
            OclType declaredType = null;
            if (let.group(2) != null) {
                Fixture current = fixture(List.of());
                declaredType = OclType.fromUmlType(new UmlType(let.group(2)), current.typeEnvironment());
                if (declaredType.isInvalid()) {
                    errors.add(message(lineIndex, "unknown declared variable type '" + let.group(2) + "'"));
                    return;
                }
            }
            evaluateAndStore(let.group(1), let.group(3), declaredType, true, lineIndex, errors);
        }

        private void applyVariableAssignment(Matcher assignment, int lineIndex, List<String> errors) {
            evaluateAndStore(assignment.group(1), assignment.group(2), shellTypes.get(assignment.group(1)),
                    false, lineIndex, errors);
        }

        private void evaluateAndStore(String name, String expression, OclType requiredType,
                boolean declaration, int lineIndex, List<String> errors) {
            Fixture current = fixture(List.of());
            OclParseResult parse = new OclParser().parse(expression.trim());
            if (!parse.success()) {
                addOclSetupError(lineIndex, "cannot parse variable expression '" + expression.trim() + "'",
                        parse.diagnostics(), errors);
                return;
            }
            OclTypecheckResult typecheck = new OclTypeChecker().checkExpression(current.typeEnvironment(), parse.ast());
            if (!typecheck.success()) {
                addOclSetupError(lineIndex, "cannot typecheck variable expression", typecheck.diagnostics(), errors);
                return;
            }
            if (requiredType != null && !typecheck.resultType().conformsTo(requiredType)) {
                errors.add(message(lineIndex, "variable '" + name + "' requires " + requiredType.displayName()
                        + " but expression has " + typecheck.resultType().displayName()));
                return;
            }
            OclEvaluationResult evaluation = new OclEvaluator().evaluate(parse.ast(), current.evaluationContext());
            if (!evaluation.success()) {
                addOclSetupError(lineIndex, "cannot evaluate variable expression", evaluation.diagnostics(), errors);
                return;
            }
            OclType storedType = requiredType == null ? typecheck.resultType() : requiredType;
            if (!declaration && shellTypes.containsKey(name)
                    && !typecheck.resultType().conformsTo(shellTypes.get(name))) {
                errors.add(message(lineIndex, "assignment to '" + name + "' is not type-compatible"));
                return;
            }
            shellTypes.put(name, storedType);
            if (evaluation.value() instanceof ObjectValue objectValue) {
                shellObjectAliases.put(name, objectValue.object().name());
                shellValues.remove(name);
            } else {
                shellValues.put(name, evaluation.value());
                shellObjectAliases.remove(name);
            }
        }

        private void applyAssignCreate(Matcher assignment, int lineIndex, List<String> errors) {
            String variableName = assignment.group(1);
            String className = assignment.group(2);
            UmlClass umlClass = model.findClassByName(className).orElse(null);
            if (umlClass == null) {
                errors.add(message(lineIndex, "unknown class '" + className + "'"));
                return;
            }
            int suffix = 1;
            while (objects.containsKey(className + suffix)) suffix++;
            String objectName = className + suffix;
            if (objects.size() >= MAX_OBJECTS) {
                errors.add(message(lineIndex, "object limit of " + MAX_OBJECTS + " exceeded"));
                return;
            }
            objects.put(objectName, newMutableObject(objectName, className));
            shellTypes.put(variableName, OclType.classType(umlClass, model));
            shellObjectAliases.put(variableName, objectName);
            shellValues.remove(variableName);
        }

        private void applyAssignNew(Matcher assignment, int lineIndex, List<String> errors) {
            String variableName = assignment.group(1);
            String className = assignment.group(2);
            UmlClass umlClass = model.findClassByName(className).orElse(null);
            if (umlClass == null) {
                errors.add(message(lineIndex, "unknown class '" + className + "'"));
                return;
            }
            String requestedName = assignment.group(3);
            String objectName = requestedName == null ? variableName : requestedName;
            if (!objectName.matches(OBJECT_NAME)) {
                errors.add(message(lineIndex, "unsupported object name '" + objectName + "'"));
                return;
            }
            if (objects.containsKey(objectName)) {
                errors.add(message(lineIndex, "object '" + objectName + "' already exists"));
                return;
            }
            if (objects.size() >= MAX_OBJECTS) {
                errors.add(message(lineIndex, "object limit of " + MAX_OBJECTS + " exceeded"));
                return;
            }
            objects.put(objectName, newMutableObject(objectName, className));
            shellTypes.put(variableName, OclType.classType(umlClass, model));
            shellObjectAliases.put(variableName, objectName);
            shellValues.remove(variableName);
        }

        private void addOclSetupError(int lineIndex, String summary,
                List<de.useweb.backend.ocl.diagnostics.OclDiagnostic> diagnostics, List<String> errors) {
            oclSetupFailure = true;
            String detail = diagnostics.isEmpty() ? summary
                    : summary + ": " + diagnostics.get(0).code() + ": " + diagnostics.get(0).message();
            errors.add(message(lineIndex, detail));
        }

        private void applyCreate(Matcher create, int lineIndex, List<String> errors) {
            if (model.findClassByName(create.group(2)).isEmpty()) {
                errors.add(message(lineIndex, "unknown class '" + create.group(2) + "'"));
                return;
            }
            for (String rawName : create.group(1).split("\\s*,\\s*")) {
                String name = rawName.trim();
                if (!name.matches(OBJECT_NAME)) {
                    errors.add(message(lineIndex, "unsupported object selector '" + name + "'"));
                } else if (objects.containsKey(name)) {
                    errors.add(message(lineIndex, "object '" + name + "' already exists"));
                } else if (objects.size() >= MAX_OBJECTS) {
                    errors.add(message(lineIndex, "object limit of " + MAX_OBJECTS + " exceeded"));
                } else {
                    objects.put(name, newMutableObject(name, create.group(2)));
                }
            }
        }

        private void applySet(Matcher set, int lineIndex, List<String> errors) {
            String[] path = set.group(1).trim().split("\\.");
            if (path.length < 2 || !path[0].matches(OBJECT_NAME)) {
                errors.add(message(lineIndex, "unsupported assignment target '" + set.group(1).trim() + "'"));
                return;
            }
            MutableObject target = objects.get(resolveObjectName(path[0]));
            if (target == null) {
                errors.add(message(lineIndex, "unknown object '" + path[0] + "'"));
                return;
            }
            for (int segment = 1; segment < path.length - 1; segment++) {
                target = navigate(target, path[segment], lineIndex, errors);
                if (target == null) return;
            }
            String attributeName = path[path.length - 1];
            UmlClass targetClass = model.findClassByName(target.className).orElseThrow();
            if (model.findAttribute(targetClass.id(), attributeName).isEmpty()) {
                errors.add(message(lineIndex, "unknown attribute '" + attributeName + "' on '" + target.className + "'"));
                return;
            }
            target.values.put(attributeName, set.group(2).trim());
        }

        private MutableObject navigate(MutableObject source, String role, int lineIndex, List<String> errors) {
            List<MutableObject> targets = new ArrayList<>();
            for (LinkSpec link : links) {
                UmlAssociation association = association(link.association);
                if (association == null) continue;
                if (link.left.equals(source.name) && association.ends().get(1).roleName().equals(role)) {
                    addTarget(targets, link.right);
                } else if (link.right.equals(source.name) && association.ends().get(0).roleName().equals(role)) {
                    addTarget(targets, link.left);
                }
            }
            if (targets.size() != 1) {
                errors.add(message(lineIndex, "navigation '" + source.name + "." + role
                        + "' resolved to " + targets.size() + " objects; exactly one is required"));
                return null;
            }
            return targets.get(0);
        }

        private void addTarget(List<MutableObject> targets, String objectName) {
            MutableObject target = objects.get(objectName);
            if (target != null) targets.add(target);
        }

        private String resolveObjectName(String selector) {
            return shellObjectAliases.getOrDefault(selector, selector);
        }

        private String resolveObjectSelector(String selector, int lineIndex, List<String> errors) {
            String direct = resolveObjectName(selector);
            if (objects.containsKey(direct)) return direct;
            OclValue value = evaluateExpression(selector, lineIndex, errors);
            if (value instanceof ObjectValue objectValue) return objectValue.object().name();
            if (errors.isEmpty()) errors.add(message(lineIndex, "selector does not resolve to one object: " + selector));
            return null;
        }

        private void applyInsert(Matcher insert, int lineIndex, List<String> errors) {
            String left = resolveObjectSelector(insert.group(1).trim(), lineIndex, errors);
            String right = resolveObjectSelector(insert.group(2).trim(), lineIndex, errors);
            if (left == null || right == null) return;
            String associationName = insert.group(3);
            if (!left.matches(OBJECT_NAME) || !right.matches(OBJECT_NAME)) {
                errors.add(message(lineIndex, "only direct object names are supported for link insertion"));
                return;
            }
            if (objects.get(left) == null || objects.get(right) == null) {
                errors.add(message(lineIndex, "link insertion references an unknown object"));
                return;
            }
            UmlAssociation association = association(associationName);
            if (association == null) {
                errors.add(message(lineIndex, "unknown binary association '" + associationName + "'"));
                return;
            }
            if (!linkTypesMatch(association, objects.get(left), objects.get(right))) {
                errors.add(message(lineIndex, "objects do not conform to association '" + associationName + "'"));
                return;
            }
            LinkSpec candidate = new LinkSpec(associationName, left, right);
            if (links.contains(candidate)) {
                errors.add(message(lineIndex, "link already exists in association '" + associationName + "'"));
                return;
            }
            links.add(candidate);
        }

        private void applyDelete(Matcher delete, int lineIndex, List<String> errors) {
            String left = resolveObjectSelector(delete.group(1).trim(), lineIndex, errors);
            String right = resolveObjectSelector(delete.group(2).trim(), lineIndex, errors);
            if (left == null || right == null) return;
            if (!left.matches(OBJECT_NAME) || !right.matches(OBJECT_NAME)) {
                errors.add(message(lineIndex, "only direct object names are supported for link deletion"));
                return;
            }
            LinkSpec link = new LinkSpec(delete.group(3), left, right);
            if (!links.remove(link)) {
                errors.add(message(lineIndex, "link does not exist in association '" + delete.group(3) + "'"));
            }
        }

        private void applyDestroy(Matcher destroy, int lineIndex, List<String> errors) {
            String[] selectors = destroy.group(1).split("\\s*,\\s*");
            for (String rawSelector : selectors) {
                String name = rawSelector.trim();
                if (!name.matches(OBJECT_NAME)) {
                    errors.add(message(lineIndex, "unsupported destroy selector '" + name + "'"));
                    continue;
                }
                String resolvedName = resolveObjectName(name);
                if (objects.remove(resolvedName) == null) {
                    errors.add(message(lineIndex, "unknown object '" + resolvedName + "'"));
                    continue;
                }
                links.removeIf(link -> link.left.equals(resolvedName) || link.right.equals(resolvedName));
                objects.values().forEach(object -> object.values.replaceAll(
                        (attribute, value) -> resolvedName.equals(value) ? null : value));
            }
        }

        private boolean linkTypesMatch(UmlAssociation association, MutableObject left, MutableObject right) {
            UmlClass leftClass = model.findClassByName(left.className).orElseThrow();
            UmlClass rightClass = model.findClassByName(right.className).orElseThrow();
            return model.isSubtypeOf(leftClass.id(), association.ends().get(0).classId())
                    && model.isSubtypeOf(rightClass.id(), association.ends().get(1).classId());
        }

        private UmlAssociation association(String name) {
            return model.associations().stream().filter(candidate -> candidate.name().equals(name)).findFirst().orElse(null);
        }

        private String message(int lineIndex, String detail) {
            return replaySource + ":" + (lineIndex + 1) + ": " + detail;
        }

        Fixture fixture(List<String> unsupported) {
            UmlClass contextClass = model.classes().stream().filter(candidate -> !candidate.abstractClass()).findFirst()
                    .orElse(model.classes().get(0));
            List<ObjectInstance> instances = objects.values().stream().map(this::instance).toList();
            Map<String, ObjectInstance> byName = instances.stream().collect(LinkedHashMap::new,
                    (map, object) -> map.put(object.name(), object), Map::putAll);
            List<ObjectLink> objectLinks = links.stream().map(link -> objectLink(link, byName)).filter(java.util.Objects::nonNull).toList();
            ObjectModel snapshot = snapshot("reference-snapshot", instances, objectLinks);
            ObjectInstance self = instances.isEmpty()
                    ? new ObjectInstance(new ObjectInstanceId("reference-self"), "referenceSelf", contextClass.id(), List.of())
                    : instances.get(0);
            Map<String, OclType> types = new LinkedHashMap<>();
            Map<String, OclValue> values = new LinkedHashMap<>();
            byName.forEach((name, object) -> {
                model.findClass(object.classId()).ifPresent(umlClass -> types.put(name, OclType.classType(umlClass, model)));
                values.put(name, new ObjectValue(object));
            });
            types.putAll(shellTypes);
            values.putAll(shellValues);
            shellObjectAliases.forEach((variableName, objectName) -> {
                ObjectInstance object = byName.get(objectName);
                if (object != null) values.put(variableName, new ObjectValue(object));
            });
            return new Fixture(new TypeEnvironment(model, contextClass, types, null, definitionService),
                    new EvaluationContext(model, snapshot, self, values, snapshot, definitionService), unsupported,
                    oclSetupFailure, List.copyOf(operationResults));
        }

        private ObjectModel snapshot(String id, List<ObjectInstance> instances, List<ObjectLink> objectLinks) {
            return new ObjectModel(new ObjectModelId(id), id, instances, objectLinks);
        }

        private ObjectModel copySnapshot(ObjectModel source, String id) {
            return new ObjectModel(new ObjectModelId(id), id, source.objects(), source.links());
        }

        private ObjectInstance instance(MutableObject object) {
            UmlClass umlClass = model.findClassByName(object.className).orElseGet(() -> model.classes().get(0));
            Map<String, UmlAttribute> visibleAttributes = new LinkedHashMap<>();
            model.typeConformanceOrder(umlClass.id()).stream()
                    .map(model::findClass).flatMap(java.util.Optional::stream)
                    .flatMap(candidate -> candidate.attributes().stream())
                    .forEach(attribute -> visibleAttributes.putIfAbsent(attribute.name(), attribute));
            List<Slot> slots = visibleAttributes.values().stream().map(attribute -> new Slot(
                    new SlotId("slot-" + object.name + "-" + attribute.name()), attribute.id(),
                    slotValue(object.values.get(attribute.name()), attribute.type()))).toList();
            return new ObjectInstance(new ObjectInstanceId("object-" + object.name), object.name, umlClass.id(), slots);
        }

        private ObjectLink objectLink(LinkSpec link, Map<String, ObjectInstance> byName) {
            UmlAssociation association = model.associations().stream().filter(candidate -> candidate.name().equals(link.association)).findFirst().orElse(null);
            ObjectInstance left = byName.get(link.left);
            ObjectInstance right = byName.get(link.right);
            if (association == null || left == null || right == null) return null;
            return new ObjectLink(new ObjectLinkId("link-" + links.indexOf(link)), association.id(), List.of(
                    new ObjectLinkEnd(association.ends().get(0).id(), left.id()),
                    new ObjectLinkEnd(association.ends().get(1).id(), right.id())));
        }

        private static SlotValue slotValue(String raw, UmlType type) {
            if (raw == null || "Undefined".equalsIgnoreCase(raw) || "null".equalsIgnoreCase(raw)) return new SlotValue(null, type);
            String value = raw.trim();
            if ("OclAny".equals(type.name())) {
                if (value.matches("-?\\d+")) return SlotValue.ofInteger(Integer.parseInt(value));
                if (value.matches("-?\\d+\\.\\d+")) return SlotValue.ofReal(Double.parseDouble(value));
                if ("true".equals(value) || "false".equals(value)) return SlotValue.ofBoolean(Boolean.parseBoolean(value));
                type = UmlType.STRING;
            }
            if (type.equals(UmlType.INTEGER)) return SlotValue.ofInteger(Integer.parseInt(value));
            if (type.equals(UmlType.REAL)) return SlotValue.ofReal(Double.parseDouble(value));
            if (type.equals(UmlType.BOOLEAN)) return SlotValue.ofBoolean(Boolean.parseBoolean(value));
            if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) value = value.substring(1, value.length() - 1).replace("''", "'");
            return new SlotValue(value, type);
        }
    }

    private static final class ClassSpec {
        final String name; final boolean abstractClass; final List<String> parents;
        final Map<String, String> attributes = new LinkedHashMap<>();
        final Map<String, String> initialValues = new LinkedHashMap<>();
        final List<OperationSpec> operations = new ArrayList<>();
        ClassSpec(String name, boolean abstractClass, List<String> parents) { this.name = name; this.abstractClass = abstractClass; this.parents = parents; }
        OperationSpec addOperation(String name, String parameters, String returnType, String bodyExpression) {
            OperationSpec operation = new OperationSpec(name, parameters(parameters), returnType, bodyExpression);
            operations.add(operation);
            return operation;
        }
        private List<ParameterSpec> parameters(String source) {
            if (source == null || source.isBlank()) return List.of();
            List<ParameterSpec> result = new ArrayList<>();
            for (String declaration : source.split(",")) {
                String[] pair = declaration.trim().split("\\s*:\\s*", 2);
                if (pair.length == 2) result.add(new ParameterSpec(pair[0], pair[1]));
            }
            return List.copyOf(result);
        }
    }
    private static final class OperationSpec {
        final String name; final List<ParameterSpec> parameters; final String returnType; final String bodyExpression;
        final List<ContractSpec> contracts = new ArrayList<>();
        final List<String> bodyCommands = new ArrayList<>();
        OperationSpec(String name, List<ParameterSpec> parameters, String returnType, String bodyExpression) {
            this.name = name; this.parameters = parameters; this.returnType = returnType;
            this.bodyExpression = bodyExpression == null ? null : bodyExpression.trim();
        }
    }
    private record ParameterSpec(String name, String type) { }
    private record ContractSpec(String kind, String name, String expression) { }
    private static final class AssociationSpec { final String name; final List<EndSpec> ends = new ArrayList<>(); AssociationSpec(String name) { this.name = name; } }
    private record EndSpec(String className, String multiplicity, String role, boolean ordered) { }
    private static final class MutableObject { final String name; final String className; final Map<String, String> values = new LinkedHashMap<>(); MutableObject(String name, String className) { this.name = name; this.className = className; } }
    private record LinkSpec(String association, String left, String right) { }
    private record OperationFrame(OperationInvocationId invocationId, OperationContextReference reference,
            String receiverName, UmlOperation operation, Map<UmlParameterId, OclValue> arguments,
            ObjectModel preState, Map<String, OclType> previousTypes, Map<String, OclValue> previousValues,
            Map<String, String> previousAliases) { }
}
