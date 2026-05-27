package com.vmudrud.daftiescanner.notification.telegram;

import com.vmudrud.daftiescanner.notification.telegram.dto.GetUpdatesRequest;
import com.vmudrud.daftiescanner.notification.telegram.dto.GetUpdatesResponse;
import com.vmudrud.daftiescanner.notification.telegram.dto.SendMessageRequest;
import com.vmudrud.daftiescanner.notification.telegram.dto.TelegramUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Component
@ConditionalOnTelegramEnabled
public class TelegramBotClient {

    private static final String METHOD_SEND_MESSAGE = "/sendMessage";
    private static final String METHOD_GET_UPDATES = "/getUpdates";
    private static final String PARSE_MODE_MARKDOWN_V2 = "MarkdownV2";
    private static final List<String> ALLOWED_MESSAGE_UPDATES = List.of("message");

    private final RestClient restClient;

    public TelegramBotClient(@Qualifier("telegramRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public void sendPlainText(String chatId, String text) {
        send(new SendMessageRequest(chatId, text, null, true));
    }

    public void sendMarkdown(String chatId, String markdownText) {
        send(new SendMessageRequest(chatId, markdownText, PARSE_MODE_MARKDOWN_V2, false));
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
                    "sendMessage failed: " + e.getResponseBodyAsString());
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
                    "getUpdates failed: " + e.getResponseBodyAsString());
        }
    }
}
