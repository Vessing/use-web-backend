package de.useweb.backend.application.uml;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import de.useweb.backend.domain.uml.PrimitiveType;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlDataType;
import de.useweb.backend.domain.uml.UmlEnumeration;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlType;

/** Parses persisted API-v1 type strings and validates their JSON values. */
public final class StructuredUmlTypeService {

    public enum Kind { PRIMITIVE, UNLIMITED_NATURAL, VOID, CLASS, ENUMERATION, DATA_TYPE, TUPLE, COLLECTION }

    public enum CollectionKind { SET, BAG, SEQUENCE, ORDERED_SET }

    public record TuplePart(String name, ResolvedType type) {}

    public record ResolvedType(Kind kind, UmlType umlType, String elementId, String elementName,
            UmlDataType dataType, UmlEnumeration enumeration, CollectionKind collectionKind,
            ResolvedType elementType, List<TuplePart> tupleParts) {
        public ResolvedType {
            tupleParts = List.copyOf(tupleParts == null ? List.of() : tupleParts);
        }
    }

    public static final class TypeException extends RuntimeException {
        private final String reason;
        private final String path;
        private final String expectedType;
        private final Object actualValue;

        private TypeException(String reason, String message, String path, String expectedType, Object actualValue) {
            super(message);
            this.reason = reason;
            this.path = path;
            this.expectedType = expectedType;
            this.actualValue = actualValue;
        }

        public String reason() { return reason; }
        public String path() { return path; }
        public String expectedType() { return expectedType; }
        public Object actualValue() { return actualValue; }
    }

