package de.useweb.backend.ocl.resolution;

import java.util.LinkedHashMap;

import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.ocl.typecheck.OclType;
import de.useweb.backend.ocl.value.BooleanValue;
import de.useweb.backend.ocl.value.CollectionValue;
import de.useweb.backend.ocl.value.EnumValue;
import de.useweb.backend.ocl.value.IntegerValue;
import de.useweb.backend.ocl.value.ObjectValue;
import de.useweb.backend.ocl.value.OclInvalidValue;
import de.useweb.backend.ocl.value.OclValue;
import de.useweb.backend.ocl.value.OclVoidValue;
import de.useweb.backend.ocl.value.RealValue;
import de.useweb.backend.ocl.value.StringValue;
import de.useweb.backend.ocl.value.TupleValue;
import de.useweb.backend.ocl.value.DataTypeValue;
import de.useweb.backend.ocl.value.ClassifierValue;
import de.useweb.backend.ocl.value.UnlimitedNaturalValue;

public final class OclRuntimeType {
    private OclRuntimeType() {
    }

    public static OclType of(OclValue value, UmlModel model) {
        if (value instanceof StringValue) return OclType.STRING;
        if (value instanceof IntegerValue) return OclType.INTEGER;
        if (value instanceof RealValue) return OclType.REAL;
        if (value instanceof BooleanValue) return OclType.BOOLEAN;
        if (value instanceof UnlimitedNaturalValue) return OclType.UNLIMITED_NATURAL;
        if (value instanceof OclVoidValue) return OclType.VOID;
        if (value instanceof OclInvalidValue) return OclType.OCL_INVALID;
        if (value instanceof EnumValue enumeration) {
            return OclType.enumerationType(enumeration.enumerationId(), enumeration.enumerationName());
        }
        if (value instanceof DataTypeValue dataType) {
            return OclType.dataType(dataType.dataTypeId(), dataType.dataTypeName());
        }
        if (value instanceof ClassifierValue classifier) {
            return OclType.classifierValueType(classifier.representedType());
        }
        if (value instanceof ObjectValue object) {
            return model.findClass(object.object().classId()).map(type -> OclType.classType(type, model))
                    .orElse(OclType.INVALID);
        }
        if (value instanceof CollectionValue collection) {
            OclType element = collection.values().stream().map(item -> of(item, model))
                    .reduce((left, right) -> left.leastUpperBound(right, model)).orElse(OclType.OCL_ANY);
            return OclType.collectionOf(collection.collectionKind(), element);
        }
        if (value instanceof TupleValue tuple) {
            LinkedHashMap<String, OclType> parts = new LinkedHashMap<>();
            tuple.parts().forEach((name, item) -> parts.put(name, of(item, model)));
            return OclType.tupleOf(parts);
        }
        return OclType.INVALID;
    }
}
