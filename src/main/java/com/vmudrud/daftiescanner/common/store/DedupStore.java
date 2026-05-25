package com.vmudrud.daftiescanner.common.store;

import java.time.Instant;

public interface DedupStore {
    boolean seen(String tenantId, long listingId);
    void markSeen(String tenantId, long listingId, Instant postedAt);

    boolean notifiedBy(String channel, String destination, long listingId);
    void markNotifiedBy(String channel, String destination, long listingId);
}
