package de.useweb.backend.api.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.useweb.backend.api.dto.ocl.OclDiagnosticDto;
import de.useweb.backend.api.dto.ocl.OclEvaluateRequestDto;
import de.useweb.backend.api.dto.ocl.OclEvaluateResponseDto;
import de.useweb.backend.api.dto.ocl.OclParseRequestDto;
import de.useweb.backend.api.dto.ocl.OclParseResponseDto;
import de.useweb.backend.api.dto.ocl.OclTypecheckRequestDto;
import de.useweb.backend.api.dto.ocl.OclTypecheckResponseDto;
import de.useweb.backend.api.dto.ocl.SourceRangeDto;
import de.useweb.backend.application.ocl.OclParseService;
import de.useweb.backend.application.ocl.OclDiagnosticMapper;
import de.useweb.backend.application.project.ProjectService;
import de.useweb.backend.domain.project.Project;
import de.useweb.backend.domain.project.ProjectId;
import de.useweb.backend.domain.snapshot.ObjectInstance;
import de.useweb.backend.domain.snapshot.ObjectInstanceId;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.error.ObjectModelException;
import de.useweb.backend.ocl.diagnostics.OclDiagnostic;
import de.useweb.backend.ocl.diagnostics.SourceRange;
import de.useweb.backend.ocl.evaluation.EvaluationContext;
import de.useweb.backend.ocl.evaluation.OclEvaluationResult;
import de.useweb.backend.ocl.evaluation.OclEvaluator;
import de.useweb.backend.ocl.parser.OclParseResult;
import de.useweb.backend.ocl.parser.OclParser;
import de.useweb.backend.ocl.typecheck.OclTypecheckResult;
import de.useweb.backend.ocl.typecheck.OclTypeChecker;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/ocl")
public class OclController {

    private final ProjectService projectService;
    private final OclParseService oclParseService;
    private final OclDiagnosticMapper diagnosticMapper;
    private final OclParser parser = new OclParser();
    private final OclTypeChecker typeChecker = new OclTypeChecker();
    private final OclEvaluator evaluator = new OclEvaluator();

    public OclController(ProjectService projectService, OclParseService oclParseService, OclDiagnosticMapper diagnosticMapper) {
        this.projectService = projectService;
        this.oclParseService = oclParseService;
        this.diagnosticMapper = diagnosticMapper;
    }

    @PostMapping("/parse")
    public OclParseResponseDto parse(@RequestBody OclParseRequestDto request) {
        return oclParseService.parse(request);
    }

    @PostMapping("/typecheck")
    public OclTypecheckResponseDto typecheck(@PathVariable String projectId, @RequestBody OclTypecheckRequestDto request) {
        Project project = projectService.loadProject(new ProjectId(projectId));
        OclParseResult parseResult = parser.parse(request.expression());
        if (!parseResult.success()) {
            return new OclTypecheckResponseDto(false, "Invalid", diagnosticMapper.toDto(parseResult.diagnostics(), request.sourceId(), request.sourceKind(), request.documentVersion()));
        }

        OclTypecheckResult typecheckResult = typeChecker.checkInvariant(
                project.umlModel(),
                new UmlClassId(request.contextClassId()),
                parseResult.ast());
        return new OclTypecheckResponseDto(
                typecheckResult.success(),
                typecheckResult.resultType().displayName(),
                diagnosticMapper.toDto(typecheckResult.diagnostics(), request.sourceId(), request.sourceKind(), request.documentVersion()));
    }

    @PostMapping("/evaluate")
    public OclEvaluateResponseDto evaluate(@PathVariable String projectId, @RequestBody OclEvaluateRequestDto request) {
        Project project = projectService.loadProject(new ProjectId(projectId));
        ObjectInstance self = project.objectModel().findObject(new ObjectInstanceId(request.contextObjectId()))
                .orElseThrow(() -> new ObjectModelException(
                        "INVALID_LINK",
                        "Unknown context object: " + request.contextObjectId(),
                        "Das Kontextobjekt fuer die OCL-Auswertung existiert nicht.",
                        Map.of("contextObjectId", request.contextObjectId())));

        OclParseResult parseResult = parser.parse(request.expression());
        if (!parseResult.success()) {
            return new OclEvaluateResponseDto(false, "Invalid", null, diagnosticMapper.toDto(parseResult.diagnostics(), request.sourceId(), request.sourceKind(), request.documentVersion()));
        }

        OclTypecheckResult typecheckResult = typeChecker.checkInvariant(project.umlModel(), self.classId(), parseResult.ast());
        if (!typecheckResult.success()) {
            return new OclEvaluateResponseDto(false, typecheckResult.resultType().displayName(), null, diagnosticMapper.toDto(typecheckResult.diagnostics(), request.sourceId(), request.sourceKind(), request.documentVersion()));
        }

        OclEvaluationResult evaluationResult = evaluator.evaluate(
                parseResult.ast(),
                new EvaluationContext(project.umlModel(), project.objectModel(), self));
        return new OclEvaluateResponseDto(
                evaluationResult.success(),
                typecheckResult.resultType().displayName(),
                evaluationResult.value() == null ? null : evaluationResult.value().rawValue(),
                evaluationResult.value() == null ? null : evaluationResult.value().valueKind(),
                typecheckResult.resultType().isCollection() ? typecheckResult.resultType().collectionKind().oclName() : null,
                typecheckResult.resultType().isCollection() ? typecheckResult.resultType().elementType().displayName() : null,
                diagnosticMapper.toDto(evaluationResult.diagnostics(), request.sourceId(), request.sourceKind(), request.documentVersion()));
    }
}
