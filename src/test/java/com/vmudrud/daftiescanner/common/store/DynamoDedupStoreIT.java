package com.vmudrud.daftiescanner.common.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@SpringBootTest
@Testcontainers
class DynamoDedupStoreIT {

    private static final int DYNAMO_PORT = 8000;
    private static final String DYNAMO_IMAGE = "amazon/dynamodb-local:2.5.2";
    private static final String TEST_TABLE = "daftiescanner_seen_it";
    private static final String CURSOR_TABLE = "daftiescanner_cursor_it";
    private static final String ALERTS_TABLE = "daftiescanner_alerts_it";
    private static final String TENANT_A = "tenantA";
    private static final String TENANT_B = "tenantB";

    // Each test gets its own pair of listing IDs so data never bleeds across tests.
    private static final AtomicLong ID_SEQ = new AtomicLong(1000);
    private long listing1;
    private long listing2;

    @Container
    @SuppressWarnings("resource")
    private static final GenericContainer<?> dynamo = new GenericContainer<>(DYNAMO_IMAGE)
            .withCommand("-jar DynamoDBLocal.jar -sharedDb -inMemory")
            .withExposedPorts(DYNAMO_PORT);

    @DynamicPropertySource
    private static void dynamoProperties(DynamicPropertyRegistry registry) {
        registry.add("daft.dynamo.endpoint",
            () -> "http://localhost:" + dynamo.getMappedPort(DYNAMO_PORT));
        registry.add("daft.dynamo.seen-table", () -> TEST_TABLE);
        registry.add("daft.dynamo.cursor-table", () -> CURSOR_TABLE);
        registry.add("daft.dynamo.alerts-table", () -> ALERTS_TABLE);
    }

    @Autowired
    private DedupStore store;

    @BeforeEach
    void assignUniqueListingIds() {
        listing1 = ID_SEQ.incrementAndGet();
        listing2 = ID_SEQ.incrementAndGet();
    }

    @Test
    void seen_returnsFalse_whenNotMarked() {
        assertThat(store.seen(TENANT_A, listing1)).isFalse();
    }

    @Test
    void markSeen_thenSeen_returnsTrue() {
        store.markSeen(TENANT_A, listing2, Instant.now());
        assertThat(store.seen(TENANT_A, listing2)).isTrue();
    }

    @Test
    void markSeen_isIdempotent() {
        assertThatNoException().isThrownBy(() -> {
            store.markSeen(TENANT_A, listing1, Instant.now());
            store.markSeen(TENANT_A, listing1, Instant.now());
        });
    }

    @Test
    void seen_returnsFalse_forDifferentTenant() {
        store.markSeen(TENANT_A, listing1, Instant.now());
        assertThat(store.seen(TENANT_B, listing1)).isFalse();
    }

    @Test
    void seen_returnsFalse_forDifferentListing() {
        store.markSeen(TENANT_A, listing1, Instant.now());
        assertThat(store.seen(TENANT_A, listing2)).isFalse();
    }

    @Test
    void notifiedBy_returnsFalse_whenNotMarked() {
        assertThat(store.notifiedBy("email", "user@example.com", listing1)).isFalse();
    }

    @Test
    void markNotifiedBy_thenNotifiedBy_returnsTrue() {
        store.markNotifiedBy("email", "user@example.com", listing1);
        assertThat(store.notifiedBy("email", "user@example.com", listing1)).isTrue();
    }

    @Test
    void notifiedBy_isIndependentFromSeenByTenant() {
        // marking tenant as seen does not affect channel notification dedup, and vice versa
        store.markSeen(TENANT_A, listing1, Instant.now());
        assertThat(store.notifiedBy("email", TENANT_A, listing1)).isFalse();

        store.markNotifiedBy("email", "other@example.com", listing2);
        assertThat(store.seen(TENANT_A, listing2)).isFalse();
    }

    @Test
    void notifiedBy_sharedAcrossTenantsWithSameDestination() {
        // simulates two tenants sharing the same email — only one notification should fire
        String sharedEmail = "shared@example.com";
        store.markNotifiedBy("email", sharedEmail, listing1);
        assertThat(store.notifiedBy("email", sharedEmail, listing1)).isTrue();
    }

    @Test
    void notifiedBy_isolatedAcrossChannels() {
        // same destination, different channels — marking one does not affect the other
        String chatId = "12345";
        store.markNotifiedBy("telegram", chatId, listing1);
        assertThat(store.notifiedBy("telegram", chatId, listing1)).isTrue();
        assertThat(store.notifiedBy("email", chatId, listing1)).isFalse();
    }
}
