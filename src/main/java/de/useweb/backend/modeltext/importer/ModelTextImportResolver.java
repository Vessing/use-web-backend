package de.useweb.backend.modeltext.importer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import de.useweb.backend.api.dto.ocl.OclDiagnosticDto;
import de.useweb.backend.api.dto.ocl.SourceRangeDto;
import de.useweb.backend.api.dto.ocl.SourceReferenceDto;
import de.useweb.backend.domain.modeltext.ModelTextSourceProvenance;
import de.useweb.backend.modeltext.parser.ModelTextParser;
import de.useweb.backend.modeltext.parser.ModelTextParser.ModelTextAssociation;
import de.useweb.backend.modeltext.parser.ModelTextParser.ModelTextClass;
import de.useweb.backend.modeltext.parser.ModelTextParser.ModelTextDataType;
import de.useweb.backend.modeltext.parser.ModelTextParser.ModelTextEnumeration;
import de.useweb.backend.modeltext.parser.ModelTextParser.ModelTextImport;
import de.useweb.backend.modeltext.parser.ModelTextParser.ModelTextInvariant;
import de.useweb.backend.modeltext.parser.ModelTextParser.ModelTextOperationContext;
import de.useweb.backend.modeltext.parser.ModelTextParser.ModelTextParseResult;

@Component
public class ModelTextImportResolver {

    private static final SourceRangeDto UNKNOWN_RANGE = new SourceRangeDto(1, 1, 0, 1, 1, 0);
    private final ModelTextParser parser;

    public ModelTextImportResolver(ModelTextParser parser) {
        this.parser = parser;
    }

    public ResolvedModel resolve(String entrySourceName, String entryText, Map<String, String> sourceFiles) {
        try {
            String entry = normalizeVirtualPath(entrySourceName == null || entrySourceName.isBlank()
                    ? "model.use" : entrySourceName);
            Map<String, byte[]> files = new LinkedHashMap<>();
            (sourceFiles == null ? Map.<String, String>of() : sourceFiles).forEach((path, text) ->
                    files.put(normalizeVirtualPath(path), (text == null ? "" : text).getBytes(StandardCharsets.UTF_8)));
            files.put(entry, (entryText == null ? "" : entryText).getBytes(StandardCharsets.UTF_8));
            return resolve(entry, new VirtualRepository(files));
        } catch (UnsafeImportPathException exception) {
            return failed("UNSAFE_IMPORT_PATH", exception.getMessage(), entrySourceName, null);
        }
    }

    public ResolvedModel resolve(Path importRoot, Path entryFile) {
        if (importRoot == null || entryFile == null) {
            return failed("INVALID_IMPORT_SOURCE", "Import root and entry file are required.", null, null);
        }
        try {
            Path root = importRoot.toRealPath();
            Path entry = entryFile.isAbsolute() ? entryFile.toRealPath() : root.resolve(entryFile).toRealPath();
            if (!entry.startsWith(root)) {
                return failed("UNSAFE_IMPORT_PATH", "Entry file escapes the configured import root.",
                        entryFile.toString(), null);
            }
            return resolve(toPortable(root.relativize(entry)), new FileRepository(root));
        } catch (IOException | InvalidPathException exception) {
            return failed("IMPORT_SOURCE_NOT_FOUND", "Entry file could not be read.",
                    entryFile == null ? null : entryFile.toString(), exception.getMessage());
        }
    }

    private ResolvedModel resolve(String entry, SourceRepository repository) {
        ResolutionState state = new ResolutionState(repository);
        resolveSource(entry, Selection.allElements(), null, 0, UNKNOWN_RANGE, state);
        return new ResolvedModel(state.accumulator.result(state.modelName, state.diagnostics),
                List.copyOf(state.provenance), state.accumulator.elementSources());
    }

