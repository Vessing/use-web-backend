package de.useweb.backend.ocl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.useweb.backend.ocl.ast.BinaryOperator;
import de.useweb.backend.ocl.collection.CollectionKind;
import de.useweb.backend.ocl.library.OclPrimitiveLibrary;
import de.useweb.backend.ocl.typecheck.OclType;
import de.useweb.backend.ocl.value.BooleanValue;
import de.useweb.backend.ocl.value.IntegerValue;
import de.useweb.backend.ocl.value.OclInvalidValue;
import de.useweb.backend.ocl.value.RealValue;
import de.useweb.backend.ocl.value.SequenceValue;
import de.useweb.backend.ocl.value.StringValue;
import de.useweb.backend.ocl.value.UnlimitedNaturalValue;

class OclPrimitiveLibraryTest {
    @Test
    void exposesTheOcl24StringSignatures() {
        assertType("size", List.of(), OclType.INTEGER);
        assertType("concat", List.of(OclType.STRING), OclType.STRING);
        assertType("substring", List.of(OclType.INTEGER, OclType.INTEGER), OclType.STRING);
        assertType("toUpperCase", List.of(), OclType.STRING);
        assertType("toLowerCase", List.of(), OclType.STRING);
        assertType("indexOf", List.of(OclType.STRING), OclType.INTEGER);
        assertType("equalsIgnoreCase", List.of(OclType.STRING), OclType.BOOLEAN);
        assertType("at", List.of(OclType.INTEGER), OclType.STRING);
        assertType("characters", List.of(), OclType.collectionOf(CollectionKind.SEQUENCE, OclType.STRING));
        assertType("toBoolean", List.of(), OclType.BOOLEAN);
        assertType("toInteger", List.of(), OclType.INTEGER);
        assertType("toReal", List.of(), OclType.REAL);
        assertType("toString", List.of(), OclType.STRING);
    }

    @Test
    void evaluatesUnicodeStringOperationsByOclCharacterPosition() {
        StringValue source = new StringValue("A\uD83D\uDE00b");

        assertThat(value(source, "size")).isEqualTo(new IntegerValue(3));
        assertThat(value(source, "substring", new IntegerValue(2), new IntegerValue(3)))
                .isEqualTo(new StringValue("\uD83D\uDE00b"));
        assertThat(value(source, "at", new IntegerValue(2))).isEqualTo(new StringValue("\uD83D\uDE00"));
        assertThat(value(source, "indexOf", new StringValue("b"))).isEqualTo(new IntegerValue(3));
        assertThat(value(source, "characters")).isEqualTo(new SequenceValue(List.of(
                new StringValue("A"), new StringValue("\uD83D\uDE00"), new StringValue("b"))));
    }

    @Test
    void evaluatesEveryStringConversionAndQuery() {
        assertThat(value(new StringValue("Use"), "concat", new StringValue("Web"))).isEqualTo(new StringValue("UseWeb"));
        assertThat(value(new StringValue("Use"), "equalsIgnoreCase", new StringValue("uSE"))).isEqualTo(new BooleanValue(true));
        assertThat(value(new StringValue("Use"), "toUpperCase")).isEqualTo(new StringValue("USE"));
        assertThat(value(new StringValue("Use"), "toLowerCase")).isEqualTo(new StringValue("use"));
        assertThat(value(new StringValue("true"), "toBoolean")).isEqualTo(new BooleanValue(true));
        assertThat(value(new StringValue("TRUE"), "toBoolean")).isEqualTo(new BooleanValue(false));
        assertThat(value(new StringValue("42"), "toInteger")).isEqualTo(new IntegerValue(42));
        assertThat(value(new StringValue("3.5"), "toReal")).isEqualTo(new RealValue(3.5));
        assertThat(value(new StringValue("x"), "toString")).isEqualTo(new StringValue("x"));
        assertThat(value(new StringValue("abc"), "substring", new IntegerValue(0), new IntegerValue(2)))
                .isEqualTo(OclInvalidValue.INSTANCE);
        assertThat(value(new StringValue("not-a-number"), "toReal")).isEqualTo(OclInvalidValue.INSTANCE);
    }

