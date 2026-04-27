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
public class CursorItem {

    public static final String COL_TENANT_ID = "tenantId";

    @Getter(onMethod_ = {@DynamoDbPartitionKey})
    private String tenantId;

    private Long lastPostedAt;
    private Long lastListingId;
    private Long updatedAt;
}
