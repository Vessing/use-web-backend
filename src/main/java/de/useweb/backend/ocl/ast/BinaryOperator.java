package de.useweb.backend.ocl.ast;

public enum BinaryOperator {
    EQUAL("="),
    NOT_EQUAL("<>"),
    LESS("<"),
    LESS_EQUAL("<="),
    GREATER(">"),
    GREATER_EQUAL(">="),
    AND("and"),
    OR("or"),
    XOR("xor"),
    IMPLIES("implies"),
    ADD("+"),
    SUBTRACT("-"),
    MULTIPLY("*"),
    DIVIDE("/"),
    INTEGER_DIVIDE("div"),
    MODULO("mod");

    private final String symbol;

    BinaryOperator(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }
}
