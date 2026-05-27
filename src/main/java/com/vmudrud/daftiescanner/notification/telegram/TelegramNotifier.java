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
            sendEach(chatId, tenant, unsent);
        } finally {
            lock.unlock();
        }
    }

    private void sendEach(String chatId, Tenant tenant, List<ListingResult> listings) {
        for (var listing : listings) {
            try {
                bot.sendMarkdown(chatId, TelegramListingFormatter.format(listing));
                dedupStore.markNotifiedBy(CHANNEL, chatId, listing.id());
                log.info("Telegram sent tenant={} chatId={} listingId={}", tenant.id(), chatId, listing.id());
            } catch (TelegramApiException e) {
                if (e.isForbidden()) {
                    releaseBlockedSubscription(chatId, tenant);
                    return;
                }
                log.error("Telegram send failed tenant={} chatId={} listingId={} status={}: {}",
                        tenant.id(), chatId, listing.id(), e.statusCode(), e.getMessage());
            }
        }
    }

    private void releaseBlockedSubscription(String chatId, Tenant tenant) {
        log.warn("Telegram bot blocked by chatId={}; releasing claim for email={}", chatId, tenant.email());
        try {
            subscriptionStore.release(chatId, tenant.email());
        } catch (Exception releaseError) {
            log.warn("Failed to release blocked subscription chatId={}: {}", chatId, releaseError.getMessage());
        }
    }
}
