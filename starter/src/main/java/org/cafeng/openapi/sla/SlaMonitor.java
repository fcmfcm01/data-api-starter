package org.cafeng.openapi.sla;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Records query performance and error metrics via Micrometer.
 *
 * <p>Tracks four metric types per API: {@code dataapi.latency} (timer),
 * {@code dataapi.success} (counter), {@code dataapi.error} (counter tagged
 * by exception type), and {@code dataapi.query} (total request counter).</p>
 */
public class SlaMonitor {

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();

    public SlaMonitor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordLatency(String apiId, long durationMs) {
        Timer timer = timers.computeIfAbsent(apiId, id ->
                Timer.builder("dataapi.latency")
                        .tag("api", id)
                        .register(meterRegistry));
        timer.record(java.time.Duration.ofMillis(durationMs));
    }

    public void recordSuccess(String apiId) {
        meterRegistry.counter("dataapi.success", "api", apiId).increment();
    }

    public void recordError(String apiId, String errorType) {
        meterRegistry.counter("dataapi.error", "api", apiId, "type", errorType).increment();
    }

    public void recordQuery(String apiId) {
        meterRegistry.counter("dataapi.query", "api", apiId).increment();
    }
}