package com.theragenx.icsr.exception;

import com.theragenx.icsr.model.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Single place for all error responses. Every handler produces the same {@link ApiError} shape.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 404 ────────────────────────────────────────────────────────────────────

    @ExceptionHandler(CaseNotFoundException.class)
    public ResponseEntity<ApiError> handleCaseNotFound(
            CaseNotFoundException ex, HttpServletRequest req) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error(404, "NOT_FOUND", ex.getMessage(), req));
    }

    // ── 400: bean validation (@Valid on @RequestBody) ──────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {

        // Include the field name so the caller knows exactly which JSON field failed.
        // e.g. "case_id: must not be blank; question: must not be blank"
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return ResponseEntity.badRequest()
                .body(error(400, "BAD_REQUEST", message, req));
    }

    // ── 400: malformed JSON or missing @RequestBody ────────────────────────────

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedJson(
            HttpMessageNotReadableException ex, HttpServletRequest req) {

        return ResponseEntity.badRequest()
                .body(error(400, "BAD_REQUEST", "Request body is missing or contains malformed JSON", req));
    }

    // ── 400: missing required @RequestParam (e.g. ?caseId= omitted) ───────────

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest req) {

        String message = "Required query parameter '" + ex.getParameterName() + "' is missing";
        return ResponseEntity.badRequest()
                .body(error(400, "BAD_REQUEST", message, req));
    }

    // ── Inline ResponseStatusException (used by controllers for custom 400s) ───

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(
            ResponseStatusException ex, HttpServletRequest req) {

        int code = ex.getStatusCode().value();
        // getReason() is null when ResponseStatusException is thrown without a reason string;
        // fall back to getMessage() so the error body never has "message": null.
        String reason = Objects.requireNonNullElse(ex.getReason(), ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode())
                .body(error(code, HttpStatus.resolve(code) != null
                        ? HttpStatus.resolve(code).name()
                        : "ERROR",
                        reason, req));
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private ApiError error(int status, String errorCode, String message, HttpServletRequest req) {
        return new ApiError(status, errorCode, message, req.getRequestURI(), Instant.now());
    }
}
