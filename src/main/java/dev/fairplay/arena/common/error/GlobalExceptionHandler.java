package dev.fairplay.arena.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    public record ValidationError(String field, String message) {}

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(ApiException ex, HttpServletRequest request) {
        ProblemDetail problem = buildProblem(ex.getErrorCode(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(ex.getErrorCode().getStatus())
                .contentType(PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ProblemDetail problem = buildProblem(ErrorCode.FORBIDDEN, "Access is denied", request.getRequestURI());
        return ResponseEntity.status(ErrorCode.FORBIDDEN.getStatus())
                .contentType(PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<ValidationError> errors = ex.getConstraintViolations().stream()
                .map(v -> {
                    String path = v.getPropertyPath().toString();
                    int dot = path.lastIndexOf('.');
                    String field = dot >= 0 ? path.substring(dot + 1) : path;
                    return new ValidationError(field, v.getMessage());
                })
                .toList();
        ProblemDetail problem = buildProblem(ErrorCode.VALIDATION_FAILED, "Request validation failed", request.getRequestURI());
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().contentType(PROBLEM_JSON).body(problem);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        ProblemDetail problem = buildProblem(
                ErrorCode.CONCURRENT_MODIFICATION,
                "Resource was modified concurrently; please retry",
                request.getRequestURI());
        return ResponseEntity.status(ErrorCode.CONCURRENT_MODIFICATION.getStatus())
                .contentType(PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String message = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        if (message != null && message.contains("ux_players_username_lower")) {
            ProblemDetail problem = buildProblem(ErrorCode.USERNAME_TAKEN, "Username is already taken", request.getRequestURI());
            return ResponseEntity.status(ErrorCode.USERNAME_TAKEN.getStatus()).contentType(PROBLEM_JSON).body(problem);
        }
        if (message != null && message.contains("ux_players_email_lower")) {
            ProblemDetail problem = buildProblem(ErrorCode.EMAIL_TAKEN, "Email is already taken", request.getRequestURI());
            return ResponseEntity.status(ErrorCode.EMAIL_TAKEN.getStatus()).contentType(PROBLEM_JSON).body(problem);
        }
        log.error("Unexpected data integrity violation", ex);
        ProblemDetail problem = buildProblem(ErrorCode.INTERNAL_ERROR, "Unexpected error", request.getRequestURI());
        return ResponseEntity.internalServerError().contentType(PROBLEM_JSON).body(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = buildProblem(ErrorCode.INTERNAL_ERROR, "Unexpected error", request.getRequestURI());
        return ResponseEntity.internalServerError().contentType(PROBLEM_JSON).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<ValidationError> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(err -> errors.add(new ValidationError(err.getField(), err.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors()
                .forEach(err -> errors.add(new ValidationError(err.getObjectName(), err.getDefaultMessage())));
        ProblemDetail problem = buildProblem(ErrorCode.VALIDATION_FAILED, "Request validation failed", extractUri(request));
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().contentType(PROBLEM_JSON).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<ValidationError> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String param = result.getMethodParameter().getParameterName();
            String field = param != null ? param : "parameter";
            result.getResolvableErrors().forEach(err ->
                    errors.add(new ValidationError(field, err.getDefaultMessage())));
        });
        ProblemDetail problem = buildProblem(ErrorCode.VALIDATION_FAILED, "Request validation failed", extractUri(request));
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().contentType(PROBLEM_JSON).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = buildProblem(
                ErrorCode.MALFORMED_REQUEST,
                "Malformed JSON request body",
                extractUri(request));
        return ResponseEntity.badRequest().contentType(PROBLEM_JSON).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String field = ex.getPropertyName() != null ? ex.getPropertyName() : "parameter";
        ProblemDetail problem = buildProblem(
                ErrorCode.VALIDATION_FAILED,
                "Invalid value for parameter '" + field + "'",
                extractUri(request));
        problem.setProperty("errors", List.of(new ValidationError(field, "invalid value")));
        return ResponseEntity.badRequest().contentType(PROBLEM_JSON).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = buildProblem(
                ErrorCode.VALIDATION_FAILED,
                "Missing required parameter '" + ex.getParameterName() + "'",
                extractUri(request));
        problem.setProperty("errors", List.of(new ValidationError(ex.getParameterName(), "is required")));
        return ResponseEntity.badRequest().contentType(PROBLEM_JSON).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = buildProblem(ErrorCode.NOT_FOUND, "Resource not found", extractUri(request));
        return ResponseEntity.status(ErrorCode.NOT_FOUND.getStatus()).contentType(PROBLEM_JSON).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = buildProblem(ErrorCode.METHOD_NOT_ALLOWED, ex.getMessage(), extractUri(request));
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getStatus()).contentType(PROBLEM_JSON).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        ErrorCode code = statusCode.is4xxClientError() ? ErrorCode.VALIDATION_FAILED : ErrorCode.INTERNAL_ERROR;
        String detail = statusCode.is4xxClientError() ? ex.getMessage() : "Unexpected error";
        if (statusCode.is5xxServerError()) {
            log.error("Internal Spring MVC exception", ex);
        }
        ProblemDetail problem = buildProblem(code, detail, extractUri(request));
        problem.setStatus(statusCode.value());
        return ResponseEntity.status(statusCode).contentType(PROBLEM_JSON).body(problem);
    }

    static ProblemDetail buildProblem(ErrorCode errorCode, String detail, String instanceUri) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.getStatus(), detail);
        problem.setType(errorCode.typeUri());
        problem.setTitle(errorCode.getTitle());
        if (instanceUri != null && !instanceUri.isBlank()) {
            problem.setInstance(URI.create(instanceUri));
        }
        problem.setProperty("code", errorCode.name());
        return problem;
    }

    private static String extractUri(WebRequest request) {
        if (request instanceof ServletWebRequest swr) {
            return swr.getRequest().getRequestURI();
        }
        return null;
    }
}
