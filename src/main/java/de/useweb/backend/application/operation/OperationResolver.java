package de.useweb.backend.application.operation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlOperation;
import de.useweb.backend.domain.uml.UmlOperationId;

public class OperationResolver {
    public ResolvedOperation resolve(UmlModel model, UmlClassId runtimeClassId, UmlOperationId requestedId) {
        UmlClass runtimeClass = model.findClass(runtimeClassId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown receiver runtime class"));
        var requested = model.classes().stream()
                .flatMap(owner -> owner.operations().stream().filter(operation -> operation.id().equals(requestedId))
                        .map(operation -> new ResolvedOperation(owner, operation, owner, operation)))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown operation"));
        if (!model.isSubtypeOf(runtimeClassId, requested.requestedOwner().id())) {
            throw new IllegalArgumentException("Receiver is incompatible with operation owner");
        }

        Map<UmlClassId, Integer> distances = distances(model, runtimeClass);
        List<ResolvedOperation> candidates = new ArrayList<>();
        for (var owner : model.classes()) {
            if (!distances.containsKey(owner.id())) continue;
            owner.operations().stream().filter(candidate -> sameSignature(requested.requested(), candidate))
                    .forEach(candidate -> candidates.add(new ResolvedOperation(
                            requested.requestedOwner(), requested.requested(), owner, candidate)));
        }
        int minimum = candidates.stream().mapToInt(candidate -> distances.get(candidate.owner().id())).min().orElseThrow();
        List<ResolvedOperation> nearest = candidates.stream()
                .filter(candidate -> distances.get(candidate.owner().id()) == minimum).toList();
        if (nearest.size() != 1) throw new IllegalArgumentException("Ambiguous operation dispatch");
        return nearest.getFirst();
    }

    private Map<UmlClassId, Integer> distances(UmlModel model, UmlClass runtimeClass) {
        Map<UmlClassId, Integer> result = new HashMap<>();
        ArrayDeque<UmlClassId> queue = new ArrayDeque<>();
        result.put(runtimeClass.id(), 0);
        queue.add(runtimeClass.id());
        while (!queue.isEmpty()) {
            UmlClass current = model.findClass(queue.remove()).orElseThrow();
            for (UmlClassId parent : current.superClassIds()) {
                if (result.putIfAbsent(parent, result.get(current.id()) + 1) == null) queue.add(parent);
            }
        }
        return result;
    }

    private boolean sameSignature(UmlOperation left, UmlOperation right) {
        return left.name().equals(right.name())
                && left.parameters().stream().map(parameter -> parameter.type()).toList()
                        .equals(right.parameters().stream().map(parameter -> parameter.type()).toList());
    }
}
