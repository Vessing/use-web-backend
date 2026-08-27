package de.useweb.backend.ocl.lexer;

import de.useweb.backend.ocl.diagnostics.SourceRange;

public record OclToken(OclTokenType type, String text, SourceRange sourceRange) {

    public OclToken {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        if (sourceRange == null) {
            throw new IllegalArgumentException("sourceRange must not be null");
        }
    }
}
