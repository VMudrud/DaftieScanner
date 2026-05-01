package com.vmudrud.daftiescanner.store;

import com.vmudrud.daftiescanner.store.entity.AlertItem;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
@ConditionalOnExpression("!'${daft.dynamo.seen-table:}'.isBlank()")
public class AlertThrottle {

    static final long THROTTLE_WINDOW_SECONDS = 3600L;
    static final long TTL_HOURS = 24L;

    private final DynamoDbClient rawClient;
    private final DynamoDbEnhancedClient enhancedClient;

    @Value("${daft.dynamo.alerts-table:daftiescanner_alerts}")
    private String tableName;

    private DynamoDbTable<AlertItem> table;

    @PostConstruct
    void init() {
        table = enhancedClient.table(tableName, TableSchema.fromBean(AlertItem.class));
        ensureTable();
    }

    void ensureTable() {
        if (tableExists()) {
            return;
        }
        try {
            rawClient.createTable(r -> r
                .tableName(table.tableName())
                .keySchema(
                    KeySchemaElement.builder().attributeName(AlertItem.COL_ALERT_KEY).keyType(KeyType.HASH).build()
                )
                .attributeDefinitions(
                    AttributeDefinition.builder().attributeName(AlertItem.COL_ALERT_KEY).attributeType(ScalarAttributeType.S).build()
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

    public boolean tryFire(String alertKey) {
        var now = Instant.now();
        var existing = table.getItem(Key.builder().partitionValue(alertKey).build());
        if (existing != null && existing.getLastFiredAt() != null) {
            long secondsSinceLast = now.getEpochSecond() - (existing.getLastFiredAt() / 1000);
            if (secondsSinceLast < THROTTLE_WINDOW_SECONDS) {
                return false;
            }
        }
        var item = new AlertItem();
        item.setAlertKey(alertKey);
        item.setLastFiredAt(now.toEpochMilli());
        item.setTtl(now.plus(TTL_HOURS, ChronoUnit.HOURS).getEpochSecond());
        table.putItem(item);
        return true;
    }
}
