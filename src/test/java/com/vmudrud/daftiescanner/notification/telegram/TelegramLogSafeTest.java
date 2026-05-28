package com.vmudrud.daftiescanner.notification.telegram;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramLogSafeTest {

    private static final String TOKEN = "8991126218:AAFRdkj9Y36qt5qPYovb_47XJpc-nyckcUQ";

    @Test
    void redact_masksTokenInRequestUri() {
        String message = "I/O error on POST request for "
                + "\"https://api.telegram.org/bot" + TOKEN + "/getUpdates\": null";

        String redacted = TelegramLogSafe.redact(message);

        assertThat(redacted).doesNotContain(TOKEN);
        assertThat(redacted).contains("/bot***/getUpdates");
    }

    @Test
    void redact_masksBareToken() {
        String redacted = TelegramLogSafe.redact("token is " + TOKEN + " done");

        assertThat(redacted).doesNotContain(TOKEN);
        assertThat(redacted).contains("***");
    }

    @Test
    void redact_leavesOrdinaryTextUntouched() {
        String message = "getUpdates failed: 401 Unauthorized";

        assertThat(TelegramLogSafe.redact(message)).isEqualTo(message);
    }

    @Test
    void redact_handlesNull() {
        assertThat(TelegramLogSafe.redact(null)).isNull();
    }
}
