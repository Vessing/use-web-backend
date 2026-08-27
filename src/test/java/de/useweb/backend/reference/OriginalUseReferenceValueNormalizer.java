package de.useweb.backend.reference;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.ocl.value.BooleanValue;
import de.useweb.backend.ocl.value.CollectionValue;
import de.useweb.backend.ocl.value.EnumValue;
import de.useweb.backend.ocl.value.IntegerValue;
import de.useweb.backend.ocl.value.ObjectValue;
import de.useweb.backend.ocl.value.OclInvalidValue;
import de.useweb.backend.ocl.value.OclValue;
import de.useweb.backend.ocl.value.OclVoidValue;
import de.useweb.backend.ocl.value.RealValue;
import de.useweb.backend.ocl.value.StringValue;
import de.useweb.backend.ocl.value.TupleValue;
import de.useweb.backend.ocl.value.UnlimitedNaturalValue;

/** Canonical, test-only representation of original USE and backend OCL values. */
final class OriginalUseReferenceValueNormalizer {

    private OriginalUseReferenceValueNormalizer() {
    }

    static NormalizedValue expected(JsonNode referenceCase) {
        JsonNode summary = referenceCase.path("expectedValueSummary");
        String type = referenceCase.path("expectedType").asText(null);
        String raw;
        if (summary.isTextual()) {
            raw = valuePart(summary.asText(), type);
        } else if (summary.isObject() && !summary.path("rawValue").isMissingNode()) {
            raw = summary.path("rawValue").asText();
        } else {
            return null;
        }
        return expectedRaw(raw, type);
    }

