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
import de.useweb.backend.api.dto.snapshot.ObjectInstanceDto;
import de.useweb.backend.api.dto.snapshot.ObjectLinkDto;
import de.useweb.backend.api.dto.snapshot.ObjectModelDto;
import de.useweb.backend.api.dto.snapshot.SlotDto;
import de.useweb.backend.application.snapshot.ObjectModelService;
import de.useweb.backend.domain.project.ProjectId;
import de.useweb.backend.domain.snapshot.ObjectInstanceId;
import de.useweb.backend.domain.snapshot.ObjectLinkId;

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class ObjectModelController {

    private final ObjectModelService objectModelService;

    public ObjectModelController(ObjectModelService objectModelService) {
        this.objectModelService = objectModelService;
    }

    @GetMapping("/object-model")
    public ObjectModelDto getObjectModel(@PathVariable String projectId) {
        return objectModelService.getCurrentSnapshot(new ProjectId(projectId));
    }

    @PostMapping("/objects")
    public ResponseEntity<ObjectInstanceDto> createObject(
            @PathVariable String projectId,
            @RequestBody ObjectInstanceDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(objectModelService.createObject(new ProjectId(projectId), request));
    }

    @DeleteMapping("/objects/{objectId}")
    public ProjectDto deleteObject(@PathVariable String projectId, @PathVariable String objectId) {
        return objectModelService.deleteObject(new ProjectId(projectId), new ObjectInstanceId(objectId));
    }

    @PutMapping("/objects/{objectId}/slots/{slotId}")
    public SlotDto setSlotValue(
            @PathVariable String projectId,
            @PathVariable String objectId,
            @PathVariable String slotId,
            @RequestBody SlotDto request) {
        SlotDto slot = new SlotDto(slotId, request.attributeId(), request.value(), request.isUnset());
        return objectModelService.setSlotValue(new ProjectId(projectId), new ObjectInstanceId(objectId), slot);
    }

    @PostMapping("/links")
    public ResponseEntity<ObjectLinkDto> createObjectLink(
            @PathVariable String projectId,
            @RequestBody ObjectLinkDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(objectModelService.createObjectLink(new ProjectId(projectId), request));
    }

    @DeleteMapping("/links/{linkId}")
    public ProjectDto deleteObjectLink(@PathVariable String projectId, @PathVariable String linkId) {
        return objectModelService.deleteObjectLink(new ProjectId(projectId), new ObjectLinkId(linkId));
    }
}
