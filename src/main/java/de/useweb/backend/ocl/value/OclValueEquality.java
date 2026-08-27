package de.useweb.backend.ocl.value;

import java.util.ArrayList;
import java.util.List;

import de.useweb.backend.ocl.collection.CollectionKind;

public final class OclValueEquality {
    private OclValueEquality() {
    }

    public static boolean equal(OclValue left, OclValue right) {
        if (left instanceof IntegerValue leftInteger && right instanceof RealValue rightReal) {
            return Double.compare(leftInteger.value(), rightReal.value()) == 0;
        }
        if (left instanceof RealValue leftReal && right instanceof IntegerValue rightInteger) {
            return Double.compare(leftReal.value(), rightInteger.value()) == 0;
        }
        if (left instanceof CollectionValue leftCollection && right instanceof CollectionValue rightCollection) {
            return collectionEqual(leftCollection, rightCollection);
        }
        if (left instanceof TupleValue leftTuple && right instanceof TupleValue rightTuple) {
            return leftTuple.parts().keySet().equals(rightTuple.parts().keySet())
                    && leftTuple.parts().entrySet().stream()
                            .allMatch(entry -> equal(entry.getValue(), rightTuple.parts().get(entry.getKey())));
        }
        if (left instanceof DataTypeValue leftData && right instanceof DataTypeValue rightData) {
            return leftData.dataTypeId().equals(rightData.dataTypeId())
                    && leftData.properties().keySet().equals(rightData.properties().keySet())
                    && leftData.properties().entrySet().stream()
                            .allMatch(entry -> equal(entry.getValue(), rightData.properties().get(entry.getKey())));
        }
        if (left instanceof ClassifierValue leftType && right instanceof ClassifierValue rightType) {
            return leftType.classifierId().equals(rightType.classifierId());
        }
        if (left instanceof ObjectValue leftObject && right instanceof ObjectValue rightObject) {
            return leftObject.object().id().equals(rightObject.object().id());
        }
        return left.equals(right);
    }

    public static OclValue semanticEqual(OclValue left, OclValue right) {
        if (left instanceof OclInvalidValue || right instanceof OclInvalidValue) return OclInvalidValue.INSTANCE;
        if (left instanceof OclVoidValue || right instanceof OclVoidValue) {
            return new BooleanValue(left instanceof OclVoidValue && right instanceof OclVoidValue);
        }
        if (left instanceof ObjectValue leftObject && right instanceof ObjectValue rightObject) {
            return new BooleanValue(leftObject.object().id().equals(rightObject.object().id()));
        }
        if (left instanceof TupleValue leftTuple && right instanceof TupleValue rightTuple) {
            if (!leftTuple.parts().keySet().equals(rightTuple.parts().keySet())) return new BooleanValue(false);
            OclValue result = new BooleanValue(true);
            for (String part : leftTuple.parts().keySet()) {
                result = OclBooleanLogic.and(result,
                        semanticEqual(leftTuple.parts().get(part), rightTuple.parts().get(part)));
            }
            return result;
        }
        if (left instanceof CollectionValue leftCollection && right instanceof CollectionValue rightCollection) {
            return semanticCollectionEqual(leftCollection, rightCollection);
        }
        return new BooleanValue(equal(left, right));
    }

    private static OclValue semanticCollectionEqual(CollectionValue left, CollectionValue right) {
        if (left.collectionKind() != right.collectionKind() || left.values().size() != right.values().size()) {
            return new BooleanValue(false);
        }
        if (left.collectionKind() == CollectionKind.SEQUENCE || left.collectionKind() == CollectionKind.ORDERED_SET) {
            OclValue result = new BooleanValue(true);
            for (int index = 0; index < left.values().size(); index++) {
                result = OclBooleanLogic.and(result, semanticEqual(left.values().get(index), right.values().get(index)));
            }
            return result;
        }
        if (left.values().stream().anyMatch(OclInvalidValue.class::isInstance)
                || right.values().stream().anyMatch(OclInvalidValue.class::isInstance)) {
            return OclInvalidValue.INSTANCE;
        }
        return new BooleanValue(collectionEqual(left, right));
    }

    private static boolean collectionEqual(CollectionValue left, CollectionValue right) {
        if (left.collectionKind() != right.collectionKind() || left.values().size() != right.values().size()) {
            return false;
        }
        if (left.collectionKind() == CollectionKind.SEQUENCE || left.collectionKind() == CollectionKind.ORDERED_SET) {
            for (int index = 0; index < left.values().size(); index++) {
                if (!equal(left.values().get(index), right.values().get(index))) return false;
            }
            return true;
        }
        List<OclValue> unmatched = new ArrayList<>(right.values());
        for (OclValue value : left.values()) {
            int index = indexOf(unmatched, value);
            if (index < 0) return false;
            unmatched.remove(index);
        }
        return unmatched.isEmpty();
    }

    private static int indexOf(List<OclValue> values, OclValue target) {
        for (int index = 0; index < values.size(); index++) {
            if (equal(values.get(index), target)) return index;
        }
        return -1;
    }
}
