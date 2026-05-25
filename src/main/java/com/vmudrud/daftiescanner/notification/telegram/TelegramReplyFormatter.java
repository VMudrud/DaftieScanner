package com.vmudrud.daftiescanner.notification.telegram;

import com.vmudrud.daftiescanner.notification.telegram.store.SubscriptionConflictException;

public final class TelegramReplyFormatter {

    private static final String MD2_RESERVED = "_*[]()~`>#+-=|{}.!";

    private TelegramReplyFormatter() {}

    public static String subscribed(String email) {
        return "Subscribed to listings for " + email + ".";
    }

    public static String changed(String newEmail) {
        return "Email updated to " + newEmail + ".";
    }

    public static String currentEmail(String email) {
        return "Currently linked: " + email;
    }

    public static String notSubscribed() {
        return "You are not subscribed. Use /subscribe <email>.";
    }

    public static String unsubscribed() {
        return "Unsubscribed. Use /subscribe <email> to start again.";
    }

    public static String unknownCommand() {
        return "Unknown command. Try /help.";
    }

    public static String genericError() {
        return "Something went wrong. Please try again later.";
    }

    public static String forConflict(SubscriptionConflictException.Reason reason) {
        return switch (reason) {
            case EMAIL_ALREADY_CLAIMED -> "That email is already linked to another Telegram account.";
            case CHAT_HAS_NO_SUBSCRIPTION -> "You are not subscribed. Use /subscribe <email> first.";
            case CHAT_ALREADY_SUBSCRIBED -> "This chat already has a subscription. Use /change to switch email.";
            case EMAIL_NOT_REGISTERED -> "This email is not registered with DaftieScanner.";
            case EMAIL_INVALID_FORMAT -> "Invalid email format. Example: name@example.com";
        };
    }

    public static String escapeMarkdownV2(String text) {
        if (text == null) {
            return "";
        }
        var sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (MD2_RESERVED.indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
