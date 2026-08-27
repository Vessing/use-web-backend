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

import de.useweb.backend.api.dto.project.ProjectDto;
import de.useweb.backend.api.dto.uml.UmlAssociationDto;
import de.useweb.backend.api.dto.uml.UmlAttributeDto;
import de.useweb.backend.api.dto.uml.UmlClassDto;
import de.useweb.backend.api.dto.uml.UmlInvariantDto;
import de.useweb.backend.api.dto.uml.UmlModelDto;
import de.useweb.backend.api.dto.uml.UmlOperationDto;
import de.useweb.backend.api.dto.uml.UmlPackageDto;
import de.useweb.backend.api.dto.uml.UmlModelImportDto;
import de.useweb.backend.application.uml.UmlModelService;
import de.useweb.backend.domain.project.ProjectId;
import de.useweb.backend.domain.uml.UmlAssociationId;
import de.useweb.backend.domain.uml.UmlAttributeId;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlInvariantId;
import de.useweb.backend.domain.uml.UmlOperationId;
import de.useweb.backend.domain.uml.UmlModelImportId;

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class UmlModelController {

    private final UmlModelService umlModelService;

    public UmlModelController(UmlModelService umlModelService) {
        this.umlModelService = umlModelService;
    }

    @GetMapping("/uml-model")
    public UmlModelDto getUmlModel(@PathVariable String projectId) {
        return umlModelService.getUmlModel(new ProjectId(projectId));
    }

    @PostMapping("/classes")
    public ResponseEntity<UmlClassDto> createClass(@PathVariable String projectId, @RequestBody UmlClassDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(umlModelService.createClass(new ProjectId(projectId), request));
    }

    @PutMapping("/classes/{classId}")
    public UmlClassDto updateClass(
            @PathVariable String projectId,
            @PathVariable String classId,
            @RequestBody UmlClassDto request) {
        return umlModelService.updateClass(new ProjectId(projectId), new UmlClassId(classId), request);
    }

    @DeleteMapping("/classes/{classId}")
    public ProjectDto deleteClass(@PathVariable String projectId, @PathVariable String classId) {
        return umlModelService.deleteClass(new ProjectId(projectId), new UmlClassId(classId));
    }

    @PostMapping("/classes/{classId}/attributes")
    public ResponseEntity<UmlAttributeDto> addAttribute(
            @PathVariable String projectId,
            @PathVariable String classId,
            @RequestBody UmlAttributeDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(umlModelService.addAttribute(new ProjectId(projectId), new UmlClassId(classId), request));
    }

    @DeleteMapping("/classes/{classId}/attributes/{attributeId}")
    public ProjectDto deleteAttribute(
            @PathVariable String projectId,
            @PathVariable String classId,
            @PathVariable String attributeId) {
        return umlModelService.deleteAttribute(new ProjectId(projectId), new UmlClassId(classId), new UmlAttributeId(attributeId));
    }

    @PutMapping("/classes/{classId}/attributes/{attributeId}")
    public UmlAttributeDto updateAttribute(@PathVariable String projectId, @PathVariable String classId,
            @PathVariable String attributeId, @RequestBody UmlAttributeDto request) {
        return umlModelService.updateAttribute(new ProjectId(projectId), new UmlClassId(classId),
                new UmlAttributeId(attributeId), request);
    }

    @PostMapping("/classes/{classId}/operations")
    public ResponseEntity<UmlOperationDto> addOperation(
            @PathVariable String projectId,
            @PathVariable String classId,
            @RequestBody UmlOperationDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(umlModelService.addOperation(new ProjectId(projectId), new UmlClassId(classId), request));
    }

    @DeleteMapping("/classes/{classId}/operations/{operationId}")
    public ProjectDto deleteOperation(
            @PathVariable String projectId,
            @PathVariable String classId,
            @PathVariable String operationId) {
        return umlModelService.deleteOperation(new ProjectId(projectId), new UmlClassId(classId), new UmlOperationId(operationId));
    }

    @PutMapping("/classes/{classId}/operations/{operationId}")
    public UmlOperationDto updateOperation(@PathVariable String projectId, @PathVariable String classId,
            @PathVariable String operationId, @RequestBody UmlOperationDto request) {
        return umlModelService.updateOperation(new ProjectId(projectId), new UmlClassId(classId),
                new UmlOperationId(operationId), request);
    }

    @PostMapping("/packages")
    public ResponseEntity<UmlPackageDto> createPackage(@PathVariable String projectId,
            @RequestBody UmlPackageDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(umlModelService.createPackage(new ProjectId(projectId), request));
    }

    @PostMapping("/imports")
    public ResponseEntity<UmlModelImportDto> createImport(@PathVariable String projectId,
            @RequestBody UmlModelImportDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(umlModelService.createImport(new ProjectId(projectId), request));
    }

    @DeleteMapping("/imports/{importId}")
    public ProjectDto deleteImport(@PathVariable String projectId, @PathVariable String importId) {
        return umlModelService.deleteImport(new ProjectId(projectId), new UmlModelImportId(importId));
    }

    @PostMapping("/associations")
    public ResponseEntity<UmlAssociationDto> createAssociation(
            @PathVariable String projectId,
            @RequestBody UmlAssociationDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(umlModelService.createAssociation(new ProjectId(projectId), request));
    }

    @PutMapping("/associations/{associationId}")
    public UmlAssociationDto updateAssociation(@PathVariable String projectId,
            @PathVariable String associationId, @RequestBody UmlAssociationDto request) {
        return umlModelService.updateAssociation(new ProjectId(projectId),
                new UmlAssociationId(associationId), request);
    }

    @DeleteMapping("/associations/{associationId}")
    public ProjectDto deleteAssociation(@PathVariable String projectId, @PathVariable String associationId) {
        return umlModelService.deleteAssociation(new ProjectId(projectId), new UmlAssociationId(associationId));
    }

    @PostMapping("/invariants")
    public ResponseEntity<UmlInvariantDto> createInvariant(
            @PathVariable String projectId,
            @RequestBody UmlInvariantDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(umlModelService.createInvariant(new ProjectId(projectId), request));
    }

    @DeleteMapping("/invariants/{invariantId}")
    public ProjectDto deleteInvariant(@PathVariable String projectId, @PathVariable String invariantId) {
        return umlModelService.deleteInvariant(new ProjectId(projectId), new UmlInvariantId(invariantId));
    }
}
