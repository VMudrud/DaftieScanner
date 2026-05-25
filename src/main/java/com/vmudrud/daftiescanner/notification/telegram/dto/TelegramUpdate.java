package com.vmudrud.daftiescanner.notification.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdate(
        @JsonProperty("update_id") long updateId,
        Message message
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            @JsonProperty("message_id") long messageId,
            Chat chat,
            String text,
            From from
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chat(long id) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record From(long id, String username) {}
}
