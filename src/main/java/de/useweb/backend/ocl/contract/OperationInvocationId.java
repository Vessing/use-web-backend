package de.useweb.backend.ocl.contract;

public record OperationInvocationId(String value) {
    public OperationInvocationId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Operation invocation id must not be blank");
        }
    }
}
