package de.useweb.backend.ocl.diagnostics;

public record SourceRange(SourcePosition start, SourcePosition end) {

    public SourceRange {
        if (start == null) {
            throw new IllegalArgumentException("start must not be null");
        }
        if (end == null) {
            throw new IllegalArgumentException("end must not be null");
        }
        if (end.offset() < start.offset()) {
            throw new IllegalArgumentException("end must not precede start");
        }
    }
}
