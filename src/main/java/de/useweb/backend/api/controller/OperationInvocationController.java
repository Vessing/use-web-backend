package de.useweb.backend.api.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import de.useweb.backend.api.dto.operation.OperationInvocationRequestDto;
import de.useweb.backend.api.dto.operation.OperationInvocationResultDto;
import de.useweb.backend.application.operation.OperationInvocationService;
import de.useweb.backend.domain.project.ProjectId;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/operations")
public class OperationInvocationController {
    private final OperationInvocationService service;

    public OperationInvocationController(OperationInvocationService service) {
        this.service = service;
    }

    @PostMapping("/{operationId}/invocations")
    public OperationInvocationResultDto invoke(@PathVariable String projectId, @PathVariable String operationId,
            @RequestBody OperationInvocationRequestDto request) {
        if (request.operationId() != null && !operationId.equals(request.operationId())) {
            throw new IllegalArgumentException("Path and request operation ids must match");
        }
        return service.invoke(new ProjectId(projectId), new OperationInvocationRequestDto(request.receiverObjectId(),
                operationId, request.arguments(), request.expectedRevision()));
    }
}
