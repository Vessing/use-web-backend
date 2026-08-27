package de.useweb.backend.ocl.ast;

public enum UnaryOperator {
    NOT("not"),
    NEGATE("-");

    private final String symbol;

    UnaryOperator(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }
}
