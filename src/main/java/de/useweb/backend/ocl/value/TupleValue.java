package de.useweb.backend.ocl.value;

import java.util.LinkedHashMap;
import java.util.Map;

public record TupleValue(Map<String, OclValue> parts) implements OclValue {
    public TupleValue {
        parts = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(parts));
    }

    @Override
    public String typeName() {
        return "Tuple";
    }

    @Override
    public Object rawValue() {
        Map<String, Object> values = new LinkedHashMap<>();
        parts.forEach((name, value) -> values.put(name, value.rawValue()));
        return values;
    }
}
