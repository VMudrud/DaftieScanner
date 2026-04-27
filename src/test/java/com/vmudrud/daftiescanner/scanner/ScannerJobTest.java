package com.vmudrud.daftiescanner.scanner;

import com.vmudrud.daftiescanner.client.BlockDetector;
import com.vmudrud.daftiescanner.client.BlockStatus;
import com.vmudrud.daftiescanner.client.DaftClient;
import com.vmudrud.daftiescanner.client.dto.ListingResult;
import com.vmudrud.daftiescanner.client.dto.SearchResult;
import com.vmudrud.daftiescanner.config.dto.FilterSpec;
import com.vmudrud.daftiescanner.config.dto.Tenant;
import com.vmudrud.daftiescanner.notifier.Notifier;
import com.vmudrud.daftiescanner.store.dto.Cursor;
import com.vmudrud.daftiescanner.store.CursorStore;
import com.vmudrud.daftiescanner.store.DedupStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScannerJobTest {

    private static final String TENANT_ID = "1";
    private static final long CURSOR_DATE = 1_700_000_000_000L;
    private static final long OLD_DATE    = 1_699_000_000_000L; // before cursor
    private static final long NEW_DATE    = 1_701_000_000_000L; // after cursor

    @Mock DaftClient daftClient;
    @Mock CursorStore cursorStore;
    @Mock DedupStore dedupStore;
    @Mock BlockDetector blockDetector;
    @Mock Notifier notifier;

    private TenantBackoff backoff;
    private EmailNotificationGuard emailGuard;
    private ScannerJob job;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        backoff = new TenantBackoff();
        emailGuard = new EmailNotificationGuard(dedupStore, notifier);
        tenant = new Tenant(TENANT_ID, true, "test@example.com",
                new FilterSpec("residential-to-rent",
                        new FilterSpec.Range(1200, 2300),
                        new FilterSpec.Range(1, 3),
                        List.of("42")));
        job = new ScannerJob(tenant, daftClient, cursorStore, dedupStore, blockDetector, emailGuard, backoff);
    }

    @Test
    void poll_coldStart_marksAllSeenSavesCursorDoesNotNotify() {
        when(cursorStore.load(TENANT_ID)).thenReturn(Optional.empty());
        when(daftClient.search(any())).thenReturn(searchResult(
                listing(1001L, NEW_DATE), listing(1002L, OLD_DATE)));

        job.poll();

        verify(dedupStore).markSeen(eq(TENANT_ID), eq(1001L), any());
        verify(dedupStore).markSeen(eq(TENANT_ID), eq(1002L), any());
        verify(notifier, never()).notify(any(), any());
        verify(cursorStore).save(TENANT_ID, NEW_DATE, 1001L); // max publishDate, first listing id
    }

    @Test
    void poll_coldStart_emptyResults_savesTimestampCursorDoesNotNotify() {
        when(cursorStore.load(TENANT_ID)).thenReturn(Optional.empty());
        when(daftClient.search(any())).thenReturn(searchResult());

        job.poll();

        verify(notifier, never()).notify(any(), any());
        verify(cursorStore).save(eq(TENANT_ID), longThat(t -> t > 0), eq(0L));
    }

    @Test
    void poll_normalPoll_newListing_notifiesAndAdvancesCursor() {
        var cursor = new Cursor(CURSOR_DATE, 999L, System.currentTimeMillis());
        when(cursorStore.load(TENANT_ID)).thenReturn(Optional.of(cursor));
        when(daftClient.search(any())).thenReturn(searchResult(listing(1001L, NEW_DATE)));
        when(dedupStore.seen(TENANT_ID, 1001L)).thenReturn(false);

        job.poll();

        verify(notifier).notify(eq(tenant), any());
        verify(cursorStore).save(TENANT_ID, NEW_DATE, 1001L);
    }

    @Test
    void poll_normalPoll_allAlreadySeen_oldDate_noNotifyNoCursorAdvance() {
        // Listing is seen AND older than cursor → no notify, cursor must not regress
        var cursor = new Cursor(CURSOR_DATE, 999L, System.currentTimeMillis());
        when(cursorStore.load(TENANT_ID)).thenReturn(Optional.of(cursor));
        when(daftClient.search(any())).thenReturn(searchResult(listing(1001L, OLD_DATE)));
        when(dedupStore.seen(TENANT_ID, 1001L)).thenReturn(true);

        job.poll();

        verify(notifier, never()).notify(any(), any());
        verify(cursorStore, never()).save(any(), anyLong(), anyLong());
    }

    @Test
    void poll_normalPoll_publishDateAtCursorBoundary_notNotified() {
        var cursor = new Cursor(CURSOR_DATE, 999L, System.currentTimeMillis());
        when(cursorStore.load(TENANT_ID)).thenReturn(Optional.of(cursor));
        when(daftClient.search(any())).thenReturn(searchResult(listing(1001L, CURSOR_DATE)));
        when(dedupStore.seen(TENANT_ID, 1001L)).thenReturn(false);

        job.poll();

        verify(notifier, never()).notify(any(), any()); // strictly > required
    }

    @Test
    void poll_newListing_marksEmailAsNotified() {
        var cursor = new Cursor(CURSOR_DATE, 999L, System.currentTimeMillis());
        when(cursorStore.load(TENANT_ID)).thenReturn(Optional.of(cursor));
        when(daftClient.search(any())).thenReturn(searchResult(listing(1001L, NEW_DATE)));
        when(dedupStore.seen(TENANT_ID, 1001L)).thenReturn(false);

        job.poll();

        verify(dedupStore).markNotifiedByEmail(tenant.email(), 1001L);
    }

    @Test
    void poll_emailAlreadyNotified_skipsNotificationButStillMarksSeen() {
        var cursor = new Cursor(CURSOR_DATE, 999L, System.currentTimeMillis());
        when(cursorStore.load(TENANT_ID)).thenReturn(Optional.of(cursor));
        when(daftClient.search(any())).thenReturn(searchResult(listing(1001L, NEW_DATE)));
        when(dedupStore.seen(TENANT_ID, 1001L)).thenReturn(false);
        when(dedupStore.notifiedByEmail(tenant.email(), 1001L)).thenReturn(true);

        job.poll();

        verify(dedupStore).markSeen(eq(TENANT_ID), eq(1001L), any()); // per-tenant dedup still tracked
        verify(notifier, never()).notify(any(), any());
        verify(dedupStore, never()).markNotifiedByEmail(any(), anyLong());
    }

    @Test
    void poll_blocked_recordsBackoffDoesNotPropagate() {
        when(cursorStore.load(TENANT_ID)).thenReturn(Optional.of(new Cursor(CURSOR_DATE, 0L, 0L)));
        when(daftClient.search(any())).thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));
        when(blockDetector.classify(any())).thenReturn(BlockStatus.BLOCKED);

        job.poll(); // must not throw

        assertThat(backoff.isBlocked()).isTrue();
        assertThat(backoff.level()).isEqualTo(1);
        verify(notifier, never()).notify(any(), any());
    }

    @Test
    void poll_rateLimited_recordsRateLimitNoLevelEscalation() {
        when(cursorStore.load(TENANT_ID)).thenReturn(Optional.of(new Cursor(CURSOR_DATE, 0L, 0L)));
        when(daftClient.search(any())).thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS));
        when(blockDetector.classify(any())).thenReturn(BlockStatus.RATE_LIMITED);

        job.poll();

        assertThat(backoff.isBlocked()).isTrue();
        assertThat(backoff.level()).isEqualTo(0); // rate-limit does not escalate level
    }

    @Test
    void poll_unknownException_logsErrorDoesNotThrowNoBackoff() {
        when(cursorStore.load(TENANT_ID)).thenReturn(Optional.of(new Cursor(CURSOR_DATE, 0L, 0L)));
        when(daftClient.search(any())).thenThrow(new RuntimeException("network timeout"));
        when(blockDetector.classify(any())).thenReturn(BlockStatus.UNKNOWN);

        job.poll(); // must not throw

        assertThat(backoff.level()).isEqualTo(0);
        assertThat(backoff.isBlocked()).isFalse();
    }

    @Test
    void poll_successAfterBlock_resetsBackoffLevel() {
        when(cursorStore.load(TENANT_ID)).thenReturn(Optional.of(new Cursor(CURSOR_DATE, 0L, 0L)));
        when(daftClient.search(any()))
                .thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN))
                .thenReturn(searchResult());
        when(blockDetector.classify(any())).thenReturn(BlockStatus.BLOCKED);

        job.poll(); // blocked → level becomes 1
        assertThat(backoff.level()).isEqualTo(1);

        job.poll(); // success → reset
        assertThat(backoff.level()).isEqualTo(0);
        assertThat(backoff.isBlocked()).isFalse();
    }

    @Test
    void poll_emptyResults_normalPoll_doesNotAdvanceCursor() {
        var cursor = new Cursor(CURSOR_DATE, 999L, System.currentTimeMillis());
        when(cursorStore.load(TENANT_ID)).thenReturn(Optional.of(cursor));
        when(daftClient.search(any())).thenReturn(searchResult());

        job.poll();

        verify(cursorStore, never()).save(any(), anyLong(), anyLong());
        verify(notifier, never()).notify(any(), any());
    }

    @Test
    void emailGuard_concurrentNotify_onlyOneNotificationSent() throws InterruptedException {
        // Two tenants with the same email competing to notify the same listing simultaneously.
        // EmailNotificationGuard must ensure only one notification fires.
        String sharedEmail = "shared@example.com";
        var cursor = new Cursor(CURSOR_DATE, 999L, System.currentTimeMillis());
        var tenantA = new Tenant("A", true, sharedEmail,
                new FilterSpec("residential-to-rent", new FilterSpec.Range(1200, 2300),
                        new FilterSpec.Range(1, 3), List.of("42")));
        var tenantB = new Tenant("B", true, sharedEmail,
                new FilterSpec("residential-to-rent", new FilterSpec.Range(1200, 2300),
                        new FilterSpec.Range(1, 3), List.of("42")));

        // State-aware mock: notifiedByEmail reflects what has been marked
        var emailMarked = new AtomicInteger(0);
        AtomicInteger notifyCount = new AtomicInteger();
        when(dedupStore.seen(eq("A"), eq(1001L))).thenReturn(false);
        when(dedupStore.seen(eq("B"), eq(1001L))).thenReturn(false);
        when(dedupStore.notifiedByEmail(eq(sharedEmail), eq(1001L)))
                .thenAnswer(inv -> emailMarked.get() > 0);
        doAnswer(inv -> { emailMarked.incrementAndGet(); return null; })
                .when(dedupStore).markNotifiedByEmail(eq(sharedEmail), eq(1001L));
        doAnswer(inv -> { notifyCount.incrementAndGet(); return null; }).when(notifier).notify(any(), any());

        when(cursorStore.load("A")).thenReturn(Optional.of(cursor));
        when(cursorStore.load("B")).thenReturn(Optional.of(cursor));
        when(daftClient.search(any())).thenReturn(searchResult(listing(1001L, NEW_DATE)));

        var jobA = new ScannerJob(tenantA, daftClient, cursorStore, dedupStore, blockDetector, emailGuard, new TenantBackoff());
        var jobB = new ScannerJob(tenantB, daftClient, cursorStore, dedupStore, blockDetector, emailGuard, new TenantBackoff());

        var latch = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(() -> { try { latch.await(); } catch (InterruptedException ignored) {} jobA.poll(); });
        pool.submit(() -> { try { latch.await(); } catch (InterruptedException ignored) {} jobB.poll(); });
        latch.countDown();
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(notifyCount.get()).isEqualTo(1);
    }

    // --- helpers ---

    private ListingResult listing(long id, long publishDate) {
        return new ListingResult(id, "Test Apt", publishDate, "€2,000 per month",
                null, null, null, "/listing/" + id, null, null, null, null, null, null, null);
    }

    private SearchResult searchResult(ListingResult... listings) {
        var wrappers = List.of(listings).stream()
                .map(SearchResult.ListingWrapper::new)
                .toList();
        return new SearchResult(wrappers, new SearchResult.PagingResult(1, 0, wrappers.size()));
    }
}
