package com.vmudrud.daftiescanner.scanner;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsPublisherTest {

    private MeterRegistry registry;
    private MetricsPublisher publisher;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        publisher = new MetricsPublisher(registry);
    }

    @Test
    void recordBlockDetected_incrementsCounterWithTenantTag() {
        publisher.recordBlockDetected("t1");
        publisher.recordBlockDetected("t1");

        Counter counter = registry.find(MetricsPublisher.METRIC_BLOCK_DETECTED)
                .tag(MetricsPublisher.TAG_TENANT_ID, "t1")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2.0);
    }

    @Test
    void recordBlockDetected_differentTenants_separateCounters() {
        publisher.recordBlockDetected("t1");
        publisher.recordBlockDetected("t2");

        assertThat(registry.find(MetricsPublisher.METRIC_BLOCK_DETECTED)
                .tag(MetricsPublisher.TAG_TENANT_ID, "t1").counter().count()).isEqualTo(1.0);
        assertThat(registry.find(MetricsPublisher.METRIC_BLOCK_DETECTED)
                .tag(MetricsPublisher.TAG_TENANT_ID, "t2").counter().count()).isEqualTo(1.0);
    }

    @Test
    void recordPollError_incrementsCounterWithTenantAndErrorTypeTags() {
        publisher.recordPollError("t1", "BLOCKED");
        publisher.recordPollError("t1", "BLOCKED");
        publisher.recordPollError("t1", "UNKNOWN");

        Counter blocked = registry.find(MetricsPublisher.METRIC_POLL_ERRORS)
                .tag(MetricsPublisher.TAG_TENANT_ID, "t1")
                .tag(MetricsPublisher.TAG_ERROR_TYPE, "BLOCKED")
                .counter();
        Counter unknown = registry.find(MetricsPublisher.METRIC_POLL_ERRORS)
                .tag(MetricsPublisher.TAG_TENANT_ID, "t1")
                .tag(MetricsPublisher.TAG_ERROR_TYPE, "UNKNOWN")
                .counter();

        assertThat(blocked).isNotNull();
        assertThat(blocked.count()).isEqualTo(2.0);
        assertThat(unknown).isNotNull();
        assertThat(unknown.count()).isEqualTo(1.0);
    }

    @Test
    void recordListingsFound_incrementsByCount() {
        publisher.recordListingsFound("t1", 20);
        publisher.recordListingsFound("t1", 5);

        Counter counter = registry.find(MetricsPublisher.METRIC_LISTINGS_FOUND)
                .tag(MetricsPublisher.TAG_TENANT_ID, "t1")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(25.0);
    }

    @Test
    void recordListingsFound_zeroCount_counterExists() {
        publisher.recordListingsFound("t1", 0);

        Counter counter = registry.find(MetricsPublisher.METRIC_LISTINGS_FOUND)
                .tag(MetricsPublisher.TAG_TENANT_ID, "t1")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(0.0);
    }

    @Test
    void recordPollDuration_timerRecordsElapsed() {
        publisher.recordPollDuration("t1", 500L);
        publisher.recordPollDuration("t1", 1000L);

        Timer timer = registry.find(MetricsPublisher.METRIC_POLL_DURATION)
                .tag(MetricsPublisher.TAG_TENANT_ID, "t1")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(2);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(1500.0);
    }

    @Test
    void recordPollDuration_differentTenants_separateTimers() {
        publisher.recordPollDuration("t1", 300L);
        publisher.recordPollDuration("t2", 700L);

        assertThat(registry.find(MetricsPublisher.METRIC_POLL_DURATION)
                .tag(MetricsPublisher.TAG_TENANT_ID, "t1").timer().count()).isEqualTo(1);
        assertThat(registry.find(MetricsPublisher.METRIC_POLL_DURATION)
                .tag(MetricsPublisher.TAG_TENANT_ID, "t2").timer().count()).isEqualTo(1);
    }
}
