package de.useweb.backend.api.dto.project;

import java.util.Map;

import de.useweb.backend.api.dto.layout.LayoutDto;
import de.useweb.backend.api.dto.modeltext.ModelTextDto;
import de.useweb.backend.api.dto.snapshot.ObjectModelDto;
import de.useweb.backend.api.dto.uml.UmlModelDto;
import de.useweb.backend.api.dto.validation.ValidationStateDto;
import de.useweb.backend.api.dto.ocl.OclDefinitionElementDto;
import java.util.List;

public record ProjectDto(
        String formatVersion,
        ProjectMetadataDto project,
        ModelTextDto modelText,
        UmlModelDto umlModel,
        ObjectModelDto objectModel,
        LayoutDto layout,
        ValidationStateDto validationState,
        Map<String, Object> extensions,
        List<OclDefinitionElementDto> definitions
) {
    public ProjectDto(String formatVersion, ProjectMetadataDto project, ModelTextDto modelText,
            UmlModelDto umlModel, ObjectModelDto objectModel, LayoutDto layout,
            ValidationStateDto validationState, Map<String, Object> extensions) {
        this(formatVersion, project, modelText, umlModel, objectModel, layout, validationState, extensions, List.of());
    }
}
