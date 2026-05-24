package org.cafeng.openapi.security;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Token-bucket rate limiter with per-API, per-caller granularity.
 *
 * <p>Each unique {@code apiId:callerId} pair gets an independent bucket.
 * Tokens refill at a rate of one per second. Can be disabled globally
 * via {@code data-api.rate-limit-enabled}.</p>
 */
public class RateLimiter {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final boolean enabled;

    public RateLimiter(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean tryConsume(String apiId, String callerId, int rateLimit) {
        if (!enabled || rateLimit <= 0) {
            return true;
        }
        String key = apiId + ":" + callerId;
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(rateLimit));
        synchronized (bucket) {
            long now = System.currentTimeMillis();
            // Refill tokens based on elapsed time (1 token per second = rate tokens per rateLimit seconds)
            double tokensPerMs = (double) bucket.capacity / (bucket.capacity * 1000.0);
            bucket.tokens = Math.min(bucket.capacity, bucket.tokens + (now - bucket.lastRefill) * tokensPerMs);
            bucket.lastRefill = now;
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return true;
            }
            return false;
        }
    }

    public long getRetryAfterSeconds(String apiId, String callerId) {
        return 1L;
    }

    private static class Bucket {
        final int capacity;
        double tokens;
        long lastRefill;

        Bucket(int capacity) {
            this.capacity = capacity;
            this.tokens = capacity;
            this.lastRefill = System.currentTimeMillis();
        }
    }
}
