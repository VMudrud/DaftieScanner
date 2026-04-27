package com.vmudrud.daftiescanner.store;

import com.vmudrud.daftiescanner.store.dto.Cursor;
import com.vmudrud.daftiescanner.store.entity.CursorItem;
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
import java.util.Optional;

@Slf4j
@Component
@ConditionalOnExpression("!'${daft.dynamo.cursor-table:}'.isBlank()")
class DynamoCursorStore implements CursorStore {

    private final DynamoDbClient rawClient;
    private final DynamoDbTable<CursorItem> table;

    public DynamoCursorStore(
            DynamoDbClient rawClient,
            DynamoDbEnhancedClient enhancedClient,
            @Value("${daft.dynamo.cursor-table}") String tableName) {
        this.rawClient = rawClient;
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(CursorItem.class));
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
                    KeySchemaElement.builder().attributeName(CursorItem.COL_TENANT_ID).keyType(KeyType.HASH).build()
                )
                .attributeDefinitions(
                    AttributeDefinition.builder().attributeName(CursorItem.COL_TENANT_ID).attributeType(ScalarAttributeType.S).build()
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
    public Optional<Cursor> load(String tenantId) {
        var item = table.getItem(Key.builder().partitionValue(tenantId).build());
        if (item == null) {
            return Optional.empty();
        }
        return Optional.of(new Cursor(item.getLastPostedAt(), item.getLastListingId(), item.getUpdatedAt()));
    }

    @Override
    public void save(String tenantId, long lastPostedAt, long lastListingId) {
        var item = new CursorItem();
        item.setTenantId(tenantId);
        item.setLastPostedAt(lastPostedAt);
        item.setLastListingId(lastListingId);
        item.setUpdatedAt(Instant.now().toEpochMilli());
        table.putItem(item);
    }
}
