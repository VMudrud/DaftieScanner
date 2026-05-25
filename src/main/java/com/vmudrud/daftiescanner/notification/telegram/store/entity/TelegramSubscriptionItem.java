package com.vmudrud.daftiescanner.notification.telegram.store.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@Getter
@Setter
@NoArgsConstructor
@DynamoDbBean
public class TelegramSubscriptionItem {

    public static final String COL_CHAT_ID = "chatId";
    public static final String COL_EMAIL = "email";

    @Getter(onMethod_ = {@DynamoDbPartitionKey})
    private String chatId;

    private String email;
    private Long createdAt;
    private Long updatedAt;

    public static TelegramSubscriptionItem of(String chatId, String email, long now) {
        var item = new TelegramSubscriptionItem();
        item.setChatId(chatId);
        item.setEmail(email);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        return item;
    }

    public static TelegramSubscriptionItem emailPatch(String chatId, String newEmail, long now) {
        var item = new TelegramSubscriptionItem();
        item.setChatId(chatId);
        item.setEmail(newEmail);
        item.setUpdatedAt(now);
        return item;
    }
}
