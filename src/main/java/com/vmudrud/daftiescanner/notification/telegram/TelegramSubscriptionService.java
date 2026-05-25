package com.vmudrud.daftiescanner.notification.telegram;

import com.vmudrud.daftiescanner.common.tenant.Tenant;
import com.vmudrud.daftiescanner.notification.telegram.store.SubscriptionConflictException;
import com.vmudrud.daftiescanner.notification.telegram.store.SubscriptionStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Service
@ConditionalOnTelegramEnabled
public class TelegramSubscriptionService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final List<Tenant> tenants;
    private final SubscriptionStore store;

    public TelegramSubscriptionService(@Qualifier("tenants") List<Tenant> tenants, SubscriptionStore store) {
        this.tenants = tenants;
        this.store = store;
    }

    public Optional<String> currentEmail(String chatId) {
        return store.emailByChatId(chatId);
    }

    public void subscribe(String chatId, String email) {
        var normalized = validateAndNormalize(email);
        store.claim(chatId, normalized);
        log.info("Telegram subscription created chatId={} email={}", chatId, normalized);
    }

    public void changeEmail(String chatId, String newEmail) {
        var normalized = validateAndNormalize(newEmail);
        var oldEmail = store.emailByChatId(chatId)
                .orElseThrow(() -> new SubscriptionConflictException(SubscriptionConflictException.Reason.CHAT_HAS_NO_SUBSCRIPTION));
        if (oldEmail.equals(normalized)) {
            return;
        }
        store.change(chatId, oldEmail, normalized);
        log.info("Telegram subscription changed chatId={} oldEmail={} newEmail={}", chatId, oldEmail, normalized);
    }

    public void unsubscribe(String chatId) {
        var email = store.emailByChatId(chatId)
                .orElseThrow(() -> new SubscriptionConflictException(SubscriptionConflictException.Reason.CHAT_HAS_NO_SUBSCRIPTION));
        store.release(chatId, email);
        log.info("Telegram subscription released chatId={} email={}", chatId, email);
    }

    private String validateAndNormalize(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new SubscriptionConflictException(SubscriptionConflictException.Reason.EMAIL_INVALID_FORMAT);
        }
        var normalized = email.trim().toLowerCase();
        boolean known = tenants.stream().anyMatch(t -> normalized.equalsIgnoreCase(t.email()));
        if (!known) {
            throw new SubscriptionConflictException(SubscriptionConflictException.Reason.EMAIL_NOT_REGISTERED);
        }
        return normalized;
    }
}
