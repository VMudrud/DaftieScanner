package com.vmudrud.daftiescanner.notification;

import com.vmudrud.daftiescanner.common.listing.ListingResult;
import com.vmudrud.daftiescanner.common.tenant.Tenant;
import com.vmudrud.daftiescanner.notification.router.Notifier;
import com.vmudrud.daftiescanner.common.store.DedupStore;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("!'${daft.dynamo.seen-table:}'.isBlank()")
public class EmailNotificationGuard {

    private final DedupStore dedupStore;
    private final Notifier notifier;
    private final ConcurrentHashMap<String, ReentrantLock> emailLocks = new ConcurrentHashMap<>();

    public int tryNotify(Tenant tenant, List<ListingResult> listings) {
        var lock = emailLocks.computeIfAbsent(tenant.email(), k -> new ReentrantLock());
        lock.lock();
        try {
            List<ListingResult> unsent = listings.stream()
                    .filter(l -> !dedupStore.notifiedByEmail(tenant.email(), l.id()))
                    .toList();
            if (unsent.isEmpty()) {
                return 0;
            }
            notifier.notify(tenant, unsent);
            unsent.forEach(l -> dedupStore.markNotifiedByEmail(tenant.email(), l.id()));
            return unsent.size();
        } finally {
            lock.unlock();
        }
    }
}
