package de.useweb.backend.domain.uml;

import java.util.List;

public record UmlOperation(
        UmlOperationId id,
        String name,
        UmlType returnType,
        List<UmlParameter> parameters,
        String bodyExpression,
        UmlVisibility visibility,
        boolean abstractOperation,
        boolean query,
        boolean staticOperation,
        List<UmlOperationContract> contracts,
        List<UmlOperationId> redefinedOperationIds) {

    public UmlOperation(UmlOperationId id, String name, UmlType returnType, List<UmlParameter> parameters,
            String bodyExpression, UmlVisibility visibility, boolean abstractOperation, boolean query,
            List<UmlOperationContract> contracts) {
        this(id, name, returnType, parameters, bodyExpression, visibility, abstractOperation, query, false, contracts, List.of());
    }

    public UmlOperation(UmlOperationId id, String name, UmlType returnType, List<UmlParameter> parameters,
            String bodyExpression) {
        this(id, name, returnType, parameters, bodyExpression, UmlVisibility.PUBLIC, false, false, List.of(), List.of());
    }

    public UmlOperation(UmlOperationId id, String name, UmlType returnType, List<UmlParameter> parameters,
            String bodyExpression, UmlVisibility visibility) {
        this(id, name, returnType, parameters, bodyExpression, visibility, false, false, List.of(), List.of());
    }

    public UmlOperation(UmlOperationId id, String name, UmlType returnType, List<UmlParameter> parameters) {
        this(id, name, returnType, parameters, null, UmlVisibility.PUBLIC, false, false, false, List.of(), List.of());
    }

    public UmlOperation(UmlOperationId id, String name, UmlType returnType, List<UmlParameter> parameters,
            String bodyExpression, UmlVisibility visibility, boolean abstractOperation, boolean query) {
        this(id, name, returnType, parameters, bodyExpression, visibility, abstractOperation, query, false, List.of(), List.of());
    }

    public UmlOperation(UmlOperationId id, String name, UmlType returnType, List<UmlParameter> parameters,
            String bodyExpression, UmlVisibility visibility, boolean abstractOperation, boolean query,
            List<UmlOperationContract> contracts, List<UmlOperationId> redefinedOperationIds) {
        this(id, name, returnType, parameters, bodyExpression, visibility, abstractOperation, query, false,
                contracts, redefinedOperationIds);
    }

    public UmlOperation {
        if (id == null) {
            throw new IllegalArgumentException("UmlOperation id must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("UmlOperation name must not be blank");
        }
        if (returnType == null) {
            throw new IllegalArgumentException("UmlOperation returnType must not be null");
        }
        parameters = List.copyOf(parameters == null ? List.of() : parameters);
        contracts = List.copyOf(contracts == null ? List.of() : contracts);
        redefinedOperationIds = List.copyOf(redefinedOperationIds == null ? List.of() : redefinedOperationIds);
        if (redefinedOperationIds.contains(id) || redefinedOperationIds.stream().distinct().count() != redefinedOperationIds.size()) {
            throw new IllegalArgumentException("Operation redefinition targets must be unique and must not reference self");
        }
        if (contracts.stream().map(UmlOperationContract::id).distinct().count() != contracts.size()) {
            throw new IllegalArgumentException("Operation contract ids must be unique");
        }
        bodyExpression = bodyExpression == null || bodyExpression.isBlank() ? null : bodyExpression.trim();
        visibility = UmlVisibility.defaulted(visibility);
    }
}
