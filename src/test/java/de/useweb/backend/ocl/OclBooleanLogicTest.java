package de.useweb.backend.ocl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.useweb.backend.ocl.value.BooleanValue;
import de.useweb.backend.ocl.value.OclBooleanLogic;
import de.useweb.backend.ocl.value.OclInvalidValue;
import de.useweb.backend.ocl.value.OclValue;
import de.useweb.backend.ocl.value.OclVoidValue;
import de.useweb.backend.ocl.value.IntegerValue;
import de.useweb.backend.ocl.value.SequenceValue;
import de.useweb.backend.ocl.value.TupleValue;

class OclBooleanLogicTest {
    private static final OclValue TRUE = new BooleanValue(true);
    private static final OclValue FALSE = new BooleanValue(false);
    private static final OclValue NULL = OclVoidValue.INSTANCE;
    private static final OclValue INVALID = OclInvalidValue.INSTANCE;
    private static final OclValue[] VALUES = { TRUE, FALSE, NULL, INVALID };

    @Test void coversNot() { assertTable(new OclValue[] { FALSE, TRUE, NULL, INVALID }, OclBooleanLogic::not); }

    @Test void coversAnd() { assertTable(new OclValue[][] {
            { TRUE, FALSE, NULL, INVALID }, { FALSE, FALSE, FALSE, FALSE },
            { NULL, FALSE, NULL, INVALID }, { INVALID, FALSE, INVALID, INVALID }
    }, OclBooleanLogic::and); }

    @Test void coversOr() { assertTable(new OclValue[][] {
            { TRUE, TRUE, TRUE, TRUE }, { TRUE, FALSE, NULL, INVALID },
            { TRUE, NULL, NULL, INVALID }, { TRUE, INVALID, INVALID, INVALID }
    }, OclBooleanLogic::or); }

    @Test void coversXor() { assertTable(new OclValue[][] {
            { FALSE, TRUE, NULL, INVALID }, { TRUE, FALSE, NULL, INVALID },
            { NULL, NULL, NULL, INVALID }, { INVALID, INVALID, INVALID, INVALID }
    }, OclBooleanLogic::xor); }

    @Test void coversImplies() { assertTable(new OclValue[][] {
            { TRUE, FALSE, NULL, INVALID }, { TRUE, TRUE, TRUE, TRUE },
            { TRUE, NULL, NULL, INVALID }, { TRUE, INVALID, INVALID, INVALID }
    }, OclBooleanLogic::implies); }

    @Test
    void distinguishesNullFromInvalidForEquality() {
        assertThat(OclBooleanLogic.equal(NULL, NULL)).isEqualTo(TRUE);
        assertThat(OclBooleanLogic.equal(NULL, FALSE)).isEqualTo(FALSE);
        assertThat(OclBooleanLogic.equal(INVALID, INVALID)).isEqualTo(INVALID);
        assertThat(OclBooleanLogic.notEqual(NULL, NULL)).isEqualTo(FALSE);
        assertThat(OclBooleanLogic.notEqual(NULL, TRUE)).isEqualTo(TRUE);
        assertThat(OclBooleanLogic.notEqual(INVALID, TRUE)).isEqualTo(INVALID);
        assertThat(OclBooleanLogic.equal(
                new TupleValue(Map.of("value", INVALID)),
                new TupleValue(Map.of("value", INVALID)))).isEqualTo(INVALID);
        assertThat(OclBooleanLogic.equal(
                new SequenceValue(List.of(new IntegerValue(1), INVALID)),
                new SequenceValue(List.of(new IntegerValue(1), INVALID)))).isEqualTo(INVALID);
    }

    private static void assertTable(OclValue[] expected, Function<OclValue, OclValue> operation) {
        for (int i = 0; i < VALUES.length; i++) assertThat(operation.apply(VALUES[i])).as("operand %s", i).isEqualTo(expected[i]);
    }

    private static void assertTable(OclValue[][] expected, BiFunction<OclValue, OclValue, OclValue> operation) {
        for (int l = 0; l < VALUES.length; l++) for (int r = 0; r < VALUES.length; r++) {
            assertThat(operation.apply(VALUES[l], VALUES[r])).as("operands %s/%s", l, r).isEqualTo(expected[l][r]);
        }
    }
}
