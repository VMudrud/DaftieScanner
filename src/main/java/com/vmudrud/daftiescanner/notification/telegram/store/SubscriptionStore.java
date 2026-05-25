package com.vmudrud.daftiescanner.notification.telegram.store;

import java.util.Optional;

public interface SubscriptionStore {

    Optional<String> chatIdByEmail(String email);

    Optional<String> emailByChatId(String chatId);

    void claim(String chatId, String email);

    void change(String chatId, String oldEmail, String newEmail);

    void release(String chatId, String email);
}
