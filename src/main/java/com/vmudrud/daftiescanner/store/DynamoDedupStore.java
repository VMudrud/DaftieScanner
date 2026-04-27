package com.vmudrud.daftiescanner.store;

import com.vmudrud.daftiescanner.store.entity.SeenItem;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@ConditionalOnExpression("!'${daft.dynamo.seen-table:}'.isBlank()")
class DynamoDedupStore implements DedupStore {

    private static final long TTL_DAYS = 30;
    private static final String EMAIL_KEY_PREFIX = "email:";

    private final DynamoDbClient rawClient;
    private final DynamoDbTable<SeenItem> table;

    public DynamoDedupStore(
            DynamoDbClient rawClient,
            DynamoDbEnhancedClient enhancedClient,
            @Value("${daft.dynamo.seen-table}") String tableName) {
        this.rawClient = rawClient;
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(SeenItem.class));
    }

    @PostConstruct
    private void ensureTable() {
        if (tableExists()) {
            return;
        }
        try {
            rawClient.createTable(r -> r
                .tableName(table.tableName())
                .keySchema(
                    KeySchemaElement.builder().attributeName(SeenItem.COL_TENANT_ID).keyType(KeyType.HASH).build(),
                    KeySchemaElement.builder().attributeName(SeenItem.COL_LISTING_ID).keyType(KeyType.RANGE).build()
                )
                .attributeDefinitions(
                    AttributeDefinition.builder().attributeName(SeenItem.COL_TENANT_ID).attributeType(ScalarAttributeType.S).build(),
                    AttributeDefinition.builder().attributeName(SeenItem.COL_LISTING_ID).attributeType(ScalarAttributeType.N).build()
                )
                .billingMode(BillingMode.PAY_PER_REQUEST)
            );
            rawClient.waiter().waitUntilTableExists(r -> r.tableName(table.tableName()));
        } catch (ResourceInUseException e) {
            log.error("Table {} creation conflict, likely concurrent startup: {}", table.tableName(), e.getMessage());
        }
    }

    private boolean tableExists() {
        try {
            rawClient.describeTable(r -> r.tableName(table.tableName()));
            return true;
        } catch (ResourceNotFoundException ignored) {
            return false;
        }
    }

    @Override
    public boolean seen(String tenantId, long listingId) {
        return table.getItem(Key.builder()
            .partitionValue(tenantId)
            .sortValue(listingId)
            .build()) != null;
    }

    @Override
    public boolean notifiedByEmail(String email, long listingId) {
        return seen(EMAIL_KEY_PREFIX + email, listingId);
    }

    @Override
    public void markNotifiedByEmail(String email, long listingId) {
        markSeen(EMAIL_KEY_PREFIX + email, listingId, Instant.now());
    }

    @Override
    public void markSeen(String tenantId, long listingId, Instant postedAt) {
        var now = Instant.now();
        var item = new SeenItem();
        item.setTenantId(tenantId);
        item.setListingId(listingId);
        item.setPostedAt(postedAt.toEpochMilli());
        item.setFirstSeenAt(now.toEpochMilli());
        item.setTtl(now.plus(TTL_DAYS, ChronoUnit.DAYS).getEpochSecond());
        table.putItem(item);
    }
}
