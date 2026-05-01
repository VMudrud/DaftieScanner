package com.vmudrud.daftiescanner.scanner;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class MetricsPublisher {

    static final String METRIC_BLOCK_DETECTED  = "daftiescanner.block_detected";
    static final String METRIC_POLL_ERRORS     = "daftiescanner.poll_errors";
    static final String METRIC_LISTINGS_FOUND  = "daftiescanner.listings_found";
    static final String METRIC_POLL_DURATION   = "daftiescanner.poll_duration";
    static final String TAG_TENANT_ID          = "tenantId";
    static final String TAG_ERROR_TYPE         = "errorType";

    private final MeterRegistry registry;

    void recordBlockDetected(String tenantId) {
        counter(METRIC_BLOCK_DETECTED, TAG_TENANT_ID, tenantId).increment();
    }

    void recordPollError(String tenantId, String errorType) {
        counter(METRIC_POLL_ERRORS, TAG_TENANT_ID, tenantId, TAG_ERROR_TYPE, errorType).increment();
    }

    void recordListingsFound(String tenantId, int count) {
        counter(METRIC_LISTINGS_FOUND, TAG_TENANT_ID, tenantId).increment(count);
    }

    void recordPollDuration(String tenantId, long elapsedMs) {
        timer(METRIC_POLL_DURATION, TAG_TENANT_ID, tenantId).record(Duration.ofMillis(elapsedMs));
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(registry);
    }

    private Timer timer(String name, String... tags) {
        return Timer.builder(name).tags(tags).register(registry);
    }
}
