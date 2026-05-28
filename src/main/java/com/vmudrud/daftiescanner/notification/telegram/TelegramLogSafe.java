package com.vmudrud.daftiescanner.notification.telegram;

import java.util.regex.Pattern;

/**
 * Masks the Telegram bot token before it can reach a log sink. The token lives in
 * every request URI ({@code /bot<token>/method}), so transport-layer exceptions from
 * the HTTP client carry it in their messages and would otherwise persist in CloudWatch.
 */
final class TelegramLogSafe {

    // "/bot<token>" as it appears in api.telegram.org request URIs.
    private static final Pattern BOT_PATH_TOKEN = Pattern.compile("/bot[^/\\s\"]+");
    // A bare Telegram token ("<id>:<secret>") appearing anywhere else.
    private static final Pattern BARE_TOKEN = Pattern.compile("\\b\\d{6,}:[A-Za-z0-9_-]{30,}");
    private static final String BOT_PATH_MASK = "/bot***";
    private static final String TOKEN_MASK = "***";

    private TelegramLogSafe() {}

    static String redact(String text) {
        if (text == null) {
            return null;
        }
        String masked = BOT_PATH_TOKEN.matcher(text).replaceAll(BOT_PATH_MASK);
        return BARE_TOKEN.matcher(masked).replaceAll(TOKEN_MASK);
    }
}
