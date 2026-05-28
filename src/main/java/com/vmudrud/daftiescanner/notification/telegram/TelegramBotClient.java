package com.vmudrud.daftiescanner.notification.telegram;

import com.vmudrud.daftiescanner.notification.telegram.dto.GetUpdatesRequest;
import com.vmudrud.daftiescanner.notification.telegram.dto.GetUpdatesResponse;
import com.vmudrud.daftiescanner.notification.telegram.dto.InlineKeyboardMarkup;
import com.vmudrud.daftiescanner.notification.telegram.dto.SendMessageRequest;
import com.vmudrud.daftiescanner.notification.telegram.dto.TelegramUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Slf4j
@Component
@ConditionalOnTelegramEnabled
public class TelegramBotClient {

    private static final String METHOD_SEND_MESSAGE = "/sendMessage";
    private static final String METHOD_GET_UPDATES = "/getUpdates";
    private static final String PARSE_MODE_MARKDOWN_V2 = "MarkdownV2";
    private static final List<String> ALLOWED_MESSAGE_UPDATES = List.of("message");
    private static final int TRANSPORT_ERROR_STATUS = 0;

    private final RestClient restClient;

    public TelegramBotClient(@Qualifier("telegramRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Retryable(retryFor = ResourceAccessException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2))
    public void sendPlainText(String chatId, String text) {
        send(new SendMessageRequest(chatId, text, null, true, null));
    }

    @Retryable(retryFor = ResourceAccessException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2))
    public void sendMarkdown(String chatId, String markdownText, InlineKeyboardMarkup replyMarkup) {
        send(new SendMessageRequest(chatId, markdownText, PARSE_MODE_MARKDOWN_V2, false, replyMarkup));
    }

    // When retries are exhausted, the ResourceAccessException message carries the
    // token-bearing request URI; convert it to a redacted TelegramApiException so
    // callers never log the raw transport error.
    @Recover
    void recoverSendPlainText(ResourceAccessException e, String chatId, String text) {
        throw transportError(e);
    }

    @Recover
    void recoverSendMarkdown(ResourceAccessException e, String chatId, String markdownText, InlineKeyboardMarkup replyMarkup) {
        throw transportError(e);
    }

    private void send(SendMessageRequest body) {
        try {
            restClient.post()
                    .uri(METHOD_SEND_MESSAGE)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpStatusCodeException e) {
            throw new TelegramApiException(e.getStatusCode().value(),
                    TelegramLogSafe.redact("sendMessage failed: " + e.getResponseBodyAsString()));
        }
    }

    public List<TelegramUpdate> getUpdates(Long offset, int timeoutSeconds) {
        var body = new GetUpdatesRequest(offset, timeoutSeconds, ALLOWED_MESSAGE_UPDATES);
        try {
            GetUpdatesResponse response = restClient.post()
                    .uri(METHOD_GET_UPDATES)
                    .body(body)
                    .retrieve()
                    .body(GetUpdatesResponse.class);
            if (response == null || response.result() == null) {
                return List.of();
            }
            return response.result();
        } catch (HttpStatusCodeException e) {
            throw new TelegramApiException(e.getStatusCode().value(),
                    TelegramLogSafe.redact("getUpdates failed: " + e.getResponseBodyAsString()));
        } catch (RestClientException e) {
            throw transportError(e);
        }
    }

    private static TelegramApiException transportError(RestClientException e) {
        return new TelegramApiException(TRANSPORT_ERROR_STATUS,
                TelegramLogSafe.redact("transport error: " + e.getMessage()));
    }
}
