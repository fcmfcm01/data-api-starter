package org.cafeng.openapi.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLTimeoutException;
import java.time.Instant;
import java.util.UUID;

@RestControllerAdvice
/**
 * Global exception handler that translates framework exceptions into
 * standardized JSON error responses with correlation IDs.
 *
 * <p>Handles SQL timeouts (503), validation errors (400), authentication
 * failures (401), rate limiting (429), not-found (404), and generic
 * server errors (500). All messages pass through
 * {@link ErrorMessageSanitizer} to prevent schema leakage.</p>
 */
public class DataApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(DataApiExceptionHandler.class);

    @ExceptionHandler(SQLTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleTimeout(SQLTimeoutException ex) {
        log.error("Query timeout", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "Query timeout",
                        ErrorMessageSanitizer.sanitize(ex.getMessage()),
                        generateRequestId()
                ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        "Bad request",
                        ex.getMessage(),
                        generateRequestId()
                ));
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ErrorResponse> handleAuthFailed(AuthenticationFailedException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(
                        HttpStatus.UNAUTHORIZED.value(),
                        "Unauthorized",
                        ex.getMessage(),
                        ex.getCorrelationId()
                ));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException ex) {
        log.warn("Rate limit exceeded: retryAfter={}s", ex.getRetryAfterSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(new ErrorResponse(
                        HttpStatus.TOO_MANY_REQUESTS.value(),
                        "Rate limit exceeded",
                        ex.getMessage(),
                        ex.getCorrelationId()
                ));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(
                        HttpStatus.NOT_FOUND.value(),
                        "Not found",
                        ex.getMessage(),
                        ex.getCorrelationId()
                ));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
        log.warn("Validation failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        "Validation failed",
                        ex.getMessage(),
                        ex.getCorrelationId()
                ));
    }

    @ExceptionHandler(DataApiException.class)
    public ResponseEntity<ErrorResponse> handleDataApi(DataApiException ex) {
        log.error("Data API error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Internal server error",
                        ErrorMessageSanitizer.sanitize(ex.getMessage()),
                        ex.getCorrelationId()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.warn("Internal error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Internal server error",
                        ErrorMessageSanitizer.sanitize(ex.getMessage()),
                        generateRequestId()
                ));
    }

    private String generateRequestId() {
        return UUID.randomUUID().toString();
    }

    public record ErrorResponse(
            int status,
            String error,
            String message,
            String requestId
    ) {}
}
