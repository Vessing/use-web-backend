package de.useweb.backend.ocl.value;

import java.util.ArrayList;
import java.util.List;

final class CollectionValues {
    private CollectionValues() {
    }

    static List<OclValue> distinct(List<OclValue> source) {
        List<OclValue> result = new ArrayList<>();
        for (OclValue value : source == null ? List.<OclValue>of() : source) {
            if (result.stream().noneMatch(existing -> OclValueEquality.equal(existing, value))) {
                result.add(value);
            }
        }
        return List.copyOf(result);
    }
}
