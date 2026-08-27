package de.useweb.backend.application.operation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import de.useweb.backend.domain.uml.UmlOperationId;

@Component
public class OperationImplementationRegistry {
    private final Map<UmlOperationId, OperationImplementation> implementations = new ConcurrentHashMap<>();

    public OperationImplementationRegistry(List<OperationImplementation> initial) {
        (initial == null ? List.<OperationImplementation>of() : initial).forEach(this::register);
    }

    public OperationImplementationRegistry() {
        this(List.of());
    }

    public void register(OperationImplementation implementation) {
        if (implementations.putIfAbsent(implementation.operationId(), implementation) != null) {
            throw new IllegalArgumentException("Operation implementation is already registered");
        }
    }

    public Optional<OperationImplementation> find(UmlOperationId id) {
        return Optional.ofNullable(implementations.get(id));
    }
}
