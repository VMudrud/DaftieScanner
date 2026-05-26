package com.vmudrud.daftiescanner.search.scheduler;

import com.vmudrud.daftiescanner.search.client.BlockDetector;
import com.vmudrud.daftiescanner.search.client.DaftClient;
import com.vmudrud.daftiescanner.common.tenant.Tenant;
import com.vmudrud.daftiescanner.common.store.AlertThrottle;
import com.vmudrud.daftiescanner.common.store.CursorStore;
import com.vmudrud.daftiescanner.common.store.DedupStore;
import com.vmudrud.daftiescanner.search.metrics.MetricsPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "daft.dynamo.enabled", havingValue = "true", matchIfMissing = true)
class ScannerScheduler implements SchedulingConfigurer {

    @Value("${daft.scanner.base-delay-seconds:60}")
    private int baseDelaySeconds;
    @Value("${daft.scanner.force-cold-start:false}")
    private boolean forceColdStart;
    @Value("${daft.scanner.jitter-min-seconds:40}")
    private int jitterMinSeconds;
    @Value("${daft.scanner.jitter-range-seconds:40}")
    private int jitterRangeSeconds;

    @Qualifier("tenants")
    private final List<Tenant> tenants;
    private final DaftClient daftClient;
    private final CursorStore cursorStore;
    private final DedupStore dedupStore;
    private final BlockDetector blockDetector;
    private final ApplicationEventPublisher eventPublisher;
    private final ThreadPoolTaskScheduler scannerTaskScheduler;
    private final MetricsPublisher metricsPublisher;
    private final AlertThrottle alertThrottle;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setScheduler(scannerTaskScheduler);
        tenants.forEach(tenant -> {
            var backoff = new TenantBackoff();
            var job = new ScannerJob(tenant, daftClient, cursorStore, dedupStore,
                    blockDetector, eventPublisher, backoff, metricsPublisher, alertThrottle,
                    new AtomicBoolean(forceColdStart));
            registrar.addTriggerTask(job::poll, buildTrigger(backoff));
            log.info("Scheduled scanner for tenant={}", tenant.id());
        });
    }

    private Trigger buildTrigger(TenantBackoff backoff) {
        var rng = new Random();
        return ctx -> {
            var base = Optional.ofNullable(ctx.lastCompletion()).orElse(Instant.now());
            long jitter = jitterMinSeconds + rng.nextInt(jitterRangeSeconds);
            if (backoff.isBlocked()) {
                return backoff.blockedUntil().plusSeconds(jitter);
            }
            return base.plusSeconds(baseDelaySeconds + jitter);
        };
    }
}
