package com.vmudrud.daftiescanner.notification.telegram;

import com.vmudrud.daftiescanner.notification.telegram.dto.TelegramUpdate;
import com.vmudrud.daftiescanner.notification.telegram.store.SubscriptionConflictException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnTelegramEnabled
public class TelegramCommandHandler {

    private static final String CMD_START = "/start";
    private static final String CMD_HELP = "/help";
    private static final String CMD_SUBSCRIBE = "/subscribe";
    private static final String CMD_CHANGE = "/change";
    private static final String CMD_EMAIL = "/email";
    private static final String CMD_UNSUBSCRIBE = "/unsubscribe";

    private static final String HELP_TEMPLATE = """
            DaftieScanner bot

            Your email must be registered by the administrator first.
            
            Admin: %s

            Commands:
            /subscribe <email> — link your registered email to this chat
            /change <email> — switch to a different registered email
            /email — show the currently linked email
            /unsubscribe — stop notifications
            /help — show this message""";

    @Value("${daft.telegram.admin-contact:Unknown}")
    private String adminContact;

    private final TelegramSubscriptionService service;
    private final TelegramBotClient bot;

    public void handle(TelegramUpdate update) {
        var msg = update.message();
        if (msg == null || msg.text() == null) {
            return;
        }
        String chatId = String.valueOf(msg.chat().id());
        String[] parts = msg.text().trim().split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1].trim() : "";

        safeReply(chatId, replyFor(chatId, command, arg));
    }

    private String replyFor(String chatId, String command, String arg) {
        try {
            return switch (command) {
                case CMD_START, CMD_HELP -> HELP_TEMPLATE.formatted(adminContact);
                case CMD_EMAIL -> service.currentEmail(chatId)
                        .map(TelegramReplyFormatter::currentEmail)
                        .orElseGet(TelegramReplyFormatter::notSubscribed);
                case CMD_UNSUBSCRIBE -> {
                    service.unsubscribe(chatId);
                    yield TelegramReplyFormatter.unsubscribed();
                }
                case CMD_SUBSCRIBE -> {
                    service.subscribe(chatId, arg);
                    yield TelegramReplyFormatter.subscribed(arg);
                }
                case CMD_CHANGE -> {
                    service.changeEmail(chatId, arg);
                    yield TelegramReplyFormatter.changed(arg);
                }
                default -> TelegramReplyFormatter.unknownCommand();
            };
        } catch (SubscriptionConflictException e) {
            return TelegramReplyFormatter.forConflict(e.reason());
        } catch (Exception e) {
            log.error("Failed to handle Telegram command chatId={} command='{}' arg='{}': {}",
                    chatId, command, arg, e.getMessage(), e);
            return TelegramReplyFormatter.genericError();
        }
    }

    private void safeReply(String chatId, String text) {
        try {
            bot.sendPlainText(chatId, text);
        } catch (TelegramApiException e) {
            log.warn("Failed to reply chatId={} status={}: {}", chatId, e.statusCode(), e.getMessage());
        }
    }
}
