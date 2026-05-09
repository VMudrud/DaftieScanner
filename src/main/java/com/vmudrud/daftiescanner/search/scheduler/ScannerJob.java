package com.vmudrud.daftiescanner.search.scheduler;

import com.vmudrud.daftiescanner.common.event.NewListingsFoundEvent;
import com.vmudrud.daftiescanner.search.client.BlockDetector;
import com.vmudrud.daftiescanner.search.client.BlockStatus;
import com.vmudrud.daftiescanner.search.client.DaftClient;
import com.vmudrud.daftiescanner.common.listing.BerRating;
import com.vmudrud.daftiescanner.common.listing.ListingResult;
import com.vmudrud.daftiescanner.common.listing.SearchResult;
import com.vmudrud.daftiescanner.common.tenant.Tenant;
import com.vmudrud.daftiescanner.common.store.AlertThrottle;
import com.vmudrud.daftiescanner.common.store.dto.Cursor;
import com.vmudrud.daftiescanner.common.store.CursorStore;
import com.vmudrud.daftiescanner.common.store.DedupStore;
import com.vmudrud.daftiescanner.search.metrics.MetricsPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RequiredArgsConstructor
class ScannerJob {

    private final Tenant tenant;
    private final DaftClient daftClient;
    private final CursorStore cursorStore;
    private final DedupStore dedupStore;
    private final BlockDetector blockDetector;
    private final ApplicationEventPublisher eventPublisher;
    private final TenantBackoff backoff;
    private final MetricsPublisher metricsPublisher;
    private final AlertThrottle alertThrottle;
    private final AtomicBoolean forceColdStart;

    void poll() {
        long start = System.currentTimeMillis();
        String tenantId = tenant.id();
        try {
            Optional<Cursor> cursorOpt = cursorStore.load(tenantId);
            List<ListingResult> listings = daftClient.search(tenant.filter())
                    .listings().stream()
                    .map(SearchResult.ListingWrapper::listing)
                    .toList();

            metricsPublisher.recordListingsFound(tenantId, listings.size());
            warnUnknownBerRatings(tenantId, listings);

            if (cursorOpt.isEmpty() || forceColdStart.getAndSet(false)) {
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
        long elapsed = System.currentTimeMillis() - start;
        log.info("tenant={} cold-start found={} new=0 skipped={} elapsed={}ms",
                tenantId, listings.size(), listings.size(), elapsed);
        metricsPublisher.recordPollDuration(tenantId, elapsed);
        backoff.reset();
    }

    private void normalPoll(String tenantId, List<ListingResult> listings, Cursor cursor, long start) {
        List<ListingResult> newListings = collectNewListings(tenantId, listings, cursor);
        advanceCursor(tenantId, listings, cursor);
        long elapsed = System.currentTimeMillis() - start;
        log.info("tenant={} found={} new={} skipped={} elapsed={}ms",
                tenantId, listings.size(), newListings.size(), listings.size() - newListings.size(), elapsed);
        metricsPublisher.recordPollDuration(tenantId, elapsed);
        backoff.reset();
        if (!newListings.isEmpty()) {
            eventPublisher.publishEvent(new NewListingsFoundEvent(tenant, newListings));
        }
    }

    private void markAllSeen(String tenantId, List<ListingResult> listings) {
        listings.forEach(l ->
            dedupStore.markSeen(tenantId, l.id(), Instant.ofEpochMilli(l.publishDate())));
    }

    private List<ListingResult> collectNewListings(String tenantId, List<ListingResult> listings, Cursor cursor) {
        List<BerRating> berFilter = tenant.filter().berRatings();
        List<ListingResult> result = new ArrayList<>();
        listings.forEach(listing -> {
            if (dedupStore.seen(tenantId, listing.id())) {
                return;
            }
            dedupStore.markSeen(tenantId, listing.id(), Instant.ofEpochMilli(listing.publishDate()));
            if (listing.publishDate() <= cursor.lastPostedAt()) {
                return;
            }
            if (!berFilter.isEmpty() && !passedBerFilter(listing, berFilter)) {
                log.info("tenant={} skipped by BER filter id={} title=\"{}\" price=\"{}\" ber={} path={}",
                        tenantId, listing.id(), listing.title(), listing.price(), berCode(listing), listing.seoFriendlyPath());
                return;
            }
            log.info("tenant={} new listing id={} title=\"{}\" price=\"{}\" ber={} path={}",
                    tenantId, listing.id(), listing.title(), listing.price(), berCode(listing), listing.seoFriendlyPath());
            result.add(listing);
        });
        return result;
    }

    private static String berCode(ListingResult listing) {
        return listing.ber() != null ? listing.ber().rating() : "none";
    }

    private boolean passedBerFilter(ListingResult listing, List<BerRating> berFilter) {
        if (listing.ber() == null) return false;
        return BerRating.fromCode(listing.ber().rating())
                .map(berFilter::contains)
                .orElse(false);
    }

    private void warnUnknownBerRatings(String tenantId, List<ListingResult> listings) {
        listings.stream()
                .filter(l -> l.ber() != null)
                .map(l -> l.ber().rating())
                .filter(code -> BerRating.fromCode(code).isEmpty())
                .distinct()
                .forEach(code -> log.warn("tenant={} unknown BER rating code={}", tenantId, code));
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
        metricsPublisher.recordPollError(tenantId, status.name());
        switch (status) {
            case BLOCKED -> {
                backoff.recordBlock();
                metricsPublisher.recordBlockDetected(tenantId);
                if (alertThrottle.tryFire(tenantId + ":block_detected")) {
                    log.warn("tenant={} BLOCK ALERT FIRED backoff-level={} until={}",
                            tenantId, backoff.level(), backoff.blockedUntil());
                }
                log.warn("tenant={} BLOCKED backoff-level={} until={}", tenantId, backoff.level(), backoff.blockedUntil());
            }
            case RATE_LIMITED -> {
                backoff.recordRateLimit();
                log.warn("tenant={} RATE_LIMITED until={}", tenantId, backoff.blockedUntil());
            }
            default -> log.error("tenant={} poll error: {}", tenantId, e.getMessage(), e);
        }
        metricsPublisher.recordPollDuration(tenantId, System.currentTimeMillis() - start);
        log.info("tenant={} found=0 new=0 skipped=0 elapsed={}ms", tenantId, System.currentTimeMillis() - start);
    }
}
