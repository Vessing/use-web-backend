package de.useweb.backend.ocl.definition;

public class OclDefinitionEvaluationException extends RuntimeException {
    private final String code;

    public OclDefinitionEvaluationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
