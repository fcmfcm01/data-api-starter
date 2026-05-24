package org.cafeng.openapi.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLTimeoutException;
import java.util.UUID;

@RestControllerAdvice
public class DataApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(DataApiExceptionHandler.class);

    @ExceptionHandler(SQLTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleTimeout(SQLTimeoutException ex) {
        log.error("Query timeout", ex);
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "Query timeout",
                ErrorMessageSanitizer.sanitize(ex.getMessage()), generateRequestId());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Bad request", ex.getMessage(), generateRequestId());
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ErrorResponse> handleAuthFailed(AuthenticationFailedException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), ex.getCorrelationId());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException ex) {
        log.warn("Rate limit exceeded: retryAfter={}s", ex.getRetryAfterSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(new ErrorResponse(HttpStatus.TOO_MANY_REQUESTS.value(),
                        "Rate limit exceeded", ex.getMessage(), ex.getCorrelationId()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "Not found", ex.getMessage(), ex.getCorrelationId());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
        log.warn("Validation failed: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed", ex.getMessage(), ex.getCorrelationId());
    }

    @ExceptionHandler(DataApiException.class)
    public ResponseEntity<ErrorResponse> handleDataApi(DataApiException ex) {
        log.error("Data API error", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                ErrorMessageSanitizer.sanitize(ex.getMessage()), ex.getCorrelationId());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.warn("Internal error", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                ErrorMessageSanitizer.sanitize(ex.getMessage()), generateRequestId());
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String error,
                                                        String message, String requestId) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), error, message, requestId));
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
