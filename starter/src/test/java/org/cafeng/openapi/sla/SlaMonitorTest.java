package org.cafeng.openapi.sla;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SlaMonitorTest {

    private SimpleMeterRegistry meterRegistry;
    private SlaMonitor monitor;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        monitor = new SlaMonitor(meterRegistry);
    }

    @Test
    void shouldRecordLatency() {
        monitor.recordLatency("test-api", 100);

        var timer = meterRegistry.find("dataapi.latency").tag("api", "test-api").timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
        assertTrue(timer.totalTime(TimeUnit.MILLISECONDS) > 0);
    }

    @Test
    void shouldRecordSuccess() {
        monitor.recordSuccess("test-api");

        var counter = meterRegistry.find("dataapi.success").tag("api", "test-api").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void shouldRecordError() {
        monitor.recordError("test-api", "timeout");

        var counter = meterRegistry.find("dataapi.error")
                .tag("api", "test-api").tag("type", "timeout").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void shouldRecordQuery() {
        monitor.recordQuery("test-api");

        var counter = meterRegistry.find("dataapi.query").tag("api", "test-api").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void shouldAccumulateMultipleRecords() {
        monitor.recordSuccess("test-api");
        monitor.recordSuccess("test-api");
        monitor.recordError("test-api", "timeout");

        var successCounter = meterRegistry.find("dataapi.success").tag("api", "test-api").counter();
        assertEquals(2.0, successCounter.count());

        var errorCounter = meterRegistry.find("dataapi.error")
                .tag("api", "test-api").tag("type", "timeout").counter();
        assertEquals(1.0, errorCounter.count());
    }

    @Test
    void shouldSeparateMetricsByApiId() {
        monitor.recordSuccess("api-1");
        monitor.recordSuccess("api-2");

        var c1 = meterRegistry.find("dataapi.success").tag("api", "api-1").counter();
        var c2 = meterRegistry.find("dataapi.success").tag("api", "api-2").counter();
        assertEquals(1.0, c1.count());
        assertEquals(1.0, c2.count());
    }
}
