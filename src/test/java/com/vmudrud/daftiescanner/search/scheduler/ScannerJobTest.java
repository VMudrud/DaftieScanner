package com.vmudrud.daftiescanner.search.scheduler;

import com.vmudrud.daftiescanner.common.event.NewListingsFoundEvent;
import com.vmudrud.daftiescanner.search.client.BlockDetector;
import com.vmudrud.daftiescanner.search.client.BlockStatus;
import com.vmudrud.daftiescanner.search.client.DaftClient;
import com.vmudrud.daftiescanner.common.listing.ListingResult;
import com.vmudrud.daftiescanner.common.listing.SearchResult;
import com.vmudrud.daftiescanner.common.tenant.FilterSpec;
import com.vmudrud.daftiescanner.common.tenant.Tenant;
import com.vmudrud.daftiescanner.common.store.AlertThrottle;
import com.vmudrud.daftiescanner.common.store.dto.Cursor;
import com.vmudrud.daftiescanner.common.store.CursorStore;
import com.vmudrud.daftiescanner.common.store.DedupStore;
import com.vmudrud.daftiescanner.search.metrics.MetricsPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScannerJobTest {

    private static final String TENANT_ID = "1";
    private static final long CURSOR_DATE = 1_700_000_000_000L;
    private static final long OLD_DATE    = 1_699_000_000_000L;
    private static final long NEW_DATE    = 1_701_000_000_000L;

    @Mock DaftClient daftClient;
    @Mock CursorStore cursorStore;
    @Mock DedupStore dedupStore;
    @Mock BlockDetector blockDetector;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock MetricsPublisher metricsPublisher;
    @Mock AlertThrottle alertThrottle;

    private TenantBackoff backoff;
    private ScannerJob job;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        backoff = new TenantBackoff();
        tenant = new Tenant(TENANT_ID, true, "test@example.com",
                new FilterSpec("residential-to-rent",
                        new FilterSpec.Range(1200, 2300),
                        new FilterSpec.Range(1, 3),
                        List.of("42")),
                List.of("email"));
        job = new ScannerJob(tenant, daftClient, cursorStore, dedupStore, blockDetector,
                eventPublisher, backoff, metricsPublisher, alertThrottle, new AtomicBoolean(false));
    }

    @Test
    void poll_coldStart_marksAllSeenSavesCursorDoesNotPublishEvent() {
        when(cursorStore.load(TENANT_ID)).thenReturn(Optional.empty());
        when(daftClient.search(any())).thenReturn(searchResult(
                listing(1001L, NEW_DATE), listing(1002L, OLD_DATE)));

        job.poll();

        verify(dedupStore).markSeen(eq(TENANT_ID), eq(1001L), any());
        verify(dedupStore).markSeen(eq(TENANT_ID), eq(1002L), any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(cursorStore).save(TENANT_ID, NEW_DATE, 1001L);
    }

    @Test
    void poll_coldStart_emptyResults_savesTimestampCursorDoesNotPublishEvent() {
        when(cursorStore.load(TENANT_ID)).thenReturn(Optional.empty());
        when(daftClient.search(any())).thenReturn(searchResult());

        job.poll();

        verify(eventPublisher, never()).publishEvent(any());
        verify(cursorStore).save(eq(TENANT_ID), longThat(t -> t > 0), eq(0L));
    }

    @Test
    void poll_normalPoll_newListing_publishesEventAndAdvancesCursor() {
        var cursor = new Cursor(CURSOR_DATE, 999L, System.currentTimeMillis());
        when(cursorStore.load(TENANT_ID)).thenReturn(Optional.of(cursor));
        when(daftClient.search(any())).thenReturn(searchResult(listing(1001L, NEW_DATE)));
        when(dedupStore.seen(TENANT_ID, 1001L)).thenReturn(false);

        job.poll();

        var captor = ArgumentCaptor.forClass(NewListingsFoundEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().tenant()).isEqualTo(tenant);
        assertThat(captor.getValue().listings()).hasSize(1);
        assertThat(captor.getValue().listings().get(0).id()).isEqualTo(1001L);
        verify(cursorStore).save(TENANT_ID, NEW_DATE, 1001L);
    }

    @Test
    void poll_normalPoll_multipleNewListings_publishesSingleEventWithAllListings() {
        var cursor = new Cursor(CURSOR_DATE, 999L, System.currentTimeMillis());
        when(cursorStore.load(TENANT_ID)).thenReturn(Optional.of(cursor));
        when(daftClient.search(any())).thenReturn(searchResult(
                listing(1001L, NEW_DATE), listing(1002L, NEW_DATE + 1000)));
        when(dedupStore.seen(TENANT_ID, 1001L)).thenReturn(false);
        when(dedupStore.seen(TENANT_ID, 1002L)).thenReturn(false);

        job.poll();

        var captor = ArgumentCaptor.forClass(NewListingsFoundEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        assertThat(captor.getValue().listings()).hasSize(2);
    }

    @Test
    void poll_normalPoll_allAlreadySeen_noEventPublished() {
        var cursor = new Cursor(CURSOR_DATE, 999L, System.currentTimeMillis());
        when(cursorStore.load(TENANT_ID)).thenReturn(Optional.of(cursor));
        when(daftClient.search(any())).thenReturn(searchResult(listing(1001L, OLD_DATE)));
        when(dedupStore.seen(TENANT_ID, 1001L)).thenReturn(true);

        job.poll();

        verify(eventPublisher, never()).publishEvent(any());
        verify(cursorStore, never()).save(any(), anyLong(), anyLong());
    }

    @Test
    void poll_normalPoll_publishDateAtCursorBoundary_noEventPublished() {
        var cursor = new Cursor(CURSOR_DATE, 999L, System.currentTimeMillis());
        when(cursorStore.load(TENANT_ID)).thenReturn(Optional.of(cursor));
        when(daftClient.search(any())).thenReturn(searchResult(listing(1001L, CURSOR_DATE)));
        when(dedupStore.seen(TENANT_ID, 1001L)).thenReturn(false);

        job.poll();

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void poll_normalPoll_emptyResults_noEventPublished() {
        var cursor = new Cursor(CURSOR_DATE, 999L, System.currentTimeMillis());
        when(cursorStore.load(TENANT_ID)).thenReturn(Optional.of(cursor));
        when(daftClient.search(any())).thenReturn(searchResult());

        job.poll();

        verify(cursorStore, never()).save(any(), anyLong(), anyLong());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void poll_blocked_recordsBackoffDoesNotPropagate() {
        when(cursorStore.load(TENANT_ID)).thenReturn(Optional.of(new Cursor(CURSOR_DATE, 0L, 0L)));
        when(daftClient.search(any())).thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));
        when(blockDetector.classify(any())).thenReturn(BlockStatus.BLOCKED);
        when(alertThrottle.tryFire(anyString())).thenReturn(true);

        job.poll();

        assertThat(backoff.isBlocked()).isTrue();
        assertThat(backoff.level()).isEqualTo(1);
        verify(eventPublisher, never()).publishEvent(any());
        verify(metricsPublisher).recordBlockDetected(TENANT_ID);
        verify(alertThrottle).tryFire(TENANT_ID + ":block_detected");
    }

    @Test
    void poll_blocked_throttled_doesNotFireAlert() {
        when(cursorStore.load(TENANT_ID)).thenReturn(Optional.of(new Cursor(CURSOR_DATE, 0L, 0L)));
        when(daftClient.search(any())).thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));
        when(blockDetector.classify(any())).thenReturn(BlockStatus.BLOCKED);
        when(alertThrottle.tryFire(anyString())).thenReturn(false);

        job.poll();

        verify(metricsPublisher).recordBlockDetected(TENANT_ID);
        verify(alertThrottle).tryFire(TENANT_ID + ":block_detected");
    }

    @Test
    void poll_rateLimited_recordsRateLimitNoLevelEscalation() {
        when(cursorStore.load(TENANT_ID)).thenReturn(Optional.of(new Cursor(CURSOR_DATE, 0L, 0L)));
        when(daftClient.search(any())).thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS));
        when(blockDetector.classify(any())).thenReturn(BlockStatus.RATE_LIMITED);

        job.poll();

        assertThat(backoff.isBlocked()).isTrue();
        assertThat(backoff.level()).isEqualTo(0);
        verify(metricsPublisher).recordPollError(TENANT_ID, BlockStatus.RATE_LIMITED.name());
        verify(metricsPublisher, never()).recordBlockDetected(any());
    }

    @Test
    void poll_unknownException_logsErrorDoesNotThrowNoBackoff() {
        when(cursorStore.load(TENANT_ID)).thenReturn(Optional.of(new Cursor(CURSOR_DATE, 0L, 0L)));
        when(daftClient.search(any())).thenThrow(new RuntimeException("network timeout"));
        when(blockDetector.classify(any())).thenReturn(BlockStatus.UNKNOWN);

        job.poll();

        assertThat(backoff.level()).isEqualTo(0);
        assertThat(backoff.isBlocked()).isFalse();
        verify(metricsPublisher).recordPollError(TENANT_ID, BlockStatus.UNKNOWN.name());
    }

    @Test
    void poll_successAfterBlock_resetsBackoffLevel() {
        when(cursorStore.load(TENANT_ID)).thenReturn(Optional.of(new Cursor(CURSOR_DATE, 0L, 0L)));
        when(daftClient.search(any()))
                .thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN))
                .thenReturn(searchResult());
        when(blockDetector.classify(any())).thenReturn(BlockStatus.BLOCKED);
        when(alertThrottle.tryFire(anyString())).thenReturn(true);

        job.poll();
        assertThat(backoff.level()).isEqualTo(1);

        job.poll();
        assertThat(backoff.level()).isEqualTo(0);
        assertThat(backoff.isBlocked()).isFalse();
    }

    @Test
    void poll_recordsListingsFound_andDuration() {
        var cursor = new Cursor(CURSOR_DATE, 999L, System.currentTimeMillis());
        when(cursorStore.load(TENANT_ID)).thenReturn(Optional.of(cursor));
        when(daftClient.search(any())).thenReturn(searchResult(listing(1001L, OLD_DATE)));
        when(dedupStore.seen(TENANT_ID, 1001L)).thenReturn(true);

        job.poll();

        verify(metricsPublisher).recordListingsFound(TENANT_ID, 1);
        verify(metricsPublisher).recordPollDuration(eq(TENANT_ID), longThat(d -> d >= 0));
    }

    @Test
    void poll_forceColdStart_cursorPresent_firstPollColdStartsNoNotification() {
        var forcedJob = new ScannerJob(tenant, daftClient, cursorStore, dedupStore, blockDetector,
                eventPublisher, backoff, metricsPublisher, alertThrottle, new AtomicBoolean(true));
        var cursor = new Cursor(CURSOR_DATE, 999L, System.currentTimeMillis());
        when(cursorStore.load(TENANT_ID)).thenReturn(Optional.of(cursor));
        when(daftClient.search(any())).thenReturn(searchResult(listing(1001L, NEW_DATE)));

        forcedJob.poll();

        verify(dedupStore).markSeen(eq(TENANT_ID), eq(1001L), any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(cursorStore).save(TENANT_ID, NEW_DATE, 1001L);
    }

    @Test
    void poll_forceColdStart_secondPollResumesNormal() {
        var forcedJob = new ScannerJob(tenant, daftClient, cursorStore, dedupStore, blockDetector,
                eventPublisher, backoff, metricsPublisher, alertThrottle, new AtomicBoolean(true));
        var cursor = new Cursor(CURSOR_DATE, 999L, System.currentTimeMillis());
        when(cursorStore.load(TENANT_ID)).thenReturn(Optional.of(cursor));
        when(daftClient.search(any())).thenReturn(searchResult(listing(1001L, NEW_DATE)));
        when(dedupStore.seen(TENANT_ID, 1001L)).thenReturn(false);

        forcedJob.poll(); // cold start — clears the flag
        forcedJob.poll(); // normal poll

        var captor = ArgumentCaptor.forClass(NewListingsFoundEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().listings()).hasSize(1);
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