    public ResolvedType resolve(UmlModel model, String typeText, UmlClass contextClass, boolean allowVoid) {
        if (typeText == null || typeText.isBlank()) {
            throw typeError("INVALID_TYPE_SYNTAX", "Type must not be blank", "type", typeText);
        }
        Parser parser = new Parser(model, contextClass, typeText.trim(), allowVoid, new LinkedHashSet<>());
        ResolvedType result = parser.parseType("type");
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw typeError("INVALID_TYPE_SYNTAX", "Unexpected token at position " + parser.position,
                    "type", typeText);
        }
        return result;
    }

    public Object normalizeValue(Object value, ResolvedType type, String path) {
        if (value == null) return null;
        return switch (type.kind()) {
            case PRIMITIVE -> normalizePrimitive(value, type, path);
            case UNLIMITED_NATURAL -> normalizeUnlimitedNatural(value, type, path);
            case ENUMERATION -> normalizeEnumeration(value, type, path);
            case DATA_TYPE -> normalizeDataType(value, type, path);
            case TUPLE -> normalizeTuple(value, type, path);
            case COLLECTION -> normalizeCollection(value, type, path);
            case CLASS -> throw valueError("Object-valued persisted attributes are not supported", path, type, value);
            case VOID -> throw valueError("Void cannot hold a persisted value", path, type, value);
        };
    }

    public boolean references(ResolvedType type, String elementId) {
        if (elementId != null && elementId.equals(type.elementId())) return true;
        if (type.elementType() != null && references(type.elementType(), elementId)) return true;
        return type.tupleParts().stream().anyMatch(part -> references(part.type(), elementId));
    }

    /** Returns persisted value paths that contain the selected DataType property. */
    public List<String> dataTypePropertyValuePaths(Object value, ResolvedType type, String dataTypeId,
            String propertyId, String path) {
        List<String> paths = new ArrayList<>();
        collectDataTypePropertyValuePaths(value, type, dataTypeId, propertyId, path, paths);
        return List.copyOf(paths);
    }

    private void collectDataTypePropertyValuePaths(Object value, ResolvedType type, String dataTypeId,
            String propertyId, String path, List<String> paths) {
        if (value == null) return;
        switch (type.kind()) {
            case DATA_TYPE -> {
                if (!(value instanceof Map<?, ?> structured)) return;
                for (int index = 0; index < type.dataType().properties().size(); index++) {
                    var property = type.dataType().properties().get(index);
                    ResolvedType propertyType = type.tupleParts().get(index).type();
                    String propertyPath = path + "." + property.name();
                    if (dataTypeId.equals(type.elementId()) && propertyId.equals(property.id())
                            && structured.containsKey(property.name())) {
                        paths.add(propertyPath);
                    }
                    collectDataTypePropertyValuePaths(structured.get(property.name()), propertyType,
                            dataTypeId, propertyId, propertyPath, paths);
                }
            }
            case TUPLE -> {
                if (!(value instanceof Map<?, ?> structured)) return;
                for (TuplePart part : type.tupleParts()) {
                    collectDataTypePropertyValuePaths(structured.get(part.name()), part.type(), dataTypeId,
                            propertyId, path + "." + part.name(), paths);
                }
            }
            case COLLECTION -> {
                if (!(value instanceof Collection<?> values)) return;
                int index = 0;
                for (Object item : values) {
                    collectDataTypePropertyValuePaths(item, type.elementType(), dataTypeId, propertyId,
                            path + "[" + index++ + "]", paths);
                }
            }
            default -> { }
        }
    }

    private Object normalizePrimitive(Object value, ResolvedType type, String path) {
        PrimitiveType primitive = type.umlType().primitiveType().orElseThrow();
        boolean valid = switch (primitive) {
            case STRING -> value instanceof String;
            case INTEGER -> value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long;
            case REAL -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
        };
        if (!valid) throw valueError("Value does not conform to primitive type", path, type, value);
        return value;
    }

    private Object normalizeUnlimitedNatural(Object value, ResolvedType type, String path) {
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)
                || ((Number) value).longValue() < 0) {
            throw valueError("UnlimitedNatural requires a non-negative integer", path, type, value);
        }
        return value;
    }

    private Object normalizeEnumeration(Object value, ResolvedType type, String path) {
        if (!(value instanceof String literal) || !type.enumeration().containsLiteral(literal)) {
            throw valueError("Unknown enumeration literal", path, type, value);
        }
        return value;
    }

    private Object normalizeDataType(Object value, ResolvedType type, String path) {
        if (!(value instanceof Map<?, ?> structured)) {
            throw valueError("DataType value must be an object", path, type, value);
        }
        List<String> expected = type.dataType().properties().stream().map(property -> property.name()).toList();
        if (structured.size() != expected.size() || !structured.keySet().stream().allMatch(expected::contains)) {
            throw valueError("DataType value must contain exactly its declared properties", path, type, value);
        }
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        for (var property : type.dataType().properties()) {
            ResolvedType propertyType = type.tupleParts().stream()
                    .filter(part -> part.name().equals(property.name())).map(TuplePart::type).findFirst()
                    .orElseThrow(() -> new TypeException("UNRESOLVED_NESTED_TYPE",
                            "DataType property type is not attached to the resolved descriptor",
                            path + "." + property.name(), property.type().name(), null));
            normalized.put(property.name(), normalizeValue(structured.get(property.name()), propertyType,
                    path + "." + property.name()));
        }
        return Collections.unmodifiableMap(normalized);
    }

    private Object normalizeTuple(Object value, ResolvedType type, String path) {
        if (!(value instanceof Map<?, ?> structured)) {
            throw valueError("Tuple value must be an object", path, type, value);
        }
        List<String> expected = type.tupleParts().stream().map(TuplePart::name).toList();
        if (structured.size() != expected.size() || !structured.keySet().stream().allMatch(expected::contains)) {
            throw valueError("Tuple value must contain exactly its declared fields", path, type, value);
        }
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        for (TuplePart part : type.tupleParts()) {
            normalized.put(part.name(), normalizeValue(structured.get(part.name()), part.type(), path + "." + part.name()));
        }
        return Collections.unmodifiableMap(normalized);
    }

    private Object normalizeCollection(Object value, ResolvedType type, String path) {
        if (!(value instanceof Collection<?> values)) {
            throw valueError("Collection value must be a JSON array", path, type, value);
        }
        List<Object> normalized = new ArrayList<>();
        int index = 0;
        for (Object item : values) {
            normalized.add(normalizeValue(item, type.elementType(), path + "[" + index++ + "]"));
        }
        if ((type.collectionKind() == CollectionKind.SET || type.collectionKind() == CollectionKind.ORDERED_SET)
                && new LinkedHashSet<>(normalized).size() != normalized.size()) {
            throw valueError("Unique collection contains duplicate values", path, type, value);
        }
        return Collections.unmodifiableList(normalized);
    }

    private static TypeException typeError(String reason, String message, String path, String type) {
        return new TypeException(reason, message, path, type, null);
    }

    private static TypeException valueError(String message, String path, ResolvedType type, Object value) {
        return new TypeException("INVALID_STRUCTURED_VALUE", message, path, type.umlType().name(), value);
    }

    private final class Parser {
        private final UmlModel model;
        private final UmlClass contextClass;
        private final String source;
        private final boolean allowVoid;
        private final LinkedHashSet<String> resolvingDataTypes;
        private int position;

        private Parser(UmlModel model, UmlClass contextClass, String source, boolean allowVoid,
                LinkedHashSet<String> resolvingDataTypes) {
            this.model = model;
            this.contextClass = contextClass;
            this.source = source;
            this.allowVoid = allowVoid;
            this.resolvingDataTypes = resolvingDataTypes;
        }

        private ResolvedType parseType(String path) {
            skipWhitespace();
            String name = identifier(path);
            skipWhitespace();
            if (peek('(')) {
                position++;
                if (name.equals("Tuple")) return parseTuple(path);
                CollectionKind collectionKind = switch (name) {
                    case "Set" -> CollectionKind.SET;
                    case "Bag" -> CollectionKind.BAG;
                    case "Sequence" -> CollectionKind.SEQUENCE;
                    case "OrderedSet" -> CollectionKind.ORDERED_SET;
                    default -> null;
                };
                if (collectionKind == null) throw typeError("INVALID_TYPE_SYNTAX", "Unknown generic type: " + name, path, source);
                ResolvedType element = parseType(path + ".elementType");
                require(')', path);
                String canonical = name + "(" + element.umlType().name() + ")";
                return new ResolvedType(Kind.COLLECTION, new UmlType(canonical), null, name, null, null,
                        collectionKind, element, List.of());
            }
            return named(name, path);
        }

        private ResolvedType parseTuple(String path) {
            List<TuplePart> parts = new ArrayList<>();
            LinkedHashSet<String> names = new LinkedHashSet<>();
            skipWhitespace();
            if (peek(')')) throw typeError("INVALID_TYPE_SYNTAX", "Tuple requires at least one field", path, source);
            while (true) {
                String field = identifier(path);
                if (!names.add(field)) throw typeError("INVALID_TYPE_SYNTAX", "Duplicate tuple field: " + field, path + "." + field, source);
                require(':', path + "." + field);
                parts.add(new TuplePart(field, parseType(path + "." + field)));
                skipWhitespace();
                if (peek(')')) { position++; break; }
                require(',', path);
            }
            String canonical = "Tuple(" + parts.stream().map(part -> part.name() + ":" + part.type().umlType().name())
                    .reduce((left, right) -> left + "," + right).orElseThrow() + ")";
            return new ResolvedType(Kind.TUPLE, new UmlType(canonical), null, "Tuple", null, null,
                    null, null, parts);
        }

        private ResolvedType named(String name, String path) {
            for (PrimitiveType primitive : PrimitiveType.values()) {
                if (primitive.displayName().equals(name)) {
                    return new ResolvedType(Kind.PRIMITIVE, new UmlType(name), null, name, null, null,
                            null, null, List.of());
                }
            }
            if (name.equals("UnlimitedNatural")) return new ResolvedType(Kind.UNLIMITED_NATURAL,
                    UmlType.UNLIMITED_NATURAL, null, name, null, null, null, null, List.of());
            if ((name.equals("Void") || name.equals("OclVoid")) && allowVoid) return new ResolvedType(Kind.VOID,
                    UmlType.VOID, null, name, null, null, null, null, List.of());

            List<UmlClass> classes = contextClass == null
                    ? model.classes().stream().filter(value -> value.name().equals(name)
                            || value.qualifiedName(model).equals(name)).toList()
                    : model.resolveClassIgnoringVisibility(name, contextClass).stream().toList();
            List<UmlEnumeration> enumerations = contextClass == null
                    ? model.enumerations().stream().filter(value -> value.name().equals(name)
                            || value.qualifiedName(model).equals(name)).toList()
                    : model.resolveEnumeration(name, contextClass).stream().toList();
            List<UmlDataType> dataTypes = contextClass == null
                    ? model.dataTypes().stream().filter(value -> value.name().equals(name)
                            || value.qualifiedName(model).equals(name)).toList()
                    : model.resolveDataType(name, contextClass).stream().toList();
            int count = classes.size() + enumerations.size() + dataTypes.size();
            if (count == 0) throw typeError("UNKNOWN_TYPE", "Unknown type: " + name, path, name);
            if (count > 1) throw typeError("AMBIGUOUS_TYPE", "Ambiguous type: " + name, path, name);
            if (!classes.isEmpty()) {
                UmlClass value = classes.getFirst();
                return new ResolvedType(Kind.CLASS, new UmlType(name), value.id().value(), value.name(), null, null,
                        null, null, List.of());
            }
            if (!enumerations.isEmpty()) {
                UmlEnumeration value = enumerations.getFirst();
                return new ResolvedType(Kind.ENUMERATION, new UmlType(name), value.id().value(), value.name(), null, value,
                        null, null, List.of());
            }
            UmlDataType value = dataTypes.getFirst();
            if (resolvingDataTypes.contains(value.id().value())) {
                throw typeError("CYCLIC_DATATYPE_VALUE_TYPE",
                        "Cyclic DataType value type: " + value.name(), path, name);
            }
            LinkedHashSet<String> nestedDataTypes = new LinkedHashSet<>(resolvingDataTypes);
            nestedDataTypes.add(value.id().value());
            List<TuplePart> properties = value.properties().stream()
                    .map(property -> new TuplePart(property.name(), new Parser(model, contextClass,
                            property.type().name(), false, nestedDataTypes)
                            .parseComplete(path + "." + property.name())))
                    .toList();
            return new ResolvedType(Kind.DATA_TYPE, new UmlType(name), value.id().value(), value.name(), value, null,
                    null, null, properties);
        }

        private ResolvedType parseComplete(String path) {
            ResolvedType result = parseType(path);
            skipWhitespace();
            if (!atEnd()) throw typeError("INVALID_TYPE_SYNTAX", "Unexpected nested type token", path, source);
            return result;
        }

        private String identifier(String path) {
            skipWhitespace();
            int start = position;
            while (!atEnd()) {
                char current = source.charAt(position);
                if (Character.isLetterOrDigit(current) || current == '_') {
                    position++;
                } else if (current == ':' && position + 1 < source.length() && source.charAt(position + 1) == ':') {
                    position += 2;
                } else {
                    break;
                }
            }
            if (start == position) throw typeError("INVALID_TYPE_SYNTAX", "Expected type or field name", path, source);
            String value = source.substring(start, position);
            if (!value.matches("[A-Za-z_][A-Za-z0-9_]*(::[A-Za-z_][A-Za-z0-9_]*)*")) {
                throw typeError("INVALID_TYPE_SYNTAX", "Invalid type or field name: " + value, path, source);
            }
            return value;
        }

        private void require(char token, String path) {
            skipWhitespace();
            if (atEnd() || source.charAt(position) != token) {
                throw typeError("INVALID_TYPE_SYNTAX", "Expected '" + token + "' at position " + position, path, source);
            }
            position++;
        }

        private boolean peek(char token) { return !atEnd() && source.charAt(position) == token; }
        private boolean atEnd() { return position >= source.length(); }
        private void skipWhitespace() { while (!atEnd() && Character.isWhitespace(source.charAt(position))) position++; }
    }
}
