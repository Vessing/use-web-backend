package de.useweb.backend.ocl.library;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import de.useweb.backend.ocl.ast.BinaryOperator;
import de.useweb.backend.ocl.collection.CollectionKind;
import de.useweb.backend.ocl.typecheck.OclType;
import de.useweb.backend.ocl.value.BooleanValue;
import de.useweb.backend.ocl.value.IntegerValue;
import de.useweb.backend.ocl.value.OclInvalidValue;
import de.useweb.backend.ocl.value.OclValue;
import de.useweb.backend.ocl.value.OclVoidValue;
import de.useweb.backend.ocl.value.RealValue;
import de.useweb.backend.ocl.value.SequenceValue;
import de.useweb.backend.ocl.value.StringValue;
import de.useweb.backend.ocl.value.UnlimitedNaturalValue;

/** OCL 2.4 numeric and String standard-library operations owned by B11. */
public final class OclPrimitiveLibrary {
    private OclPrimitiveLibrary() {
    }

    public static Optional<OclType> operationType(OclType receiver, String name, List<OclType> arguments) {
        if (receiver.sameTypeAs(OclType.STRING)) {
            if ((name.equals("size") || name.equals("toInteger")) && arguments.isEmpty()) return Optional.of(OclType.INTEGER);
            if (name.equals("toReal") && arguments.isEmpty()) return Optional.of(OclType.REAL);
            if (name.equals("toBoolean") && arguments.isEmpty()) return Optional.of(OclType.BOOLEAN);
            if ((name.equals("toUpperCase") || name.equals("toLowerCase") || name.equals("toString"))
                    && arguments.isEmpty()) return Optional.of(OclType.STRING);
            if ((name.equals("concat") || name.equals("equalsIgnoreCase") || name.equals("indexOf"))
                    && arguments.size() == 1 && arguments.getFirst().conformsTo(OclType.STRING)) {
                return Optional.of(name.equals("equalsIgnoreCase") ? OclType.BOOLEAN
                        : name.equals("indexOf") ? OclType.INTEGER : OclType.STRING);
            }
            if (name.equals("substring") && arguments.size() == 2
                    && arguments.stream().allMatch(type -> type.conformsTo(OclType.INTEGER))) return Optional.of(OclType.STRING);
            if (name.equals("at") && arguments.size() == 1 && arguments.getFirst().conformsTo(OclType.INTEGER)) return Optional.of(OclType.STRING);
            if (name.equals("characters") && arguments.isEmpty()) {
                return Optional.of(OclType.collectionOf(CollectionKind.SEQUENCE, OclType.STRING));
            }
        }
        if (receiver.isNumeric()) {
            if (name.equals("abs") && arguments.isEmpty()) return Optional.of(receiver);
            if ((name.equals("floor") || name.equals("round") || name.equals("toInteger")) && arguments.isEmpty()) {
                return Optional.of(OclType.INTEGER);
            }
            if (name.equals("toString") && arguments.isEmpty()) return Optional.of(OclType.STRING);
            if ((name.equals("max") || name.equals("min")) && arguments.size() == 1 && arguments.getFirst().isNumeric()) {
                return Optional.of(receiver.leastUpperBound(arguments.getFirst()));
            }
        }
        if (receiver.sameTypeAs(OclType.BOOLEAN) && name.equals("toString") && arguments.isEmpty()) {
            return Optional.of(OclType.STRING);
        }
        return Optional.empty();
    }

    public static Optional<OclValue> evaluate(OclValue receiver, String name, List<OclValue> arguments) {
        if (!supports(receiver, name)) return Optional.empty();
        if (receiver instanceof OclInvalidValue || receiver instanceof OclVoidValue
                || arguments.stream().anyMatch(value -> value instanceof OclInvalidValue || value instanceof OclVoidValue)) {
            return Optional.of(OclInvalidValue.INSTANCE);
        }
        if (receiver instanceof StringValue string) return Optional.of(stringOperation(string, name, arguments));
        if (isNumeric(receiver)) return Optional.of(numericOperation(receiver, name, arguments));
        if (receiver instanceof BooleanValue booleanValue && name.equals("toString") && arguments.isEmpty()) {
            return Optional.of(new StringValue(Boolean.toString(booleanValue.value())));
        }
        return Optional.empty();
    }

    public static OclValue negate(OclValue value) {
        if (value instanceof OclInvalidValue || value instanceof OclVoidValue || value instanceof UnlimitedNaturalValue) {
            return OclInvalidValue.INSTANCE;
        }
        if (value instanceof IntegerValue integer) {
            try {
                return new IntegerValue(Math.negateExact(integer.value()));
            } catch (ArithmeticException ignored) {
                return OclInvalidValue.INSTANCE;
            }
        }
        if (value instanceof RealValue real) return finiteReal(-real.value());
        return OclInvalidValue.INSTANCE;
    }

