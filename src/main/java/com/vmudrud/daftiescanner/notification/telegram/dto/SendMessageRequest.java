package com.vmudrud.daftiescanner.notification.telegram.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SendMessageRequest(
        @JsonProperty("chat_id") String chatId,
        String text,
        @JsonProperty("parse_mode") String parseMode,
        @JsonProperty("disable_web_page_preview") Boolean disableWebPagePreview,
        @JsonProperty("reply_markup") InlineKeyboardMarkup replyMarkup
) {}
