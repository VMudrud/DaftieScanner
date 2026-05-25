package com.vmudrud.daftiescanner.notification;

import com.vmudrud.daftiescanner.common.listing.ListingResult;
import com.vmudrud.daftiescanner.common.tenant.Tenant;
import com.vmudrud.daftiescanner.notification.router.Notifier;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("!'${daft.dynamo.seen-table:}'.isBlank()")
public class NotificationGuard {

    private final Notifier notifier;
    private final ConcurrentHashMap<String, ReentrantLock> tenantLocks = new ConcurrentHashMap<>();

    public void notify(Tenant tenant, List<ListingResult> listings) {
        if (listings.isEmpty()) {
            return;
        }
        var lock = tenantLocks.computeIfAbsent(tenant.id(), k -> new ReentrantLock());
        lock.lock();
        try {
            notifier.notify(tenant, listings);
        } finally {
            lock.unlock();
        }
    }
}
