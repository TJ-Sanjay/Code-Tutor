package com.codetutor.backend.common.exception;

import com.codetutor.backend.common.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/** Converts exceptions into the standard {@link ApiResponse} error envelope with sensible HTTP status codes. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ResponseEntity<ApiResponse<Void>> respond(HttpStatus status, String message, String code) {
        return ResponseEntity.status(status).body(ApiResponse.error(message, code));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationErrors(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream().map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(ApiResponse.error(errors.isBlank() ? "Validation failed" : errors, "VALIDATION_FAILED"));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        return respond(HttpStatus.BAD_REQUEST, "The request is missing or has invalid data.", "BAD_REQUEST");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return respond(HttpStatus.BAD_REQUEST, ex.getMessage(), "BAD_REQUEST");
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, ex.getMessage(), "NOT_FOUND");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, "Not found", "NOT_FOUND");
    }

    @ExceptionHandler(NotReadyException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReady(NotReadyException ex) {
        return respond(HttpStatus.CONFLICT, ex.getMessage(), "NOT_READY");
    }

    @ExceptionHandler({LimitExceededException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<ApiResponse<Void>> handleLimit(Exception ex) {
        String msg = ex instanceof MaxUploadSizeExceededException ? "The file is larger than the 100 MB upload limit." : ex.getMessage();
        return respond(HttpStatus.PAYLOAD_TOO_LARGE, msg, "LIMIT_EXCEEDED");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return respond(HttpStatus.UNAUTHORIZED, "Invalid email/username or password", "UNAUTHORIZED");
    }

    @ExceptionHandler({AuthorizationDeniedException.class, SecurityException.class})
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(Exception ex) {
        return respond(HttpStatus.FORBIDDEN, "Access denied", "FORBIDDEN");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        log.error("Internal state error: {}", ex.getMessage());
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage() == null ? "Internal error" : ex.getMessage(), "INTERNAL_ERROR");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", "INTERNAL_SERVER_ERROR");
    }
}
