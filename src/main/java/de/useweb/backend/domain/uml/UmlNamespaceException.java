package de.useweb.backend.domain.uml;

import java.util.Map;

public final class UmlNamespaceException extends IllegalArgumentException {
    private final String code;
    private final Map<String, Object> details;

    public UmlNamespaceException(String code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = Map.copyOf(details == null ? Map.of() : details);
    }

    public String code() { return code; }
    public Map<String, Object> details() { return details; }
}
