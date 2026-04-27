package com.vmudrud.daftiescanner.scanner;

import com.vmudrud.daftiescanner.client.BlockDetector;
import com.vmudrud.daftiescanner.client.DaftClient;
import com.vmudrud.daftiescanner.config.dto.Tenant;
import com.vmudrud.daftiescanner.store.CursorStore;
import com.vmudrud.daftiescanner.store.DedupStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Slf4j
@Component
@ConditionalOnExpression("!'${daft.dynamo.seen-table:}'.isBlank()")
class ScannerScheduler implements SchedulingConfigurer {

    private static final int BASE_DELAY_SECONDS = 60;
    private static final int JITTER_MIN_SECONDS = 40;
    private static final int JITTER_RANGE_SECONDS = 40; // 40–80s range

    private final List<Tenant> tenants;
    private final DaftClient daftClient;
    private final CursorStore cursorStore;
    private final DedupStore dedupStore;
    private final BlockDetector blockDetector;
    private final EmailNotificationGuard emailGuard;
    private final ThreadPoolTaskScheduler scannerTaskScheduler;

    ScannerScheduler(
            @Qualifier("tenants") List<Tenant> tenants,
            DaftClient daftClient,
            CursorStore cursorStore,
            DedupStore dedupStore,
            BlockDetector blockDetector,
            EmailNotificationGuard emailGuard,
            ThreadPoolTaskScheduler scannerTaskScheduler) {
        this.tenants = tenants;
        this.daftClient = daftClient;
        this.cursorStore = cursorStore;
        this.dedupStore = dedupStore;
        this.blockDetector = blockDetector;
        this.emailGuard = emailGuard;
        this.scannerTaskScheduler = scannerTaskScheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setScheduler(scannerTaskScheduler);
        tenants.forEach(tenant -> {
            var backoff = new TenantBackoff();
            var job = new ScannerJob(tenant, daftClient, cursorStore, dedupStore,
                    blockDetector, emailGuard, backoff);
            registrar.addTriggerTask(job::poll, buildTrigger(backoff));
            log.info("Scheduled scanner for tenant={}", tenant.id());
        });
    }

    private Trigger buildTrigger(TenantBackoff backoff) {
        var rng = new Random();
        return ctx -> {
            var base = Optional.ofNullable(ctx.lastCompletion()).orElse(Instant.now());
            long jitter = JITTER_MIN_SECONDS + rng.nextInt(JITTER_RANGE_SECONDS);
            if (backoff.isBlocked()) {
                return backoff.blockedUntil().plusSeconds(jitter);
            }
            return base.plusSeconds(BASE_DELAY_SECONDS + jitter);
        };
    }
}
