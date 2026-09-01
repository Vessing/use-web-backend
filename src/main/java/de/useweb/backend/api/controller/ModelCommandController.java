package de.useweb.backend.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.useweb.backend.api.dto.command.DeleteCommandRequestDto;
import de.useweb.backend.api.dto.command.DeleteImpactDto;
import de.useweb.backend.api.dto.command.MutationCommandRequestDto;
import de.useweb.backend.api.dto.command.MutationResultDto;
import de.useweb.backend.application.command.ModelCommandService;
import de.useweb.backend.domain.project.ProjectId;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlAttributeId;
import de.useweb.backend.domain.uml.UmlAssociationId;
import de.useweb.backend.domain.uml.UmlDataTypeId;
import de.useweb.backend.domain.uml.UmlInvariantId;
import de.useweb.backend.domain.uml.UmlEnumerationId;
import de.useweb.backend.domain.uml.UmlOperationId;
import de.useweb.backend.domain.uml.UmlPackageId;
import de.useweb.backend.domain.uml.UmlModelImportId;
import de.useweb.backend.domain.ocl.OclDefinitionElementId;
import de.useweb.backend.api.dto.ocl.OclDefinitionElementDto;
import de.useweb.backend.application.ocl.OclDefinitionApplicationService;
import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/commands")
public class ModelCommandController {
    private final ModelCommandService commands;
    private final OclDefinitionApplicationService definitions;

    public ModelCommandController(ModelCommandService commands, OclDefinitionApplicationService definitions) {
        this.commands = commands;
        this.definitions = definitions;
    }

    @GetMapping("/definitions")
    public List<OclDefinitionElementDto> definitions(@PathVariable String projectId) {
        return definitions.list(new ProjectId(projectId));
    }

