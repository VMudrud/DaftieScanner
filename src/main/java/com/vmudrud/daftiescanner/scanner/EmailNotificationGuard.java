package com.vmudrud.daftiescanner.scanner;

import com.vmudrud.daftiescanner.client.dto.ListingResult;
import com.vmudrud.daftiescanner.config.dto.Tenant;
import com.vmudrud.daftiescanner.notifier.Notifier;
import com.vmudrud.daftiescanner.store.DedupStore;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("!'${daft.dynamo.seen-table:}'.isBlank()")
class EmailNotificationGuard {

    private final DedupStore dedupStore;
    private final Notifier notifier;
    private final ConcurrentHashMap<String, ReentrantLock> emailLocks = new ConcurrentHashMap<>();

    boolean tryNotify(Tenant tenant, ListingResult listing) {
        var lock = emailLocks.computeIfAbsent(tenant.email(), k -> new ReentrantLock());
        lock.lock();
        try {
            if (dedupStore.notifiedByEmail(tenant.email(), listing.id())) {
                return false;
            }
            notifier.notify(tenant, listing);
            dedupStore.markNotifiedByEmail(tenant.email(), listing.id());
            return true;
        } finally {
            lock.unlock();
        }
    }
}
