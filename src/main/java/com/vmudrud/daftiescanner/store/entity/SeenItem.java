package com.vmudrud.daftiescanner.store.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@Getter
@Setter
@NoArgsConstructor
@DynamoDbBean
public class SeenItem {

    public static final String COL_TENANT_ID = "tenantId";
    public static final String COL_LISTING_ID = "listingId";

    @Getter(onMethod_ = {@DynamoDbPartitionKey})
    private String tenantId;

    @Getter(onMethod_ = {@DynamoDbSortKey})
    private Long listingId;

    private Long postedAt;
    private Long firstSeenAt;
    private Long ttl;
}
