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
public class TelegramClaimItem {

    public static final String COL_EMAIL = "email";
    public static final String COL_CHAT_ID = "chatId";

    @Getter(onMethod_ = {@DynamoDbPartitionKey})
    private String email;

    private String chatId;
    private Long createdAt;

    public static TelegramClaimItem of(String email, String chatId, long now) {
        var item = new TelegramClaimItem();
        item.setEmail(email);
        item.setChatId(chatId);
        item.setCreatedAt(now);
        return item;
    }
}
