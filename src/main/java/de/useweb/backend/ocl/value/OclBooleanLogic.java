package de.useweb.backend.ocl.value;

import java.util.Objects;

/** Central OCL four-valued Boolean and equality semantics. */
public final class OclBooleanLogic {
    private OclBooleanLogic() {
    }

    public static OclValue not(OclValue value) {
        return switch (state(value)) {
            case TRUE -> new BooleanValue(false);
            case FALSE -> new BooleanValue(true);
            case NULL -> OclVoidValue.INSTANCE;
            case INVALID, NON_BOOLEAN -> OclInvalidValue.INSTANCE;
        };
    }

    public static OclValue and(OclValue left, OclValue right) {
        State l = state(left);
        State r = state(right);
        if (l == State.FALSE || r == State.FALSE) return new BooleanValue(false);
        if (l == State.INVALID || r == State.INVALID || l == State.NON_BOOLEAN || r == State.NON_BOOLEAN) return OclInvalidValue.INSTANCE;
        if (l == State.NULL || r == State.NULL) return OclVoidValue.INSTANCE;
        return new BooleanValue(true);
    }

    public static OclValue or(OclValue left, OclValue right) {
        State l = state(left);
        State r = state(right);
        if (l == State.TRUE || r == State.TRUE) return new BooleanValue(true);
        if (l == State.INVALID || r == State.INVALID || l == State.NON_BOOLEAN || r == State.NON_BOOLEAN) return OclInvalidValue.INSTANCE;
        if (l == State.NULL || r == State.NULL) return OclVoidValue.INSTANCE;
        return new BooleanValue(false);
    }

    public static OclValue xor(OclValue left, OclValue right) {
        State l = state(left);
        State r = state(right);
        if (l == State.INVALID || r == State.INVALID || l == State.NON_BOOLEAN || r == State.NON_BOOLEAN) return OclInvalidValue.INSTANCE;
        if (l == State.NULL || r == State.NULL) return OclVoidValue.INSTANCE;
        return new BooleanValue(l != r);
    }

    public static OclValue implies(OclValue left, OclValue right) {
        State l = state(left);
        State r = state(right);
        if (l == State.FALSE || r == State.TRUE) return new BooleanValue(true);
        if (l == State.INVALID || r == State.INVALID || l == State.NON_BOOLEAN || r == State.NON_BOOLEAN) return OclInvalidValue.INSTANCE;
        if (l == State.NULL || r == State.NULL) return OclVoidValue.INSTANCE;
        return new BooleanValue(false);
    }

    public static OclValue equal(OclValue left, OclValue right) {
        if (left instanceof OclInvalidValue || right instanceof OclInvalidValue) return OclInvalidValue.INSTANCE;
        if (left instanceof OclVoidValue || right instanceof OclVoidValue) {
            return new BooleanValue(left instanceof OclVoidValue && right instanceof OclVoidValue);
        }
        return OclValueEquality.semanticEqual(left, right);
    }

    public static OclValue notEqual(OclValue left, OclValue right) {
        OclValue equal = equal(left, right);
        return equal instanceof BooleanValue booleanValue ? new BooleanValue(!booleanValue.value()) : equal;
    }

    private static State state(OclValue value) {
        Objects.requireNonNull(value, "OCL values must use OclVoidValue or OclInvalidValue instead of Java null");
        if (value instanceof BooleanValue booleanValue) return booleanValue.value() ? State.TRUE : State.FALSE;
        if (value instanceof OclVoidValue) return State.NULL;
        if (value instanceof OclInvalidValue) return State.INVALID;
        return State.NON_BOOLEAN;
    }

    private enum State { TRUE, FALSE, NULL, INVALID, NON_BOOLEAN }
}
