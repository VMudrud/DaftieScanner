package com.vmudrud.daftiescanner.notification.telegram;

import com.vmudrud.daftiescanner.common.listing.ListingResult;
import com.vmudrud.daftiescanner.common.store.DedupStore;
import com.vmudrud.daftiescanner.common.tenant.FilterSpec;
import com.vmudrud.daftiescanner.common.tenant.Tenant;
import com.vmudrud.daftiescanner.notification.telegram.store.SubscriptionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramNotifierTest {

    private static final String EMAIL = "test@example.com";
    private static final String CHAT_ID = "9001";

    @Mock
    TelegramBotClient bot;
    @Mock
    SubscriptionStore subscriptionStore;
    @Mock
    DedupStore dedupStore;

    private TelegramNotifier notifier;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        notifier = new TelegramNotifier(bot, subscriptionStore, dedupStore);
        tenant = new Tenant("1", true, EMAIL,
                new FilterSpec("residential-to-rent",
                        new FilterSpec.Range(1, 9999),
                        new FilterSpec.Range(1, 5),
                        List.of("42"), List.of()),
                List.of("telegram"));
    }

    @Test
    void channel_returnsTelegram() {
        assertThat(notifier.channel()).isEqualTo("telegram");
    }

    @Test
    void notify_emptyList_doesNothing() {
        notifier.notify(tenant, List.of());

        verifyNoInteractions(bot, subscriptionStore, dedupStore);
    }

    @Test
    void notify_noSubscription_skipsSilently() {
        when(subscriptionStore.chatIdByEmail(EMAIL)).thenReturn(Optional.empty());

        notifier.notify(tenant, List.of(listing(1L, "Apt", "/listing/1")));

        verifyNoInteractions(bot);
        verify(dedupStore, never()).markNotifiedBy(any(), any(), anyLong());
    }

    @Test
    void notify_happyPath_sendsOneMessagePerListingAndMarksDedup() {
        when(subscriptionStore.chatIdByEmail(EMAIL)).thenReturn(Optional.of(CHAT_ID));
        var listings = List.of(
                listing(1001L, "Bright Studio", "/listing/1001", "1 Bed", "B2", "Apartment"),
                listing(1002L, "Cosy 1-Bed", "/listing/1002", "2 Bed", "A3", "House"));

        notifier.notify(tenant, listings);

        var bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(bot, times(2)).sendMarkdown(eq(CHAT_ID), bodyCaptor.capture());
        var bodies = bodyCaptor.getAllValues();

        String first = bodies.get(0);
        assertThat(first).contains("Bright Studio");
        assertThat(first).contains("/listing/1001");
        assertThat(first).contains("```\n");
        assertThat(first).contains("💶 Price");
        assertThat(first).contains("│ €2,000/month");
        assertThat(first).contains("🏠 Type");
        assertThat(first).contains("│ Apartment");
        assertThat(first).contains("🛏 Beds");
        assertThat(first).contains("│ 1\n");
        assertThat(first).contains("BER");
        assertThat(first).contains("│ B2\n");
        assertThat(first).doesNotContain("Cosy");
        // Type row sits between Price and Beds.
        assertThat(first.indexOf("Price")).isLessThan(first.indexOf("Type"));
        assertThat(first.indexOf("Type")).isLessThan(first.indexOf("Beds"));

        String second = bodies.get(1);
        assertThat(second).contains("Cosy 1\\-Bed");
        assertThat(second).contains("/listing/1002");
        assertThat(second).contains("│ House");
        assertThat(second).contains("│ 2\n");
        assertThat(second).contains("│ A3\n");

        verify(dedupStore).markNotifiedBy("telegram", CHAT_ID, 1001L);
        verify(dedupStore).markNotifiedBy("telegram", CHAT_ID, 1002L);
    }

    @Test
    void notify_missingBedsAndBer_omitsDetailsGracefully() {
        when(subscriptionStore.chatIdByEmail(EMAIL)).thenReturn(Optional.of(CHAT_ID));

        notifier.notify(tenant, List.of(listing(1L, "No Details", "/listing/1", null, null)));

        var bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(bot).sendMarkdown(eq(CHAT_ID), bodyCaptor.capture());
        String body = bodyCaptor.getValue();
        assertThat(body).contains("No Details");
        assertThat(body).doesNotContain("BER");
        assertThat(body).doesNotContain("Beds");
        assertThat(body).doesNotContain("Type");
        assertThat(body).contains("💶 Price");
        assertThat(body).contains("│ €2,000/month");
    }

    @Test
    void notify_allDeduped_doesNotSend() {
        when(subscriptionStore.chatIdByEmail(EMAIL)).thenReturn(Optional.of(CHAT_ID));
        when(dedupStore.notifiedBy("telegram", CHAT_ID, 1001L)).thenReturn(true);

        notifier.notify(tenant, List.of(listing(1001L, "Already", "/listing/1001")));

        verify(bot, never()).sendMarkdown(any(), any());
        verify(dedupStore, never()).markNotifiedBy(any(), any(), anyLong());
    }

    @Test
    void notify_botBlocked403_releasesClaimAndDoesNotMark() {
        when(subscriptionStore.chatIdByEmail(EMAIL)).thenReturn(Optional.of(CHAT_ID));
        doThrow(new TelegramApiException(403, "bot was blocked"))
                .when(bot).sendMarkdown(eq(CHAT_ID), any());

        notifier.notify(tenant, List.of(listing(1L, "Apt", "/listing/1")));

        verify(subscriptionStore).release(CHAT_ID, EMAIL);
        verify(dedupStore, never()).markNotifiedBy(any(), any(), anyLong());
    }

    @Test
    void notify_otherApiError_doesNotMarkOrRelease() {
        when(subscriptionStore.chatIdByEmail(EMAIL)).thenReturn(Optional.of(CHAT_ID));
        doThrow(new TelegramApiException(500, "server error"))
                .when(bot).sendMarkdown(eq(CHAT_ID), any());

        notifier.notify(tenant, List.of(listing(1L, "Apt", "/listing/1")));

        verify(subscriptionStore, never()).release(any(), any());
        verify(dedupStore, never()).markNotifiedBy(any(), any(), anyLong());
    }

    private ListingResult listing(long id, String title, String path) {
        return listing(id, title, path, null, null, null);
    }

    private ListingResult listing(long id, String title, String path, String numBedrooms, String berRating) {
        return listing(id, title, path, numBedrooms, berRating, null);
    }

    private ListingResult listing(long id, String title, String path, String numBedrooms, String berRating, String propertyType) {
        var ber = berRating == null ? null : new ListingResult.BerInfo(berRating);
        return new ListingResult(id, title, 1_700_000_000_000L, "€2,000 per month",
                numBedrooms, null, propertyType, path, null, null, null, ber, null, null, null);
    }
}
