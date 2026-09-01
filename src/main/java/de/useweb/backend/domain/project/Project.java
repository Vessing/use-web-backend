package de.useweb.backend.domain.project;

import de.useweb.backend.domain.layout.LayoutInformation;
import de.useweb.backend.domain.modeltext.ModelText;
import de.useweb.backend.domain.snapshot.ObjectModel;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.ocl.OclDefinitionElement;
import java.util.List;

public record Project(
        ProjectId id,
        ProjectMetadata metadata,
        ModelText modelText,
        UmlModel umlModel,
        ObjectModel objectModel,
        LayoutInformation layout,
        List<OclDefinitionElement> definitions) {

    public Project(ProjectId id, ProjectMetadata metadata, ModelText modelText, UmlModel umlModel,
            ObjectModel objectModel, LayoutInformation layout) {
        this(id, metadata, modelText, umlModel, objectModel, layout, List.of());
    }

    public Project(ProjectId id, ProjectMetadata metadata, UmlModel umlModel, ObjectModel objectModel, LayoutInformation layout) {
        this(id, metadata, null, umlModel, objectModel, layout, List.of());
    }

    public Project {
        if (id == null) {
            throw new IllegalArgumentException("Project id must not be null");
        }
        if (metadata == null) {
            throw new IllegalArgumentException("Project metadata must not be null");
        }
        if (umlModel == null) {
            throw new IllegalArgumentException("Project umlModel must not be null");
        }
        if (objectModel == null) {
            throw new IllegalArgumentException("Project objectModel must not be null");
        }
        if (layout == null) {
            throw new IllegalArgumentException("Project layout must not be null");
        }
        definitions = List.copyOf(definitions == null ? List.of() : definitions);
        if (definitions.stream().map(OclDefinitionElement::id).distinct().count() != definitions.size()) {
            throw new IllegalArgumentException("Definition ids must be unique");
        }
    }
}
