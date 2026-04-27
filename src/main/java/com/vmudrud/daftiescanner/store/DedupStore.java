package com.vmudrud.daftiescanner.store;

import java.time.Instant;

public interface DedupStore {
    boolean seen(String tenantId, long listingId);
    void markSeen(String tenantId, long listingId, Instant postedAt);

    boolean notifiedByEmail(String email, long listingId);
    void markNotifiedByEmail(String email, long listingId);
}
