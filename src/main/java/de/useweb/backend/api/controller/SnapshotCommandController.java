package de.useweb.backend.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.useweb.backend.api.dto.command.CreateObjectCommandRequestDto;
import de.useweb.backend.api.dto.command.AssociationClassInstanceCommandRequestDto;
import de.useweb.backend.api.dto.command.CreateObjectLinkCommandRequestDto;
import de.useweb.backend.api.dto.command.DeleteCommandRequestDto;
import de.useweb.backend.api.dto.command.MutationResultDto;
import de.useweb.backend.api.dto.command.ObjectLinkDeleteImpactDto;
import de.useweb.backend.api.dto.command.UpdateObjectLinkCommandRequestDto;
import de.useweb.backend.api.dto.command.UpdateSlotCommandRequestDto;
import de.useweb.backend.application.command.SnapshotCommandService;
import de.useweb.backend.domain.project.ProjectId;
import de.useweb.backend.domain.snapshot.ObjectInstanceId;
import de.useweb.backend.domain.snapshot.ObjectLinkId;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/commands/object-model")
public class SnapshotCommandController {

    private final SnapshotCommandService commands;

    public SnapshotCommandController(SnapshotCommandService commands) {
        this.commands = commands;
    }

    @PostMapping("/objects")
    public ResponseEntity<MutationResultDto> createObject(@PathVariable String projectId,
            @RequestBody CreateObjectCommandRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commands.createObject(new ProjectId(projectId), request));
    }

    @PutMapping("/objects/{objectId}/slots/{slotId}")
    public MutationResultDto updateSlot(@PathVariable String projectId, @PathVariable String objectId,
            @PathVariable String slotId, @RequestBody UpdateSlotCommandRequestDto request) {
        return commands.updateSlot(new ProjectId(projectId), new ObjectInstanceId(objectId), slotId, request);
    }

    @PostMapping("/links")
    public ResponseEntity<MutationResultDto> createObjectLink(@PathVariable String projectId,
            @RequestBody CreateObjectLinkCommandRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commands.createObjectLink(new ProjectId(projectId), request));
    }

    @PutMapping("/links/{linkId}")
    public MutationResultDto updateObjectLink(@PathVariable String projectId, @PathVariable String linkId,
            @RequestBody UpdateObjectLinkCommandRequestDto request) {
        return commands.updateObjectLink(new ProjectId(projectId), new ObjectLinkId(linkId), request);
    }

    @PostMapping("/association-class-instances")
    public ResponseEntity<MutationResultDto> createAssociationClassInstance(@PathVariable String projectId,
            @RequestBody AssociationClassInstanceCommandRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commands.createAssociationClassInstance(new ProjectId(projectId), request));
    }

    @PutMapping("/association-class-instances/{linkId}")
    public MutationResultDto updateAssociationClassInstance(@PathVariable String projectId,
            @PathVariable String linkId, @RequestBody AssociationClassInstanceCommandRequestDto request) {
        return commands.updateAssociationClassInstance(new ProjectId(projectId), new ObjectLinkId(linkId), request);
    }

    @GetMapping("/links/{linkId}/delete-impact")
    public ObjectLinkDeleteImpactDto objectLinkDeleteImpact(@PathVariable String projectId,
            @PathVariable String linkId) {
        return commands.objectLinkDeleteImpact(new ProjectId(projectId), new ObjectLinkId(linkId));
    }

    @DeleteMapping("/links/{linkId}")
    public MutationResultDto deleteObjectLink(@PathVariable String projectId, @PathVariable String linkId,
            @RequestBody DeleteCommandRequestDto request) {
        return commands.deleteObjectLink(new ProjectId(projectId), new ObjectLinkId(linkId), request);
    }
}
