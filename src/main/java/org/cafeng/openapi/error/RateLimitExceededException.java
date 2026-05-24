package org.cafeng.openapi.error;

/**
 * Thrown when a caller exceeds the configured rate limit for an API.
 */
public class RateLimitExceededException extends DataApiException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("Rate limit exceeded");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
