package org.cafeng.openapi.security;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Token-bucket rate limiter with per-API, per-caller granularity.
 *
 * <p>Each unique {@code apiId:callerId} pair gets an independent bucket.
 * Tokens refill at a rate of one per second. Can be disabled globally
 * via {@code data-api.rate-limit-enabled}.</p>
 *
 * @implNote Thread-safe. Uses {@link ConcurrentHashMap} for bucket storage.
 * Buckets evicted after 2&times; window ({@code STALE_THRESHOLD_MS}) of inactivity.
 */
public class RateLimiter {

    private static final long STALE_THRESHOLD_MS = 10 * 60 * 1000; // 10 minutes
    private static final int MAX_BUCKETS = 10_000;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final boolean enabled;

    public RateLimiter(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean tryConsume(String apiId, String callerId, int rateLimit) {
        if (!enabled || rateLimit <= 0) {
            return true;
        }
        evictStaleBuckets();
        String key = apiId + ":" + callerId;
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(rateLimit));
        synchronized (bucket) {
            long now = System.currentTimeMillis();
            refill(bucket, now);
            bucket.lastAccessTime = now;
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return true;
            }
            return false;
        }
    }

    public long getRetryAfterSeconds(String apiId, String callerId) {
        String key = apiId + ":" + callerId;
        Bucket bucket = buckets.get(key);
        if (bucket == null) return 1L;
        synchronized (bucket) {
            refill(bucket, System.currentTimeMillis());
            if (bucket.tokens >= 1.0) return 0L;
            double tokensNeeded = 1.0 - bucket.tokens;
            double tokensPerMs = 1.0 / 1000.0; // 1 token per second
            long waitMs = (long) Math.ceil(tokensNeeded / tokensPerMs);
            return Math.max(1L, waitMs / 1000);
        }
    }

    private void refill(Bucket bucket, long now) {
        double tokensPerMs = 1.0 / 1000.0; // 1 token per second
        bucket.tokens = Math.min(bucket.capacity, bucket.tokens + (now - bucket.lastRefill) * tokensPerMs);
        bucket.lastRefill = now;
    }

    private void evictStaleBuckets() {
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(entry ->
                (now - entry.getValue().lastAccessTime) > STALE_THRESHOLD_MS);
        if (buckets.size() > MAX_BUCKETS) {
            buckets.clear(); // safety net
        }
    }

    private static class Bucket {
        final int capacity;
        double tokens;
        long lastRefill;
        volatile long lastAccessTime;

        Bucket(int capacity) {
            this.capacity = capacity;
            this.tokens = capacity;
            long now = System.currentTimeMillis();
            this.lastRefill = now;
            this.lastAccessTime = now;
        }
    }
}