    @Test
    void evaluatesNumericOperationsPromotionRoundingAndOverflow() {
        assertThat(OclPrimitiveLibrary.arithmetic(new IntegerValue(5), new IntegerValue(2), BinaryOperator.DIVIDE))
                .isEqualTo(new RealValue(2.5));
        assertThat(OclPrimitiveLibrary.arithmetic(new IntegerValue(-5), new IntegerValue(2), BinaryOperator.INTEGER_DIVIDE))
                .isEqualTo(new IntegerValue(-2));
        assertThat(OclPrimitiveLibrary.arithmetic(new IntegerValue(-5), new IntegerValue(2), BinaryOperator.MODULO))
                .isEqualTo(new IntegerValue(-1));
        assertThat(OclPrimitiveLibrary.arithmetic(new IntegerValue(Integer.MAX_VALUE), new IntegerValue(1), BinaryOperator.ADD))
                .isEqualTo(OclInvalidValue.INSTANCE);
        assertThat(OclPrimitiveLibrary.negate(new IntegerValue(Integer.MIN_VALUE))).isEqualTo(OclInvalidValue.INSTANCE);
        assertThat(value(new RealValue(-1.5), "round")).isEqualTo(new IntegerValue(-1));
        assertThat(value(new RealValue(-1.2), "floor")).isEqualTo(new IntegerValue(-2));
        assertThat(value(new IntegerValue(-4), "abs")).isEqualTo(new IntegerValue(4));
        assertThat(value(new IntegerValue(4), "max", new RealValue(4.5))).isEqualTo(new RealValue(4.5));
        assertThat(value(new IntegerValue(4), "toString")).isEqualTo(new StringValue("4"));
    }

    @Test
    void handlesUnlimitedNaturalWithoutTreatingTheStarAsAnInteger() {
        assertThat(value(UnlimitedNaturalValue.UNLIMITED, "toInteger")).isEqualTo(OclInvalidValue.INSTANCE);
        assertThat(value(UnlimitedNaturalValue.UNLIMITED, "toString")).isEqualTo(new StringValue("*"));
        assertThat(value(UnlimitedNaturalValue.UNLIMITED, "max", UnlimitedNaturalValue.UNLIMITED))
                .isEqualTo(UnlimitedNaturalValue.UNLIMITED);
        assertThat(OclPrimitiveLibrary.compare(UnlimitedNaturalValue.UNLIMITED, new IntegerValue(5), BinaryOperator.GREATER))
                .isEqualTo(new BooleanValue(true));
        assertThat(OclPrimitiveLibrary.arithmetic(UnlimitedNaturalValue.UNLIMITED, UnlimitedNaturalValue.UNLIMITED,
                BinaryOperator.ADD)).isEqualTo(OclInvalidValue.INSTANCE);
    }

    @Test
    void exposesEveryNumericOperationSignature() {
        assertType(OclType.INTEGER, "abs", List.of(), OclType.INTEGER);
        assertType(OclType.INTEGER, "max", List.of(OclType.INTEGER), OclType.INTEGER);
        assertType(OclType.INTEGER, "min", List.of(OclType.REAL), OclType.REAL);
        assertType(OclType.INTEGER, "toString", List.of(), OclType.STRING);
        assertType(OclType.REAL, "abs", List.of(), OclType.REAL);
        assertType(OclType.REAL, "floor", List.of(), OclType.INTEGER);
        assertType(OclType.REAL, "round", List.of(), OclType.INTEGER);
        assertType(OclType.REAL, "max", List.of(OclType.INTEGER), OclType.REAL);
        assertType(OclType.REAL, "min", List.of(OclType.REAL), OclType.REAL);
        assertType(OclType.UNLIMITED_NATURAL, "toInteger", List.of(), OclType.INTEGER);
        assertType(OclType.UNLIMITED_NATURAL, "toString", List.of(), OclType.STRING);
    }

    private static void assertType(String name, List<OclType> arguments, OclType expected) {
        assertType(OclType.STRING, name, arguments, expected);
    }

    private static void assertType(OclType receiver, String name, List<OclType> arguments, OclType expected) {
        assertThat(OclPrimitiveLibrary.operationType(receiver, name, arguments)).contains(expected);
    }

    private static de.useweb.backend.ocl.value.OclValue value(
            de.useweb.backend.ocl.value.OclValue receiver, String name,
            de.useweb.backend.ocl.value.OclValue... arguments) {
        return OclPrimitiveLibrary.evaluate(receiver, name, List.of(arguments)).orElseThrow();
    }
}
