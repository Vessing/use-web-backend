package de.useweb.backend.ocl.contract;

public record OperationContractId(String value) {
    public OperationContractId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Operation contract id must not be blank");
        }
    }
}
