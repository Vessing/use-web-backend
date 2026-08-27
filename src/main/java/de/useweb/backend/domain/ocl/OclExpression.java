package de.useweb.backend.domain.ocl;

public record OclExpression(OclExpressionId id, String text, String languageVersion) {

    public OclExpression {
        if (id == null) {
            throw new IllegalArgumentException("OclExpression id must not be null");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("OclExpression text must not be blank");
        }
        if (languageVersion == null || languageVersion.isBlank()) {
            languageVersion = "mvp-ocl";
        }
    }
}