    private void resolveSource(String sourcePath, Selection selection, String importedBy, int depth,
            SourceRangeDto importRange, ResolutionState state) {
        String normalized;
        try {
            normalized = state.repository.resolve(importedBy, sourcePath);
        } catch (UnsafeImportPathException exception) {
            state.diagnostics.add(diagnostic("UNSAFE_IMPORT_PATH", exception.getMessage(), sourcePath,
                    importedBy, importRange, Map.of("requestedPath", sourcePath)));
            return;
        }

        if (state.active.contains(normalized)) {
            List<String> cycle = new ArrayList<>(state.active);
            cycle.add(normalized);
            state.diagnostics.add(diagnostic("IMPORT_CYCLE", "Import cycle detected: " + String.join(" -> ", cycle),
                    normalized, importedBy, importRange, Map.of("cycle", cycle)));
            return;
        }

        byte[] bytes;
        try {
            bytes = state.repository.read(normalized);
        } catch (IOException exception) {
            state.diagnostics.add(diagnostic("IMPORT_SOURCE_NOT_FOUND", "Imported USE file was not found.",
                    normalized, importedBy, importRange,
                    Map.of("requestedPath", sourcePath, "technicalMessage", String.valueOf(exception.getMessage()))));
            return;
        }

        String edgeKey = (importedBy == null ? "<root>" : importedBy) + "->" + normalized + selection.key();
        if (state.provenanceEdges.add(edgeKey)) {
            state.provenance.add(new ModelTextSourceProvenance(normalized, importedBy, selection.displayNames(),
                    depth, sha256(bytes)));
        }

        ModelTextParseResult parsed = state.parsed.computeIfAbsent(normalized, ignored -> parser.parse(bytes));
        if (state.diagnosedSources.add(normalized)) {
            parsed.diagnostics().forEach(problem -> state.diagnostics.add(withSource(problem, normalized)));
        }
        if (importedBy == null) {
            state.modelName = parsed.modelName();
        }

        state.active.addLast(normalized);
        for (ModelTextImport modelImport : parsed.imports()) {
            Selection importedSelection = modelImport.wildcard()
                    ? Selection.allElements()
                    : Selection.names(modelImport.selectedNames());
            resolveSource(modelImport.sourcePath(), importedSelection, normalized, depth + 1,
                    modelImport.sourceRange(), state);
        }
        state.active.removeLast();

        Set<String> available = availableNames(parsed);
        if (!selection.all()) {
            for (String selectedName : selection.names()) {
                if (!available.contains(selectedName)) {
                    state.diagnostics.add(diagnostic("UNKNOWN_IMPORTED_ELEMENT",
                            "Imported element '" + selectedName + "' does not exist in source '" + normalized + "'.",
                            normalized, importedBy, importRange, Map.of("selectedName", selectedName)));
                }
            }
        }
        state.accumulator.add(parsed, selection, normalized, state.diagnostics);
    }

    private Set<String> availableNames(ModelTextParseResult parsed) {
        Set<String> names = new LinkedHashSet<>();
        parsed.classes().forEach(type -> names.add(type.name()));
        parsed.enumerations().forEach(type -> names.add(type.name()));
        parsed.dataTypes().forEach(type -> names.add(type.name()));
        parsed.associations().forEach(association -> {
            names.add(association.name());
            if (association.associationClassName() != null) names.add(association.associationClassName());
        });
        return names;
    }

    private ResolvedModel failed(String code, String message, String source, String technicalMessage) {
        OclDiagnosticDto diagnostic = diagnostic(code, message, source, null, UNKNOWN_RANGE,
                Map.of("technicalMessage", technicalMessage == null ? "" : technicalMessage));
        return new ResolvedModel(new ModelTextParseResult(null, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(diagnostic)), List.of(), Map.of());
    }

    private OclDiagnosticDto withSource(OclDiagnosticDto diagnostic, String sourcePath) {
        return new OclDiagnosticDto(diagnostic.id(), diagnostic.kind(), diagnostic.phase(), diagnostic.code(),
                diagnostic.severity(), diagnostic.message(), diagnostic.userMessage(), diagnostic.technicalMessage(),
                diagnostic.sourceRange(), new SourceReferenceDto(sourcePath, "USE_MODEL_TEXT", null,
                        diagnostic.sourceRange()), diagnostic.expected(), diagnostic.actual(), diagnostic.targets(),
                mergeDetails(diagnostic.details(), Map.of("sourcePath", sourcePath)), diagnostic.suggestedFix());
    }

    private OclDiagnosticDto diagnostic(String code, String message, String sourcePath, String importedBy,
            SourceRangeDto range, Map<String, Object> details) {
        Map<String, Object> provenance = new LinkedHashMap<>(details);
        if (sourcePath != null) provenance.put("sourcePath", sourcePath);
        if (importedBy != null) provenance.put("importedBy", importedBy);
        return new OclDiagnosticDto(null, "VALIDATION_ERROR", "IMPORT", code, "ERROR", message, message, message,
                range, sourcePath == null ? null : new SourceReferenceDto(sourcePath, "USE_MODEL_TEXT", null, range),
                List.of(), sourcePath, List.of(), Map.copyOf(provenance), null);
    }

