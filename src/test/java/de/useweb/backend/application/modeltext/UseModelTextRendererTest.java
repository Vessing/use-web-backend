package de.useweb.backend.application.modeltext;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.useweb.backend.domain.ocl.OclExpression;
import de.useweb.backend.domain.ocl.OclExpressionId;
import de.useweb.backend.domain.uml.AggregationKind;
import de.useweb.backend.domain.uml.Multiplicity;
import de.useweb.backend.domain.uml.UmlAssociation;
import de.useweb.backend.domain.uml.UmlAssociationEnd;
import de.useweb.backend.domain.uml.UmlAssociationEndId;
import de.useweb.backend.domain.uml.UmlAssociationId;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlDataType;
import de.useweb.backend.domain.uml.UmlDataTypeId;
import de.useweb.backend.domain.uml.UmlDataTypeProperty;
import de.useweb.backend.domain.uml.UmlEnumeration;
import de.useweb.backend.domain.uml.UmlEnumerationId;
import de.useweb.backend.domain.uml.UmlInvariant;
import de.useweb.backend.domain.uml.UmlInvariantId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlModelId;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.modeltext.parser.ModelTextParser;

class UseModelTextRendererTest {

    @Test
    void rendersDeterministicParserCompatibleUseText() {
        UmlClassId orderId = new UmlClassId("class-order");
        UmlClassId lineId = new UmlClassId("class-line");
        UmlAssociation lines = new UmlAssociation(new UmlAssociationId("association-lines"), "lines", List.of(
                new UmlAssociationEnd(new UmlAssociationEndId("end-order"), orderId, "order",
                        Multiplicity.exactlyOne(), true, false, true, false, false, List.of(), List.of(),
                        List.of(), AggregationKind.COMPOSITE, null),
                new UmlAssociationEnd(new UmlAssociationEndId("end-line"), lineId, "lines",
                        Multiplicity.zeroToMany(), true)));
        UmlInvariant invariant = new UmlInvariant(new UmlInvariantId("invariant-order"), "valid", orderId,
                new OclExpression(new OclExpressionId("expression-valid"), "self = self", "mvp-subset"), true);
        UmlDataType money = new UmlDataType(new UmlDataTypeId("datatype-money"), "Money",
                List.of(new UmlDataTypeProperty("property-amount", "amount", UmlType.REAL)));
        UmlModel model = new UmlModel(new UmlModelId("model-sales"), "Sales", List.of(
                new UmlClass(orderId, "Order", List.of(), List.of()),
                new UmlClass(lineId, "Line", List.of(), List.of())), List.of(lines), List.of(invariant),
                List.of(new UmlEnumeration(new UmlEnumerationId("enum-status"), "Status", List.of("OPEN", "CLOSED"))),
                List.of(), List.of(), List.of(money));

        String rendered = new UseModelTextRenderer().render(model);

        assertThat(rendered).isEqualTo("""
                model Sales

                enum Status { CLOSED, OPEN }

                datatype Money
                attributes
                  amount : Real
                end

                class Line
                end

                class Order
                end

                composition lines between
                  Order[1] role order
                  Line[0..*] role lines
                end

                constraints
                context Order inv valid:
                  self = self
                """);
        assertThat(new ModelTextParser().parse(rendered).diagnostics()).isEmpty();
    }
}
