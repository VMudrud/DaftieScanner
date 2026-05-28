 package com.vmudrud.daftiescanner.notification.telegram.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InlineKeyboardButton(
        String text,
        @JsonProperty("copy_text") CopyTextButton copyText
) {
    public static InlineKeyboardButton copy(String label, String textToCopy) {
        return new InlineKeyboardButton(label, new CopyTextButton(textToCopy));
    }
}
