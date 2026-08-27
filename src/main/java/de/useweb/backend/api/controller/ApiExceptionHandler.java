package de.useweb.backend.api.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import de.useweb.backend.api.dto.error.ApiErrorDto;
import de.useweb.backend.error.FeatureNotAvailableException;
import de.useweb.backend.error.InvalidProjectNameException;
import de.useweb.backend.error.InvalidProjectFormatException;
import de.useweb.backend.error.ObjectModelException;
import de.useweb.backend.error.ProjectNotFoundException;
import de.useweb.backend.error.UmlModelException;
import de.useweb.backend.domain.uml.UmlGeneralizationException;
import de.useweb.backend.domain.uml.UmlNamespaceException;
import de.useweb.backend.domain.uml.UmlAssociationMetadataException;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ProjectNotFoundException.class)
    public ResponseEntity<ApiErrorDto> handleProjectNotFound(ProjectNotFoundException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(withPath(exception.error(), request));
    }

    @ExceptionHandler({UmlModelException.class, ObjectModelException.class, InvalidProjectFormatException.class, InvalidProjectNameException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiErrorDto> handleBadRequest(RuntimeException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(withPath(toApiError(exception), request));
    }

    @ExceptionHandler(FeatureNotAvailableException.class)
    public ResponseEntity<ApiErrorDto> handleFeatureNotAvailable(FeatureNotAvailableException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(withPath(exception.error(), request));
    }

    private ApiErrorDto toApiError(RuntimeException exception) {
        if (exception instanceof UmlModelException umlModelException) {
            return umlModelException.error();
        }
        if (exception instanceof ObjectModelException objectModelException) {
            return objectModelException.error();
        }
        if (exception instanceof InvalidProjectFormatException invalidProjectFormatException) {
            return invalidProjectFormatException.error();
        }
        if (exception instanceof InvalidProjectNameException invalidProjectNameException) {
            return invalidProjectNameException.error();
        }
        if (exception instanceof UmlGeneralizationException generalizationException) {
            return new ApiErrorDto(
                    generalizationException.code(),
                    generalizationException.getMessage(),
                    "Die Vererbung im UML-Modell ist ungueltig.",
                    null,
                    Instant.now(),
                    null,
                    generalizationException.details());
        }
        if (exception instanceof UmlNamespaceException namespaceException) {
            return new ApiErrorDto(
                    namespaceException.code(),
                    namespaceException.getMessage(),
                    "Der Namespace oder Import im UML-Modell ist ungueltig.",
                    null, Instant.now(), null, namespaceException.details());
        }
        if (exception instanceof UmlAssociationMetadataException associationException) {
            return new ApiErrorDto(
                    associationException.code(), associationException.getMessage(),
                    "Die Metadaten des Assoziationsendes sind ungueltig.",
                    null, Instant.now(), null, associationException.details());
        }
        return new ApiErrorDto(
                "API_ERROR",
                exception.getMessage(),
                "Die Anfrage konnte nicht verarbeitet werden.",
                null,
                Instant.now(),
                null,
                Map.of());
    }

    private ApiErrorDto withPath(ApiErrorDto error, HttpServletRequest request) {
        return new ApiErrorDto(
                error.code(),
                error.message(),
                error.userMessage(),
                request.getRequestURI(),
                Instant.now(),
                error.requestId(),
                error.details());
    }
}
