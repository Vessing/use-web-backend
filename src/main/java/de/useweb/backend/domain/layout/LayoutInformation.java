package de.useweb.backend.domain.layout;

public record LayoutInformation(DiagramLayout classDiagram, DiagramLayout objectDiagram) {

    public LayoutInformation {
        classDiagram = classDiagram == null ? DiagramLayout.empty() : classDiagram;
        objectDiagram = objectDiagram == null ? DiagramLayout.empty() : objectDiagram;
    }

    public static LayoutInformation empty() {
        return new LayoutInformation(DiagramLayout.empty(), DiagramLayout.empty());
    }
}