    private Map<String, Object> mergeDetails(Map<String, Object> first, Map<String, Object> second) {
        Map<String, Object> result = new LinkedHashMap<>(first == null ? Map.of() : first);
        result.putAll(second);
        return Map.copyOf(result);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String normalizeVirtualPath(String value) {
        if (value == null || value.isBlank()) {
            throw new UnsafeImportPathException("Import path must not be blank.");
        }
        String portable = value.replace('\\', '/');
        if (portable.startsWith("/") || portable.matches("^[A-Za-z]:.*") || portable.contains("://")) {
            throw new UnsafeImportPathException("Absolute import paths are not allowed: " + value);
        }
        try {
            Path normalized = Path.of(portable).normalize();
            if (normalized.isAbsolute() || normalized.startsWith("..")) {
                throw new UnsafeImportPathException("Import path escapes the source bundle: " + value);
            }
            return toPortable(normalized);
        } catch (InvalidPathException exception) {
            throw new UnsafeImportPathException("Invalid import path: " + value);
        }
    }

    private static String toPortable(Path path) {
        return path.toString().replace('\\', '/');
    }

    public record ResolvedModel(ModelTextParseResult model, List<ModelTextSourceProvenance> provenance,
            Map<String, String> elementSources) {
        public ResolvedModel(ModelTextParseResult model, List<ModelTextSourceProvenance> provenance) {
            this(model, provenance, Map.of());
        }

        public ResolvedModel {
            elementSources = Map.copyOf(elementSources == null ? Map.of() : elementSources);
        }
    }

    private interface SourceRepository {
        String resolve(String importedBy, String requestedPath);
        byte[] read(String sourcePath) throws IOException;
    }

    private static final class VirtualRepository implements SourceRepository {
        private final Map<String, byte[]> files;

        private VirtualRepository(Map<String, byte[]> files) {
            this.files = Map.copyOf(files);
        }

        @Override
        public String resolve(String importedBy, String requestedPath) {
            String requested = requestedPath.replace('\\', '/');
            String parent = importedBy == null || !importedBy.contains("/")
                    ? "" : importedBy.substring(0, importedBy.lastIndexOf('/') + 1);
            return normalizeVirtualPath(parent + requested);
        }

        @Override
        public byte[] read(String sourcePath) throws IOException {
            byte[] source = files.get(sourcePath);
            if (source == null) throw new IOException("No bundled source named " + sourcePath);
            return source.clone();
        }
    }

    private static final class FileRepository implements SourceRepository {
        private final Path root;

        private FileRepository(Path root) {
            this.root = root;
        }

        @Override
        public String resolve(String importedBy, String requestedPath) {
            try {
                Path parent = importedBy == null ? Path.of("") : Path.of(importedBy).getParent();
                Path relative = (parent == null ? Path.of("") : parent).resolve(requestedPath).normalize();
                if (relative.isAbsolute() || relative.startsWith("..")) {
                    throw new UnsafeImportPathException("Import path escapes the configured import root: " + requestedPath);
                }
                Path candidate = root.resolve(relative).normalize();
                if (!candidate.startsWith(root)) {
                    throw new UnsafeImportPathException("Import path escapes the configured import root: " + requestedPath);
                }
                if (Files.exists(candidate)) {
                    Path real = candidate.toRealPath();
                    if (!real.startsWith(root)) {
                        throw new UnsafeImportPathException("Import symlink escapes the configured import root: " + requestedPath);
                    }
                    return toPortable(root.relativize(real));
                }
                return toPortable(relative);
            } catch (IOException | InvalidPathException exception) {
                throw new UnsafeImportPathException("Invalid import path: " + requestedPath);
            }
        }

        @Override
        public byte[] read(String sourcePath) throws IOException {
            Path candidate = root.resolve(sourcePath).normalize();
            if (!candidate.startsWith(root)) throw new IOException("Path escapes import root");
            Path real = candidate.toRealPath();
            if (!real.startsWith(root)) throw new IOException("Symlink escapes import root");
            return Files.readAllBytes(real);
        }
    }

    private static final class ResolutionState {
        private final SourceRepository repository;
        private final Map<String, ModelTextParseResult> parsed = new LinkedHashMap<>();
        private final Set<String> diagnosedSources = new LinkedHashSet<>();
        private final Deque<String> active = new ArrayDeque<>();
        private final Set<String> provenanceEdges = new LinkedHashSet<>();
        private final List<ModelTextSourceProvenance> provenance = new ArrayList<>();
        private final List<OclDiagnosticDto> diagnostics = new ArrayList<>();
        private final Accumulator accumulator = new Accumulator();
        private String modelName;

        private ResolutionState(SourceRepository repository) {
            this.repository = repository;
        }
    }

    private record Selection(boolean all, Set<String> names) {
        private static Selection allElements() {
            return new Selection(true, Set.of());
        }

        private static Selection names(List<String> names) {
            return new Selection(false, Set.copyOf(names == null ? List.of() : names));
        }

        private boolean includes(String name) {
            return all || names.contains(name);
        }

        private List<String> displayNames() {
            return all ? List.of("*") : names.stream().sorted().toList();
        }

        private String key() {
            return all ? "*" : names.stream().sorted().toList().toString();
        }
    }

    private static final class Accumulator {
        private final Map<String, ModelTextClass> classes = new LinkedHashMap<>();
        private final Map<String, ModelTextEnumeration> enumerations = new LinkedHashMap<>();
        private final Map<String, ModelTextDataType> dataTypes = new LinkedHashMap<>();
        private final Map<String, ModelTextAssociation> associations = new LinkedHashMap<>();
        private final Map<String, ModelTextInvariant> invariants = new LinkedHashMap<>();
        private final Map<String, ModelTextOperationContext> operationContexts = new LinkedHashMap<>();
        private final Map<String, String> elementSources = new LinkedHashMap<>();

        private void add(ModelTextParseResult parsed, Selection selection, String source,
                List<OclDiagnosticDto> diagnostics) {
            parsed.classes().stream().filter(type -> selection.includes(type.name()))
                    .forEach(type -> {
                        put(classes, type.name(), type, "CLASS", source, diagnostics);
                        elementSources.putIfAbsent("CLASS:" + type.name(), source);
                    });
            parsed.enumerations().stream().filter(type -> selection.includes(type.name()))
                    .forEach(type -> {
                        put(enumerations, type.name(), type, "ENUMERATION", source, diagnostics);
                        elementSources.putIfAbsent("ENUMERATION:" + type.name(), source);
                    });
            parsed.dataTypes().stream().filter(type -> selection.includes(type.name()))
                    .forEach(type -> {
                        put(dataTypes, type.name(), type, "DATATYPE", source, diagnostics);
                        elementSources.putIfAbsent("DATATYPE:" + type.name(), source);
                    });
            parsed.associations().stream()
                    .filter(value -> selection.includes(value.name())
                            || value.associationClassName() != null && selection.includes(value.associationClassName()))
                    .forEach(value -> {
                        put(associations, value.name(), value, "ASSOCIATION", source, diagnostics);
                        elementSources.putIfAbsent("ASSOCIATION:" + value.name(), source);
                    });
            parsed.invariants().stream().filter(value -> selection.includes(value.contextClass()))
                    .forEach(value -> put(invariants, value.contextClass() + "::" + value.name(), value,
                            "INVARIANT", source, diagnostics));
            parsed.operationContexts().stream().filter(value -> selection.includes(value.contextClass()))
                    .forEach(value -> put(operationContexts, operationKey(value), value,
                            "OPERATION_CONTEXT", source, diagnostics));
        }

        private <T> void put(Map<String, T> target, String key, T value, String kind, String source,
                List<OclDiagnosticDto> diagnostics) {
            T existing = target.putIfAbsent(key, value);
            if (existing != null && !existing.equals(value)) {
                diagnostics.add(new OclDiagnosticDto(null, "VALIDATION_ERROR", "IMPORT",
                        "DUPLICATE_IMPORTED_ELEMENT", "ERROR",
                        "Imported " + kind.toLowerCase() + " '" + key + "' is defined more than once.",
                        "Importierter Name ist nicht eindeutig.", "Duplicate imported element: " + key,
                        UNKNOWN_RANGE, new SourceReferenceDto(source, "USE_MODEL_TEXT", null, UNKNOWN_RANGE),
                        List.of(), key, List.of(), Map.of("elementKind", kind, "elementName", key,
                        "sourcePath", source), null));
            }
        }

        private String operationKey(ModelTextOperationContext context) {
            return context.contextClass() + "::" + context.operationName() + context.parameters().stream()
                    .map(parameter -> parameter.type()).toList();
        }

        private ModelTextParseResult result(String modelName, List<OclDiagnosticDto> diagnostics) {
            return new ModelTextParseResult(modelName, List.of(), List.copyOf(classes.values()),
                    List.copyOf(enumerations.values()), List.copyOf(dataTypes.values()),
                    List.copyOf(associations.values()), List.copyOf(invariants.values()),
                    List.copyOf(operationContexts.values()), List.copyOf(diagnostics));
        }

        private Map<String, String> elementSources() {
            return Map.copyOf(elementSources);
        }
    }

    private static final class UnsafeImportPathException extends IllegalArgumentException {
        private UnsafeImportPathException(String message) {
            super(message);
        }
    }
}
