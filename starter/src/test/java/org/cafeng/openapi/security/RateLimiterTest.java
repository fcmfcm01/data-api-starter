package org.cafeng.openapi.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    @Test
    void shouldAllowRequestsWithinLimit() {
        RateLimiter limiter = new RateLimiter(true);
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryConsume("api-1", "caller-1", 5));
        }
    }

    @Test
    void shouldRejectRequestOverLimit() {
        RateLimiter limiter = new RateLimiter(true);
        assertTrue(limiter.tryConsume("api-1", "caller-1", 2));
        assertTrue(limiter.tryConsume("api-1", "caller-1", 2));
        assertFalse(limiter.tryConsume("api-1", "caller-1", 2));
    }

    @Test
    void shouldIsolateDifferentApis() {
        RateLimiter limiter = new RateLimiter(true);
        assertTrue(limiter.tryConsume("api-a", "caller-1", 1));
        assertTrue(limiter.tryConsume("api-b", "caller-1", 1));
    }

    @Test
    void shouldIsolateDifferentCallers() {
        RateLimiter limiter = new RateLimiter(true);
        assertTrue(limiter.tryConsume("api-1", "caller1", 1));
        assertTrue(limiter.tryConsume("api-1", "caller2", 1));
    }

    @Test
    void shouldAllowAllWhenDisabled() {
        RateLimiter limiter = new RateLimiter(false);
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryConsume("api-1", "caller-1", 1));
        }
    }

    @Test
    void shouldAllowAllWhenNoLimit() {
        RateLimiter limiter = new RateLimiter(true);
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryConsume("api-1", "caller-1", 0));
        }
    }

    @Test
    void shouldReturnZeroRetryWhenTokensAvailable() {
        RateLimiter limiter = new RateLimiter(true);
        limiter.tryConsume("api-1", "caller-1", 5);
        assertEquals(0L, limiter.getRetryAfterSeconds("api-1", "caller-1"));
    }

    @Test
    void shouldComputeRetryAfterWhenExhausted() {
        RateLimiter limiter = new RateLimiter(true);
        limiter.tryConsume("api-1", "caller-1", 1);
        assertFalse(limiter.tryConsume("api-1", "caller-1", 1));
        long retryAfter = limiter.getRetryAfterSeconds("api-1", "caller-1");
        assertTrue(retryAfter >= 1L, "retryAfter should be at least 1 second, got " + retryAfter);
    }

    @Test
    void shouldReturnDefaultRetryForUnknownKey() {
        RateLimiter limiter = new RateLimiter(true);
        assertEquals(1L, limiter.getRetryAfterSeconds("unknown", "unknown"));
    }
}
