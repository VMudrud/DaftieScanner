package com.vmudrud.daftiescanner.notification.telegram.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record InlineKeyboardMarkup(
        @JsonProperty("inline_keyboard") List<List<InlineKeyboardButton>> inlineKeyboard
) {
    public static InlineKeyboardMarkup singleButton(InlineKeyboardButton button) {
        return new InlineKeyboardMarkup(List.of(List.of(button)));
    }
}
