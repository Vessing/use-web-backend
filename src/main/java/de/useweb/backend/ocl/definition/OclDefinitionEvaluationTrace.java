package de.useweb.backend.ocl.definition;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import de.useweb.backend.ocl.value.OclValue;

/** State shared only inside one top-level OCL evaluation and therefore one snapshot. */
public final class OclDefinitionEvaluationTrace {
    private final Map<String, OclValue> values = new LinkedHashMap<>();
    private final Map<String, Set<String>> dependencies = new LinkedHashMap<>();

    public Optional<OclValue> cached(String key) {
        return Optional.ofNullable(values.get(key));
    }

    public void cache(String key, OclValue value) {
        values.put(key, value);
    }

    public void dependsOn(String source, String target) {
        if (source != null && !source.equals(target)) {
            dependencies.computeIfAbsent(source, ignored -> new LinkedHashSet<>()).add(target);
        }
    }

    public Map<String, Set<String>> dependencyGraph() {
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        dependencies.forEach((key, value) -> copy.put(key, Set.copyOf(value)));
        return Map.copyOf(copy);
    }
}