    public static OclValue arithmetic(OclValue left, OclValue right, BinaryOperator operator) {
        if (left instanceof OclInvalidValue || left instanceof OclVoidValue
                || right instanceof OclInvalidValue || right instanceof OclVoidValue) return OclInvalidValue.INSTANCE;
        if (operator == BinaryOperator.ADD && left instanceof StringValue l && right instanceof StringValue r) {
            return new StringValue(l.value() + r.value());
        }
        if (!isNumeric(left) || !isNumeric(right)) return OclInvalidValue.INSTANCE;
        if (left instanceof UnlimitedNaturalValue || right instanceof UnlimitedNaturalValue) return OclInvalidValue.INSTANCE;
        if ((operator == BinaryOperator.INTEGER_DIVIDE || operator == BinaryOperator.MODULO)
                && left instanceof IntegerValue l && right instanceof IntegerValue r) {
            if (r.value() == 0 || l.value() == Integer.MIN_VALUE && r.value() == -1) return OclInvalidValue.INSTANCE;
            return new IntegerValue(operator == BinaryOperator.INTEGER_DIVIDE
                    ? l.value() / r.value() : l.value() % r.value());
        }
        if (operator == BinaryOperator.DIVIDE) {
            double divisor = number(right);
            return divisor == 0.0 ? OclInvalidValue.INSTANCE : finiteReal(number(left) / divisor);
        }
        if (left instanceof IntegerValue l && right instanceof IntegerValue r) {
            try {
                return new IntegerValue(switch (operator) {
                    case ADD -> Math.addExact(l.value(), r.value());
                    case SUBTRACT -> Math.subtractExact(l.value(), r.value());
                    case MULTIPLY -> Math.multiplyExact(l.value(), r.value());
                    default -> throw new ArithmeticException("unsupported integer operator");
                });
            } catch (ArithmeticException ignored) {
                return OclInvalidValue.INSTANCE;
            }
        }
        return finiteReal(switch (operator) {
            case ADD -> number(left) + number(right);
            case SUBTRACT -> number(left) - number(right);
            case MULTIPLY -> number(left) * number(right);
            default -> Double.NaN;
        });
    }

    public static OclValue compare(OclValue left, OclValue right, BinaryOperator operator) {
        if (left instanceof OclInvalidValue || left instanceof OclVoidValue
                || right instanceof OclInvalidValue || right instanceof OclVoidValue) return OclInvalidValue.INSTANCE;
        int comparison;
        if (left instanceof StringValue l && right instanceof StringValue r) comparison = l.value().compareTo(r.value());
        else if (left instanceof UnlimitedNaturalValue && right instanceof UnlimitedNaturalValue) comparison = 0;
        else if (left instanceof UnlimitedNaturalValue && isNumeric(right)) comparison = 1;
        else if (right instanceof UnlimitedNaturalValue && isNumeric(left)) comparison = -1;
        else if (isNumeric(left) && isNumeric(right)) comparison = Double.compare(number(left), number(right));
        else return OclInvalidValue.INSTANCE;
        return new BooleanValue(switch (operator) {
            case LESS -> comparison < 0;
            case LESS_EQUAL -> comparison <= 0;
            case GREATER -> comparison > 0;
            case GREATER_EQUAL -> comparison >= 0;
            default -> false;
        });
    }

    private static OclValue stringOperation(StringValue string, String name, List<OclValue> arguments) {
        int[] codePoints = string.value().codePoints().toArray();
        return switch (name) {
            case "size" -> new IntegerValue(codePoints.length);
            case "concat" -> arguments.getFirst() instanceof StringValue value
                    ? new StringValue(string.value() + value.value()) : OclInvalidValue.INSTANCE;
            case "substring" -> substring(codePoints, arguments);
            case "toUpperCase" -> new StringValue(string.value().toUpperCase(Locale.ROOT));
            case "toLowerCase" -> new StringValue(string.value().toLowerCase(Locale.ROOT));
            case "indexOf" -> arguments.getFirst() instanceof StringValue value
                    ? new IntegerValue(codePointIndexOf(string.value(), value.value())) : OclInvalidValue.INSTANCE;
            case "equalsIgnoreCase" -> arguments.getFirst() instanceof StringValue value
                    ? new BooleanValue(string.value().toUpperCase(Locale.ROOT).equals(value.value().toUpperCase(Locale.ROOT)))
                    : OclInvalidValue.INSTANCE;
            case "at" -> arguments.getFirst() instanceof IntegerValue index && index.value() > 0 && index.value() <= codePoints.length
                    ? new StringValue(new String(codePoints, index.value() - 1, 1)) : OclInvalidValue.INSTANCE;
            case "characters" -> characters(codePoints);
            case "toBoolean" -> new BooleanValue(string.value().equals("true"));
            case "toInteger" -> parseInteger(string.value());
            case "toReal" -> parseReal(string.value());
            case "toString" -> string;
            default -> OclInvalidValue.INSTANCE;
        };
    }

