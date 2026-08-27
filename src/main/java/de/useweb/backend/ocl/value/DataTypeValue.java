package de.useweb.backend.ocl.value;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import de.useweb.backend.domain.uml.UmlDataTypeId;

public record DataTypeValue(UmlDataTypeId dataTypeId, String dataTypeName,
        Map<String, OclValue> properties) implements OclValue {
    public DataTypeValue {
        if (dataTypeId == null || dataTypeName == null || dataTypeName.isBlank()) {
            throw new IllegalArgumentException("DataType value metadata must not be blank");
        }
        properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties == null ? Map.of() : properties));
    }

    @Override public String typeName() { return dataTypeName; }

    @Override public Object rawValue() {
        Map<String, Object> result = new LinkedHashMap<>();
        properties.forEach((name, value) -> result.put(name, value.rawValue()));
        return result;
    }
}
