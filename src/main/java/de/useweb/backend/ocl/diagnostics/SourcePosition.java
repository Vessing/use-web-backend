package de.useweb.backend.ocl.diagnostics;

public record SourcePosition(int line, int column, int offset) {

    public SourcePosition {
        if (line < 1) {
            throw new IllegalArgumentException("line must be >= 1");
        }
        if (column < 1) {
            throw new IllegalArgumentException("column must be >= 1");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
    }
}
