package de.useweb.backend.domain.layout;

import java.util.List;

public record DiagramLayout(List<NodeLayout> nodes, List<EdgeLayout> edges, Viewport viewport) {

    public DiagramLayout {
        nodes = List.copyOf(nodes == null ? List.of() : nodes);
        edges = List.copyOf(edges == null ? List.of() : edges);
    }

    public static DiagramLayout empty() {
        return new DiagramLayout(List.of(), List.of(), null);
    }
}
