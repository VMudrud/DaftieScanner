package com.vmudrud.daftiescanner.store;

import com.vmudrud.daftiescanner.store.entity.AlertItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.CreateTableResponse;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.waiters.DynamoDbWaiter;

import java.time.Instant;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
class AlertThrottleTest {

    private static final String TABLE_NAME = "daftiescanner_alerts";
    private static final String ALERT_KEY  = "tenant1:block_detected";

    @Mock DynamoDbClient rawClient;
    @Mock DynamoDbEnhancedClient enhancedClient;
    @Mock DynamoDbTable<AlertItem> table;

    private AlertThrottle throttle;

    @BeforeEach
    void setUp() {
        when(enhancedClient.table(eq(TABLE_NAME), any(TableSchema.class))).thenReturn((DynamoDbTable) table);
        when(table.tableName()).thenReturn(TABLE_NAME);
        // Table exists — ensureTable() returns early
        doReturn(DescribeTableResponse.builder().build())
                .when(rawClient).describeTable(any(Consumer.class));

        throttle = new AlertThrottle(rawClient, enhancedClient);
        ReflectionTestUtils.setField(throttle, "tableName", TABLE_NAME);
        throttle.init();
    }

    @Test
    void tryFire_noExistingItem_returnsTrue_writesItem() {
        when(table.getItem(any(Key.class))).thenReturn(null);

        boolean result = throttle.tryFire(ALERT_KEY);

        assertThat(result).isTrue();
        var captor = ArgumentCaptor.forClass(AlertItem.class);
        verify(table).putItem(captor.capture());
        assertThat(captor.getValue().getAlertKey()).isEqualTo(ALERT_KEY);
        assertThat(captor.getValue().getLastFiredAt()).isPositive();
        assertThat(captor.getValue().getTtl()).isPositive();
    }

    @Test
    void tryFire_existingItemWithinWindow_returnsFalse_doesNotWrite() {
        var existing = new AlertItem();
        existing.setAlertKey(ALERT_KEY);
        existing.setLastFiredAt(Instant.now().toEpochMilli()); // fired just now → 0s since last
        when(table.getItem(any(Key.class))).thenReturn(existing);

        boolean result = throttle.tryFire(ALERT_KEY);

        assertThat(result).isFalse();
        verify(table, never()).putItem(any(AlertItem.class));
    }

    @Test
    void tryFire_existingItemJustInsideWindow_returnsFalse() {
        var existing = new AlertItem();
        existing.setAlertKey(ALERT_KEY);
        // 1 second short of the 1-hour window
        existing.setLastFiredAt(Instant.now().minusSeconds(AlertThrottle.THROTTLE_WINDOW_SECONDS - 1).toEpochMilli());
        when(table.getItem(any(Key.class))).thenReturn(existing);

        boolean result = throttle.tryFire(ALERT_KEY);

        assertThat(result).isFalse();
        verify(table, never()).putItem(any(AlertItem.class));
    }

    @Test
    void tryFire_existingItemOutsideWindow_returnsTrue_writesItem() {
        var existing = new AlertItem();
        existing.setAlertKey(ALERT_KEY);
        // fired more than 1 hour ago
        existing.setLastFiredAt(Instant.now().minusSeconds(AlertThrottle.THROTTLE_WINDOW_SECONDS + 60).toEpochMilli());
        when(table.getItem(any(Key.class))).thenReturn(existing);

        boolean result = throttle.tryFire(ALERT_KEY);

        assertThat(result).isTrue();
        verify(table).putItem(any(AlertItem.class));
    }

    @Test
    void tryFire_differentAlertKeys_independentThrottling() {
        when(table.getItem(any(Key.class))).thenReturn(null);

        boolean r1 = throttle.tryFire("tenant1:block_detected");
        boolean r2 = throttle.tryFire("tenant2:block_detected");

        assertThat(r1).isTrue();
        assertThat(r2).isTrue();
        verify(table, times(2)).putItem(any(AlertItem.class));
    }

    @Test
    void ensureTable_tableNotFound_callsCreateTable() {
        doThrow(ResourceNotFoundException.builder().message("not found").build())
                .when(rawClient).describeTable(any(Consumer.class));
        doReturn(CreateTableResponse.builder().build())
                .when(rawClient).createTable(any(Consumer.class));
        var waiter = mock(DynamoDbWaiter.class);
        when(rawClient.waiter()).thenReturn(waiter);
        doReturn(null).when(waiter).waitUntilTableExists(any(Consumer.class));

        throttle.ensureTable();

        verify(rawClient).createTable(any(Consumer.class));
    }
}