    private static OclValue numericOperation(OclValue receiver, String name, List<OclValue> arguments) {
        if (receiver instanceof UnlimitedNaturalValue) {
            return switch (name) {
                case "max", "min" -> arguments.size() == 1 && arguments.getFirst() instanceof UnlimitedNaturalValue
                        ? UnlimitedNaturalValue.UNLIMITED : OclInvalidValue.INSTANCE;
                case "toString" -> new StringValue("*");
                case "toInteger", "abs", "floor", "round" -> OclInvalidValue.INSTANCE;
                default -> OclInvalidValue.INSTANCE;
            };
        }
        return switch (name) {
            case "abs" -> abs(receiver);
            case "floor" -> boundedInteger(Math.floor(number(receiver)));
            case "round" -> boundedInteger(Math.floor(number(receiver) + 0.5));
            case "max", "min" -> numericMinMax(receiver, arguments, name.equals("max"));
            case "toInteger" -> receiver instanceof IntegerValue ? receiver : OclInvalidValue.INSTANCE;
            case "toString" -> new StringValue(receiver instanceof IntegerValue integer
                    ? Integer.toString(integer.value()) : Double.toString(((RealValue) receiver).value()));
            default -> OclInvalidValue.INSTANCE;
        };
    }

    private static OclValue abs(OclValue value) {
        if (value instanceof IntegerValue integer) {
            if (integer.value() == Integer.MIN_VALUE) return OclInvalidValue.INSTANCE;
            return new IntegerValue(Math.abs(integer.value()));
        }
        return finiteReal(Math.abs(((RealValue) value).value()));
    }

    private static OclValue numericMinMax(OclValue receiver, List<OclValue> arguments, boolean maximum) {
        if (arguments.size() != 1 || !isNumeric(arguments.getFirst())) return OclInvalidValue.INSTANCE;
        OclValue argument = arguments.getFirst();
        if (argument instanceof UnlimitedNaturalValue) return maximum ? argument : receiver;
        double result = maximum ? Math.max(number(receiver), number(argument)) : Math.min(number(receiver), number(argument));
        return receiver instanceof IntegerValue && argument instanceof IntegerValue
                ? new IntegerValue((int) result) : finiteReal(result);
    }

    private static OclValue substring(int[] codePoints, List<OclValue> arguments) {
        if (arguments.size() != 2 || !(arguments.get(0) instanceof IntegerValue lower)
                || !(arguments.get(1) instanceof IntegerValue upper)
                || lower.value() < 1 || upper.value() < lower.value() || upper.value() > codePoints.length) {
            return OclInvalidValue.INSTANCE;
        }
        return new StringValue(new String(codePoints, lower.value() - 1, upper.value() - lower.value() + 1));
    }

    private static SequenceValue characters(int[] codePoints) {
        List<OclValue> values = new ArrayList<>(codePoints.length);
        for (int codePoint : codePoints) values.add(new StringValue(new String(new int[] { codePoint }, 0, 1)));
        return new SequenceValue(values);
    }

    private static int codePointIndexOf(String source, String target) {
        if (source.isEmpty()) return 0;
        if (target.isEmpty()) return 1;
        int charIndex = source.indexOf(target);
        return charIndex < 0 ? 0 : source.codePointCount(0, charIndex) + 1;
    }

    private static OclValue parseInteger(String value) {
        try {
            return new IntegerValue(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return OclInvalidValue.INSTANCE;
        }
    }

    private static OclValue parseReal(String value) {
        try {
            return finiteReal(Double.parseDouble(value));
        } catch (NumberFormatException ignored) {
            return OclInvalidValue.INSTANCE;
        }
    }

    private static OclValue boundedInteger(double value) {
        return Double.isFinite(value) && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE
                ? new IntegerValue((int) value) : OclInvalidValue.INSTANCE;
    }

    private static OclValue finiteReal(double value) {
        return Double.isFinite(value) ? new RealValue(value) : OclInvalidValue.INSTANCE;
    }

    private static boolean supports(OclValue receiver, String name) {
        if (receiver instanceof StringValue) return List.of("size", "concat", "substring", "toUpperCase", "toLowerCase",
                "indexOf", "equalsIgnoreCase", "at", "characters", "toBoolean", "toInteger", "toReal", "toString").contains(name);
        if (isNumeric(receiver)) return List.of("abs", "floor", "round", "max", "min", "toInteger", "toString").contains(name);
        if (receiver instanceof BooleanValue) return name.equals("toString");
        return false;
    }

    private static boolean isNumeric(OclValue value) {
        return value instanceof IntegerValue || value instanceof RealValue || value instanceof UnlimitedNaturalValue;
    }

    private static double number(OclValue value) {
        return value instanceof IntegerValue integer ? integer.value() : ((RealValue) value).value();
    }
}
