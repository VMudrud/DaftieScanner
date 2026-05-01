package com.vmudrud.daftiescanner.store.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@Getter
@Setter
@NoArgsConstructor
@DynamoDbBean
public class AlertItem {

    public static final String COL_ALERT_KEY = "alertKey";

    @Getter(onMethod_ = {@DynamoDbPartitionKey})
    private String alertKey;

    private Long lastFiredAt;
    private Long ttl;
}
