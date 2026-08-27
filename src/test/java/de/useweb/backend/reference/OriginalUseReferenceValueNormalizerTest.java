package de.useweb.backend.reference;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlModelId;
import de.useweb.backend.ocl.value.BagValue;
import de.useweb.backend.ocl.value.IntegerValue;
import de.useweb.backend.ocl.value.SequenceValue;
import de.useweb.backend.ocl.value.SetValue;
import de.useweb.backend.ocl.value.StringValue;
import de.useweb.backend.ocl.value.TupleValue;

class OriginalUseReferenceValueNormalizerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final UmlModel emptyModel = new UmlModel(
            new UmlModelId("model"), "Model", List.of(), List.of(), List.of());

    @Test
    void comparesSetsWithoutDependingOnTextOrder() {
        var expected = expected("Set{1,2}", "Set(Integer)");
        var observed = OriginalUseReferenceValueNormalizer.observed(
                new SetValue(List.of(new IntegerValue(2), new IntegerValue(1))), emptyModel);

        assertThat(observed).isEqualTo(expected);
    }

    @Test
    void preservesMultiplicityForBagsAndOrderForSequences() {
        var bag = OriginalUseReferenceValueNormalizer.observed(
                new BagValue(List.of(new IntegerValue(2), new IntegerValue(1), new IntegerValue(1))), emptyModel);
        var sequence = OriginalUseReferenceValueNormalizer.observed(
                new SequenceValue(List.of(new IntegerValue(2), new IntegerValue(1))), emptyModel);

        assertThat(bag).isEqualTo(expected("Bag{1,2,1}", "Bag(Integer)"));
        assertThat(sequence).isEqualTo(expected("Sequence{2,1}", "Sequence(Integer)"));
        assertThat(sequence).isNotEqualTo(expected("Sequence{1,2}", "Sequence(Integer)"));
    }

    @Test
    void normalizesNestedCollectionsTuplesStringsAndUndefined() {
        var expected = expected("Tuple{names=Sequence{'A','B'},missing=null}",
                "Tuple(names:Sequence(String),missing:OclVoid)");
        var observed = OriginalUseReferenceValueNormalizer.observed(new TupleValue(Map.of(
                "names", new SequenceValue(List.of(new StringValue("A"), new StringValue("B"))),
                "missing", de.useweb.backend.ocl.value.OclVoidValue.INSTANCE)), emptyModel);

        assertThat(observed).isEqualTo(expected);
    }

    @Test
    void keepsVoidAndInvalidDistinct() {
        assertThat(expected("null", "OclVoid"))
                .isNotEqualTo(expected("null", "OclInvalid"));
    }

    @Test
    void migratesLegacyValueAndTypeTextIntoTheTypedAssertionModel() {
        ObjectNode referenceCase = mapper.createObjectNode();
        referenceCase.put("expectedType", "Integer");
        referenceCase.put("expectedValueSummary", "42 : Integer");

        assertThat(OriginalUseReferenceValueNormalizer.expected(referenceCase))
                .isEqualTo(expected("42", "Integer"));
    }

    @Test
    void comparesObjectIdentityIndependentlyFromUseCollectionTypeProjection() {
        var expectedObject = new OriginalUseReferenceValueNormalizer.NormalizedValue(
                "OBJECT", "Base", "item", List.of(), Map.of());
        var runtimeObject = new OriginalUseReferenceValueNormalizer.NormalizedValue(
                "OBJECT", "Derived", "item", List.of(), Map.of());
        var expectedCollection = new OriginalUseReferenceValueNormalizer.NormalizedValue(
                "COLLECTION", "Set", "Set", List.of(expectedObject), Map.of());
        var observedCollection = new OriginalUseReferenceValueNormalizer.NormalizedValue(
                "COLLECTION", "Set", "Set", List.of(runtimeObject), Map.of());

        assertThat(OriginalUseReferenceValueNormalizer.semanticallyEquivalent(
                expectedCollection, observedCollection)).isTrue();
    }

    private OriginalUseReferenceValueNormalizer.NormalizedValue expected(String raw, String type) {
        ObjectNode referenceCase = mapper.createObjectNode();
        referenceCase.put("expectedType", type);
        referenceCase.putObject("expectedValueSummary").put("rawValue", raw);
        return OriginalUseReferenceValueNormalizer.expected(referenceCase);
    }
}