    @PostMapping("/definitions")
    public ResponseEntity<MutationResultDto> createDefinition(@PathVariable String projectId,
            @RequestBody MutationCommandRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commands.createDefinition(new ProjectId(projectId), request));
    }

    @PutMapping("/definitions/{definitionId}")
    public MutationResultDto updateDefinition(@PathVariable String projectId, @PathVariable String definitionId,
            @RequestBody MutationCommandRequestDto request) {
        return commands.updateDefinition(new ProjectId(projectId), new OclDefinitionElementId(definitionId), request);
    }

    @PostMapping("/classes")
    public ResponseEntity<MutationResultDto> createClass(@PathVariable String projectId,
            @RequestBody MutationCommandRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commands.createClass(new ProjectId(projectId), request));
    }

    @PutMapping("/classes/{classId}")
    public MutationResultDto updateClass(@PathVariable String projectId, @PathVariable String classId,
            @RequestBody MutationCommandRequestDto request) {
        return commands.updateClass(new ProjectId(projectId), new UmlClassId(classId), request);
    }

    @PutMapping("/classes/{classId}/generalizations")
    public MutationResultDto setGeneralizations(@PathVariable String projectId, @PathVariable String classId,
            @RequestBody MutationCommandRequestDto request) {
        return commands.setGeneralizations(new ProjectId(projectId), new UmlClassId(classId), request);
    }

    @PutMapping("/classes/{classId}/redefinitions")
    public MutationResultDto setFeatureRedefinition(@PathVariable String projectId, @PathVariable String classId,
            @RequestBody MutationCommandRequestDto request) {
        return commands.setFeatureRedefinition(new ProjectId(projectId), new UmlClassId(classId), request);
    }

    @PutMapping("/classes/{classId}/operations/{operationId}")
    public MutationResultDto updateOperation(@PathVariable String projectId, @PathVariable String classId,
            @PathVariable String operationId, @RequestBody MutationCommandRequestDto request) {
        return commands.updateOperation(new ProjectId(projectId), new UmlClassId(classId),
                new UmlOperationId(operationId), request);
    }

    @PostMapping("/classes/{classId}/operations")
    public ResponseEntity<MutationResultDto> createOperation(@PathVariable String projectId, @PathVariable String classId,
            @RequestBody MutationCommandRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commands.createOperation(new ProjectId(projectId),
                new UmlClassId(classId), request));
    }

    @PutMapping("/classes/{classId}/attributes/{attributeId}")
    public MutationResultDto updateAttribute(@PathVariable String projectId, @PathVariable String classId,
            @PathVariable String attributeId, @RequestBody MutationCommandRequestDto request) {
        return commands.updateAttribute(new ProjectId(projectId), new UmlClassId(classId),
                new UmlAttributeId(attributeId), request);
    }

    @PostMapping("/classes/{classId}/attributes")
    public ResponseEntity<MutationResultDto> createAttribute(@PathVariable String projectId, @PathVariable String classId,
            @RequestBody MutationCommandRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commands.createAttribute(new ProjectId(projectId),
                new UmlClassId(classId), request));
    }

    @PostMapping("/associations")
    public ResponseEntity<MutationResultDto> createAssociation(@PathVariable String projectId,
            @RequestBody MutationCommandRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commands.createAssociation(new ProjectId(projectId), request));
    }

    @PutMapping("/associations/{associationId}")
    public MutationResultDto updateAssociation(@PathVariable String projectId, @PathVariable String associationId,
            @RequestBody MutationCommandRequestDto request) {
        return commands.updateAssociation(new ProjectId(projectId), new UmlAssociationId(associationId), request);
    }

    @PostMapping("/associations/{associationId}/association-class")
    public ResponseEntity<MutationResultDto> createAssociationClass(@PathVariable String projectId,
            @PathVariable String associationId, @RequestBody MutationCommandRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commands.createAssociationClass(
                new ProjectId(projectId), new UmlAssociationId(associationId), request));
    }

    @PostMapping("/invariants")
    public ResponseEntity<MutationResultDto> createInvariant(@PathVariable String projectId,
            @RequestBody MutationCommandRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commands.createInvariant(new ProjectId(projectId), request));
    }

    @PutMapping("/invariants/{invariantId}")
    public MutationResultDto updateInvariant(@PathVariable String projectId, @PathVariable String invariantId,
            @RequestBody MutationCommandRequestDto request) {
        return commands.updateInvariant(new ProjectId(projectId), new UmlInvariantId(invariantId), request);
    }

    @PostMapping("/datatypes")
    public ResponseEntity<MutationResultDto> createDataType(@PathVariable String projectId,
            @RequestBody MutationCommandRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commands.createDataType(new ProjectId(projectId), request));
    }

    @PutMapping("/datatypes/{dataTypeId}")
    public MutationResultDto updateDataType(@PathVariable String projectId, @PathVariable String dataTypeId,
            @RequestBody MutationCommandRequestDto request) {
        return commands.updateDataType(new ProjectId(projectId), new UmlDataTypeId(dataTypeId), request);
    }

    @GetMapping("/datatypes/{dataTypeId}/properties/{propertyId}/delete-impact")
    public DeleteImpactDto dataTypePropertyDeleteImpact(@PathVariable String projectId,
            @PathVariable String dataTypeId, @PathVariable String propertyId) {
        return commands.dataTypePropertyDeleteImpact(new ProjectId(projectId), new UmlDataTypeId(dataTypeId), propertyId);
    }

    @DeleteMapping("/datatypes/{dataTypeId}/properties/{propertyId}")
    public MutationResultDto deleteDataTypeProperty(@PathVariable String projectId, @PathVariable String dataTypeId,
            @PathVariable String propertyId, @RequestBody DeleteCommandRequestDto request) {
        return commands.deleteDataTypeProperty(new ProjectId(projectId), new UmlDataTypeId(dataTypeId), propertyId,
                request);
    }

    @PostMapping("/packages")
    public ResponseEntity<MutationResultDto> createPackage(@PathVariable String projectId,
            @RequestBody MutationCommandRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commands.createPackage(new ProjectId(projectId), request));
    }

    @PutMapping("/packages/{packageId}")
    public MutationResultDto updatePackage(@PathVariable String projectId, @PathVariable String packageId,
            @RequestBody MutationCommandRequestDto request) {
        return commands.updatePackage(new ProjectId(projectId), new UmlPackageId(packageId), request);
    }

    @PostMapping("/imports")
    public ResponseEntity<MutationResultDto> createImport(@PathVariable String projectId,
            @RequestBody MutationCommandRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commands.createImport(new ProjectId(projectId), request));
    }

    @PutMapping("/imports/{importId}")
    public MutationResultDto updateImport(@PathVariable String projectId, @PathVariable String importId,
            @RequestBody MutationCommandRequestDto request) {
        return commands.updateImport(new ProjectId(projectId), new UmlModelImportId(importId), request);
    }

    @PostMapping("/enumerations")
    public ResponseEntity<MutationResultDto> createEnumeration(@PathVariable String projectId,
            @RequestBody MutationCommandRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commands.createEnumeration(new ProjectId(projectId), request));
    }

    @PutMapping("/enumerations/{enumerationId}")
    public MutationResultDto updateEnumeration(@PathVariable String projectId, @PathVariable String enumerationId,
            @RequestBody MutationCommandRequestDto request) {
        return commands.updateEnumeration(new ProjectId(projectId), new UmlEnumerationId(enumerationId), request);
    }

    @GetMapping("/delete-impact/{elementType}/{elementId}")
    public DeleteImpactDto deleteImpact(@PathVariable String projectId, @PathVariable String elementType,
            @PathVariable String elementId) {
        return commands.deleteImpact(new ProjectId(projectId), elementType, elementId);
    }

    @DeleteMapping("/{elementType}/{elementId}")
    public MutationResultDto delete(@PathVariable String projectId, @PathVariable String elementType,
            @PathVariable String elementId, @RequestBody DeleteCommandRequestDto request) {
        return commands.delete(new ProjectId(projectId), elementType, elementId, request);
    }
}