    static NormalizedValue expectedRaw(String raw, String type) {
        try {
            return new ExpectedParser(raw, type).parse();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String valuePart(String summary, String expectedType) {
        String value = summary == null ? "" : summary.trim();
        String suffix = expectedType == null ? "" : " : " + expectedType;
        return !suffix.isEmpty() && value.endsWith(suffix)
                ? value.substring(0, value.length() - suffix.length()).trim()
                : value;
    }

    static NormalizedValue observed(OclValue value, UmlModel model) {
        if (value instanceof OclVoidValue) return scalar("VOID", "OclVoid", null);
        if (value instanceof OclInvalidValue) return scalar("INVALID", "OclInvalid", null);
        if (value instanceof IntegerValue integer) return scalar("INTEGER", "Integer", integer.value());
        if (value instanceof RealValue real) return scalar("REAL", "Real", decimal(real.value()));
        if (value instanceof BooleanValue bool) return scalar("BOOLEAN", "Boolean", bool.value());
        if (value instanceof StringValue string) return scalar("STRING", "String", string.value());
        if (value instanceof UnlimitedNaturalValue) return scalar("UNLIMITED", "UnlimitedNatural", "*");
        if (value instanceof EnumValue enumeration) {
            return scalar("ENUM", enumeration.enumerationName(), enumeration.literal());
        }
        if (value instanceof ObjectValue object) {
            String className = model.findClass(object.object().classId())
                    .map(umlClass -> umlClass.name()).orElse("Object");
            return scalar("OBJECT", className, object.object().name());
        }
        if (value instanceof TupleValue tuple) {
            Map<String, NormalizedValue> parts = new LinkedHashMap<>();
            tuple.parts().forEach((name, part) -> parts.put(name, observed(part, model)));
            return new NormalizedValue("TUPLE", "Tuple", null, List.of(), Map.copyOf(parts));
        }
        if (value instanceof CollectionValue collection) {
            List<NormalizedValue> elements = collection.values().stream()
                    .map(element -> observed(element, model)).toList();
            String kind = collection.collectionKind().oclName();
            return collection(kind, collection.typeName(), elements);
        }
        throw new IllegalArgumentException("Unsupported OCL value: " + value.getClass().getSimpleName());
    }

    private static NormalizedValue scalar(String kind, String type, Object value) {
        return new NormalizedValue(kind, type, value, List.of(), Map.of());
    }

    private static NormalizedValue collection(String kind, String type, List<NormalizedValue> source) {
        List<NormalizedValue> values = new ArrayList<>(source);
        if ("Set".equals(kind) || "OrderedSet".equals(kind)) {
            values = new ArrayList<>(new LinkedHashSet<>(values));
        }
        if ("Set".equals(kind) || "Bag".equals(kind)) {
            values.sort(Comparator.comparing(NormalizedValue::canonicalKey));
        }
        return new NormalizedValue("COLLECTION", type, kind, List.copyOf(values), Map.of());
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros();
    }

    static boolean semanticallyEquivalent(NormalizedValue expected, NormalizedValue observed) {
        if (!expected.kind().equals(observed.kind())) return false;
        if (!java.util.Objects.equals(expected.value(), observed.value())) return false;
        if (!expected.parts().keySet().equals(observed.parts().keySet())) return false;
        for (String name : expected.parts().keySet()) {
            if (!semanticallyEquivalent(expected.parts().get(name), observed.parts().get(name))) return false;
        }
        if (expected.elements().size() != observed.elements().size()) return false;
        for (int index = 0; index < expected.elements().size(); index++) {
            if (!semanticallyEquivalent(expected.elements().get(index), observed.elements().get(index))) return false;
        }
        // USE prints an inferred collection element type for contained objects. Runtime
        // classifiers remain semantically relevant in the backend but do not change identity.
        return "OBJECT".equals(expected.kind()) || "VOID".equals(expected.kind())
                || java.util.Objects.equals(expected.type(), observed.type());
    }

    static boolean differsOnlyByUseVoidForInvalid(NormalizedValue expected, NormalizedValue observed) {
        if ("VOID".equals(expected.kind()) && "INVALID".equals(observed.kind())) return true;
        if (!expected.kind().equals(observed.kind())
                || !java.util.Objects.equals(expected.value(), observed.value())
                || !expected.parts().keySet().equals(observed.parts().keySet())
                || expected.elements().size() != observed.elements().size()) return false;
        boolean differs = false;
        for (String name : expected.parts().keySet()) {
            NormalizedValue left = expected.parts().get(name);
            NormalizedValue right = observed.parts().get(name);
            if (semanticallyEquivalent(left, right)) continue;
            if (!differsOnlyByUseVoidForInvalid(left, right)) return false;
            differs = true;
        }
        for (int index = 0; index < expected.elements().size(); index++) {
            NormalizedValue left = expected.elements().get(index);
            NormalizedValue right = observed.elements().get(index);
            if (semanticallyEquivalent(left, right)) continue;
            if (!differsOnlyByUseVoidForInvalid(left, right)) return false;
            differs = true;
        }
        return differs;
    }

    static boolean sameTopLevelElementsIgnoringOrder(NormalizedValue expected, NormalizedValue observed) {
        if (!"COLLECTION".equals(expected.kind()) || !"COLLECTION".equals(observed.kind())
                || !java.util.Objects.equals(expected.value(), observed.value())
                || expected.elements().size() != observed.elements().size()) return false;
        List<String> left = expected.elements().stream().map(NormalizedValue::canonicalKey).sorted().toList();
        List<String> right = observed.elements().stream().map(NormalizedValue::canonicalKey).sorted().toList();
        return left.equals(right);
    }

    record NormalizedValue(String kind, String type, Object value,
            List<NormalizedValue> elements, Map<String, NormalizedValue> parts) {
        String canonicalKey() {
            return kind + '|' + type + '|' + String.valueOf(value) + '|' + elements + '|' + parts;
        }
    }

    private static final class ExpectedParser {
        private final String source;
        private final String expectedType;
        private int index;

        private ExpectedParser(String source, String expectedType) {
            this.source = source == null ? "" : source.trim();
            this.expectedType = expectedType;
        }

        NormalizedValue parse() {
            NormalizedValue value = value(expectedType);
            whitespace();
            if (index != source.length()) throw new IllegalArgumentException("Trailing expected value");
            return value;
        }

        private NormalizedValue value(String typeHint) {
            whitespace();
            if (index == 0 && "'''".equals(source)) {
                index = source.length();
                return scalar("STRING", "String", "'");
            }
            if (peek("Set{") || peek("Bag{") || peek("Sequence{") || peek("OrderedSet{")) {
                return collectionValue(typeHint);
            }
            if (peek("Tuple{")) return tupleValue(typeHint);
            if (peek("null")) {
                index += 4;
                return scalar("OclInvalid".equals(typeHint) ? "INVALID" : "VOID", typeHint, null);
            }
            if (peek("invalid")) {
                index += 7;
                return scalar("INVALID", "OclInvalid", null);
            }
            if (current() == '\'') return scalar("STRING", "String", string());
            String token = token();
            if ("true".equals(token) || "false".equals(token)) {
                return scalar("BOOLEAN", "Boolean", Boolean.valueOf(token));
            }
            if ("*".equals(token)) return scalar("UNLIMITED", "UnlimitedNatural", token);
            if (token.matches("[-+]?\\d+")) return scalar("INTEGER", "Integer", Integer.valueOf(token));
            if (token.matches("[-+]?(?:\\d+\\.\\d*|\\d*\\.\\d+)(?:[eE][-+]?\\d+)?")) {
                return scalar("REAL", "Real", new BigDecimal(token).stripTrailingZeros());
            }
            int enumSeparator = token.indexOf("::");
            if (enumSeparator > 0) {
                return scalar("ENUM", token.substring(0, enumSeparator), token.substring(enumSeparator + 2));
            }
            if (token.contains("{") || token.isBlank()) throw new IllegalArgumentException("Unsupported value");
            return scalar("OBJECT", typeHint, token);
        }

        private NormalizedValue collectionValue(String typeHint) {
            String kind = identifier();
            expect('{');
            List<NormalizedValue> elements = new ArrayList<>();
            String elementType = innerType(typeHint);
            whitespace();
            while (current() != '}') {
                elements.add(value(elementType));
                whitespace();
                if (current() != ',') break;
                index++;
            }
            expect('}');
            return collection(kind, kind, elements);
        }

        private NormalizedValue tupleValue(String typeHint) {
            index += "Tuple".length();
            expect('{');
            Map<String, NormalizedValue> parts = new LinkedHashMap<>();
            whitespace();
            while (current() != '}') {
                String name = identifier();
                expect('=');
                parts.put(name, value(tuplePartType(typeHint, name)));
                whitespace();
                if (current() != ',') break;
                index++;
            }
            expect('}');
            return new NormalizedValue("TUPLE", "Tuple", null, List.of(), Map.copyOf(parts));
        }

        private String string() {
            index++;
            StringBuilder value = new StringBuilder();
            while (index < source.length()) {
                char character = source.charAt(index++);
                if (character == '\'' && index < source.length() && source.charAt(index) == '\'') {
                    value.append('\'');
                    index++;
                } else if (character == '\'') {
                    return value.toString();
                } else {
                    value.append(character);
                }
            }
            throw new IllegalArgumentException("Unclosed string");
        }

        private String identifier() {
            whitespace();
            int start = index;
            while (index < source.length() && (Character.isLetterOrDigit(source.charAt(index))
                    || source.charAt(index) == '_')) index++;
            if (start == index) throw new IllegalArgumentException("Identifier expected");
            return source.substring(start, index);
        }

        private String token() {
            whitespace();
            int start = index;
            while (index < source.length() && ",}".indexOf(source.charAt(index)) < 0
                    && !Character.isWhitespace(source.charAt(index))) index++;
            return source.substring(start, index);
        }

        private void expect(char expected) {
            whitespace();
            if (current() != expected) throw new IllegalArgumentException("Expected " + expected);
            index++;
        }

        private boolean peek(String text) {
            return source.startsWith(text, index);
        }

        private char current() {
            return index < source.length() ? source.charAt(index) : '\0';
        }

        private void whitespace() {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++;
        }

        private static String innerType(String type) {
            if (type == null) return null;
            int opening = type.indexOf('(');
            return opening >= 0 && type.endsWith(")") ? type.substring(opening + 1, type.length() - 1) : null;
        }

        private static String tuplePartType(String type, String partName) {
            if (type == null || !type.startsWith("Tuple(") || !type.endsWith(")")) return null;
            String body = type.substring(6, type.length() - 1);
            for (String part : splitTypeParts(body)) {
                int separator = part.indexOf(':');
                if (separator > 0 && part.substring(0, separator).trim().equals(partName)) {
                    return part.substring(separator + 1).trim();
                }
            }
            return null;
        }

        private static List<String> splitTypeParts(String source) {
            List<String> parts = new ArrayList<>();
            int depth = 0;
            int start = 0;
            for (int i = 0; i < source.length(); i++) {
                char character = source.charAt(i);
                if (character == '(') depth++;
                if (character == ')') depth--;
                if (character == ',' && depth == 0) {
                    parts.add(source.substring(start, i));
                    start = i + 1;
                }
            }
            parts.add(source.substring(start));
            return parts;
        }
    }
}
