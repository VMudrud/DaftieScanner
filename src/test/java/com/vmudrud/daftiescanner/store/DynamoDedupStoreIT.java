package com.vmudrud.daftiescanner.store;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@SpringBootTest
@Testcontainers
class DynamoDedupStoreIT {

    private static final int DYNAMO_PORT = 8000;
    private static final String DYNAMO_IMAGE = "amazon/dynamodb-local:2.5.2";
    private static final String TEST_TABLE = "daftiescanner_seen_it";
    private static final String TENANT_A = "tenantA";
    private static final String TENANT_B = "tenantB";
    private static final long LISTING_1 = 1001L;
    private static final long LISTING_2 = 1002L;

    @Container
    private static final GenericContainer<?> dynamo = new GenericContainer<>(DYNAMO_IMAGE)
            .withCommand("-jar DynamoDBLocal.jar -sharedDb -inMemory")
            .withExposedPorts(DYNAMO_PORT);

    @DynamicPropertySource
    private static void dynamoProperties(DynamicPropertyRegistry registry) {
        registry.add("daft.dynamo.endpoint",
            () -> "http://localhost:" + dynamo.getMappedPort(DYNAMO_PORT));
        registry.add("daft.dynamo.seen-table", () -> TEST_TABLE);
    }

    @Autowired
    private DedupStore store;

    @Test
    void seen_returnsFalse_whenNotMarked() {
        assertThat(store.seen(TENANT_A, LISTING_1)).isFalse();
    }

    @Test
    void markSeen_thenSeen_returnsTrue() {
        store.markSeen(TENANT_A, LISTING_2, Instant.now());
        assertThat(store.seen(TENANT_A, LISTING_2)).isTrue();
    }

    @Test
    void markSeen_isIdempotent() {
        assertThatNoException().isThrownBy(() -> {
            store.markSeen(TENANT_A, 9999L, Instant.now());
            store.markSeen(TENANT_A, 9999L, Instant.now());
        });
    }

    @Test
    void seen_returnsFalse_forDifferentTenant() {
        store.markSeen(TENANT_A, LISTING_1, Instant.now());
        assertThat(store.seen(TENANT_B, LISTING_1)).isFalse();
    }

    @Test
    void seen_returnsFalse_forDifferentListing() {
        store.markSeen(TENANT_A, LISTING_1, Instant.now());
        assertThat(store.seen(TENANT_A, LISTING_2)).isFalse();
    }

    @Test
    void notifiedByEmail_returnsFalse_whenNotMarked() {
        assertThat(store.notifiedByEmail("user@example.com", LISTING_1)).isFalse();
    }

    @Test
    void markNotifiedByEmail_thenNotifiedByEmail_returnsTrue() {
        store.markNotifiedByEmail("user@example.com", LISTING_1);
        assertThat(store.notifiedByEmail("user@example.com", LISTING_1)).isTrue();
    }

    @Test
    void notifiedByEmail_isIndependentFromSeenByTenant() {
        // marking tenant as seen does not affect email notification dedup, and vice versa
        store.markSeen(TENANT_A, LISTING_1, Instant.now());
        assertThat(store.notifiedByEmail(TENANT_A, LISTING_1)).isFalse();

        store.markNotifiedByEmail("other@example.com", LISTING_2);
        assertThat(store.seen(TENANT_A, LISTING_2)).isFalse();
    }

    @Test
    void notifiedByEmail_sharedAcrossTenantsWithSameEmail() {
        // simulates two tenants sharing the same email — only one notification should fire
        String sharedEmail = "shared@example.com";
        store.markNotifiedByEmail(sharedEmail, LISTING_1);
        assertThat(store.notifiedByEmail(sharedEmail, LISTING_1)).isTrue();
    }
}
