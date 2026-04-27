package com.vmudrud.daftiescanner.scanner;

import com.vmudrud.daftiescanner.client.BlockDetector;
import com.vmudrud.daftiescanner.client.BlockStatus;
import com.vmudrud.daftiescanner.client.DaftClient;
import com.vmudrud.daftiescanner.client.dto.ListingResult;
import com.vmudrud.daftiescanner.client.dto.SearchResult;
import com.vmudrud.daftiescanner.config.dto.Tenant;
import com.vmudrud.daftiescanner.store.dto.Cursor;
import com.vmudrud.daftiescanner.store.CursorStore;
import com.vmudrud.daftiescanner.store.DedupStore;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
class ScannerJob {

    private final Tenant tenant;
    private final DaftClient daftClient;
    private final CursorStore cursorStore;
    private final DedupStore dedupStore;
    private final BlockDetector blockDetector;
    private final EmailNotificationGuard emailGuard;
    private final TenantBackoff backoff;

    ScannerJob(Tenant tenant, DaftClient daftClient, CursorStore cursorStore,
               DedupStore dedupStore, BlockDetector blockDetector,
               EmailNotificationGuard emailGuard, TenantBackoff backoff) {
        this.tenant = tenant;
        this.daftClient = daftClient;
        this.cursorStore = cursorStore;
        this.dedupStore = dedupStore;
        this.blockDetector = blockDetector;
        this.emailGuard = emailGuard;
        this.backoff = backoff;
    }

    void poll() {
        long start = System.currentTimeMillis();
        String tenantId = tenant.id();
        try {
            Optional<Cursor> cursorOpt = cursorStore.load(tenantId);
            List<ListingResult> listings = daftClient.search(tenant.filter())
                    .listings().stream()
                    .map(SearchResult.ListingWrapper::listing)
                    .toList();

            if (cursorOpt.isEmpty()) {
                coldStart(tenantId, listings, start);
                return;
            }

            normalPoll(tenantId, listings, cursorOpt.get(), start);

        } catch (Exception e) {
            handleError(tenantId, e, start);
        }
    }

    private void coldStart(String tenantId, List<ListingResult> listings, long start) {
        markAllSeen(tenantId, listings);
        long maxDate = listings.stream().mapToLong(ListingResult::publishDate).max()
                .orElse(Instant.now().toEpochMilli());
        long topId = listings.isEmpty() ? 0L : listings.get(0).id();
        cursorStore.save(tenantId, maxDate, topId);
        log.info("tenant={} cold-start found={} new=0 skipped={} elapsed={}ms",
                tenantId, listings.size(), listings.size(), System.currentTimeMillis() - start);
        backoff.reset();
    }

    private void normalPoll(String tenantId, List<ListingResult> listings, Cursor cursor, long start) {
        long notified = listings.stream().filter(l -> processListing(tenantId, l, cursor)).count();
        advanceCursor(tenantId, listings, cursor);
        log.info("tenant={} found={} new={} skipped={} elapsed={}ms",
                tenantId, listings.size(), notified, listings.size() - notified,
                System.currentTimeMillis() - start);
        backoff.reset();
    }

    private void markAllSeen(String tenantId, List<ListingResult> listings) {
        listings.forEach(l ->
            dedupStore.markSeen(tenantId, l.id(), Instant.ofEpochMilli(l.publishDate())));
    }

    private boolean processListing(String tenantId, ListingResult listing, Cursor cursor) {
        if (dedupStore.seen(tenantId, listing.id())) {
            return false;
        }
        dedupStore.markSeen(tenantId, listing.id(), Instant.ofEpochMilli(listing.publishDate()));
        if (listing.publishDate() <= cursor.lastPostedAt()) {
            return false;
        }
        return emailGuard.tryNotify(tenant, listing);
    }

    private void advanceCursor(String tenantId, List<ListingResult> listings, Cursor cursor) {
        listings.stream().mapToLong(ListingResult::publishDate).max()
                .ifPresent(maxDate -> {
                    if (maxDate > cursor.lastPostedAt()) {
                        cursorStore.save(tenantId, maxDate, listings.get(0).id());
                    }
                });
    }

    private void handleError(String tenantId, Exception e, long start) {
        BlockStatus status = blockDetector.classify(e);
        switch (status) {
            case BLOCKED -> {
                backoff.recordBlock();
                log.warn("tenant={} BLOCKED backoff-level={} until={}", tenantId, backoff.level(), backoff.blockedUntil());
            }
            case RATE_LIMITED -> {
                backoff.recordRateLimit();
                log.warn("tenant={} RATE_LIMITED until={}", tenantId, backoff.blockedUntil());
            }
            default -> log.error("tenant={} poll error: {}", tenantId, e.getMessage(), e);
        }
        log.info("tenant={} found=0 new=0 skipped=0 elapsed={}ms", tenantId, System.currentTimeMillis() - start);
    }
}
