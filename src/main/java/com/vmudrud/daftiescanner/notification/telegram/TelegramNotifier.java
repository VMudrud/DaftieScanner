package com.vmudrud.daftiescanner.notification.telegram;

import com.vmudrud.daftiescanner.common.listing.ListingResult;
import com.vmudrud.daftiescanner.common.store.DedupStore;
import com.vmudrud.daftiescanner.common.tenant.Tenant;
import com.vmudrud.daftiescanner.notification.router.Notifier;
import com.vmudrud.daftiescanner.notification.telegram.store.SubscriptionStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnTelegramEnabled
public class TelegramNotifier implements Notifier {

    public static final String CHANNEL = "telegram";
    private static final String DAFT_BASE = "https://www.daft.ie";
    private static final String DETAIL_SEPARATOR = " — ";

    private final TelegramBotClient bot;
    private final SubscriptionStore subscriptionStore;
    private final DedupStore dedupStore;
    private final ConcurrentHashMap<String, ReentrantLock> chatLocks = new ConcurrentHashMap<>();

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public void notify(Tenant tenant, List<ListingResult> listings) {
        if (listings.isEmpty()) {
            return;
        }
        var chatIdOpt = subscriptionStore.chatIdByEmail(tenant.email());
        if (chatIdOpt.isEmpty()) {
            log.debug("Telegram skip tenant={}: no subscription for email={}", tenant.id(), tenant.email());
            return;
        }
        String chatId = chatIdOpt.get();
        var lock = chatLocks.computeIfAbsent(chatId, k -> new ReentrantLock());
        lock.lock();
        try {
            var unsent = listings.stream()
                    .filter(l -> !dedupStore.notifiedBy(CHANNEL, chatId, l.id()))
                    .toList();
            if (unsent.isEmpty()) {
                return;
            }
            sendMarkdown(chatId, tenant, unsent);
        } finally {
            lock.unlock();
        }
    }

    private void sendMarkdown(String chatId, Tenant tenant, List<ListingResult> listings) {
        try {
            bot.sendMarkdown(chatId, buildMarkdownBody(listings));
            listings.forEach(l -> dedupStore.markNotifiedBy(CHANNEL, chatId, l.id()));
            log.info("Telegram sent tenant={} chatId={} count={}", tenant.id(), chatId, listings.size());
        } catch (TelegramApiException e) {
            if (e.isForbidden()) {
                log.warn("Telegram bot blocked by chatId={}; releasing claim for email={}", chatId, tenant.email());
                try {
                    subscriptionStore.release(chatId, tenant.email());
                } catch (Exception releaseError) {
                    log.warn("Failed to release blocked subscription chatId={}: {}", chatId, releaseError.getMessage());
                }
                return;
            }
            log.error("Telegram send failed tenant={} chatId={} status={}: {}",
                    tenant.id(), chatId, e.statusCode(), e.getMessage());
        }
    }

    private String buildMarkdownBody(List<ListingResult> listings) {
        var sb = new StringBuilder();
        sb.append(TelegramReplyFormatter.escapeMarkdownV2(
                "%d new rental listing(s):".formatted(listings.size()))).append("\n\n");
        listings.forEach(l -> {
            String url = DAFT_BASE + l.seoFriendlyPath();
            sb.append("• [")
                    .append(TelegramReplyFormatter.escapeMarkdownV2(l.title()))
                    .append("](")
                    .append(TelegramReplyFormatter.escapeMarkdownV2(url))
                    .append(") — ")
                    .append(TelegramReplyFormatter.escapeMarkdownV2(l.price()))
                    .append(TelegramReplyFormatter.escapeMarkdownV2(extras(l)))
                    .append("\n");
        });
        return sb.toString();
    }

    private static String extras(ListingResult l) {
        var sb = new StringBuilder();
        if (l.numBedrooms() != null && !l.numBedrooms().isBlank()) {
            sb.append(DETAIL_SEPARATOR).append(l.numBedrooms());
        }
        String ber = berRating(l);
        if (ber != null) {
            sb.append(DETAIL_SEPARATOR).append("BER ").append(ber);
        }
        return sb.toString();
    }

    private static String berRating(ListingResult l) {
        var ber = l.ber();
        if (ber == null) {
            return null;
        }
        String rating = ber.rating();
        return (rating == null || rating.isBlank()) ? null : rating;
    }
}
