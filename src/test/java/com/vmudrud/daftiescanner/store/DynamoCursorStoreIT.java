package com.vmudrud.daftiescanner.store;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class DynamoCursorStoreIT {

    private static final int DYNAMO_PORT = 8000;
    private static final String DYNAMO_IMAGE = "amazon/dynamodb-local:2.5.2";
    private static final String SEEN_TABLE = "daftiescanner_seen_cursor_it";
    private static final String CURSOR_TABLE = "daftiescanner_cursor_it";
    private static final String TENANT_A = "tenantA";
    private static final String TENANT_B = "tenantB";
    private static final long POSTED_AT_1 = 1_700_000_000_000L;
    private static final long POSTED_AT_2 = 1_800_000_000_000L;
    private static final long LISTING_ID_1 = 5001L;
    private static final long LISTING_ID_2 = 5002L;

    @Container
    private static final GenericContainer<?> dynamo = new GenericContainer<>(DYNAMO_IMAGE)
            .withCommand("-jar DynamoDBLocal.jar -sharedDb -inMemory")
            .withExposedPorts(DYNAMO_PORT);

    @DynamicPropertySource
    private static void dynamoProperties(DynamicPropertyRegistry registry) {
        registry.add("daft.dynamo.endpoint",
            () -> "http://localhost:" + dynamo.getMappedPort(DYNAMO_PORT));
        registry.add("daft.dynamo.seen-table", () -> SEEN_TABLE);
        registry.add("daft.dynamo.cursor-table", () -> CURSOR_TABLE);
    }

    @Autowired
    private CursorStore store;

    @Test
    void load_returnsEmpty_forUnknownTenant() {
        assertThat(store.load("unknown-tenant-xyz")).isEmpty();
    }

    @Test
    void save_thenLoad_returnsCursor() {
        store.save(TENANT_A, POSTED_AT_1, LISTING_ID_1);

        var cursor = store.load(TENANT_A);

        assertThat(cursor).isPresent();
        assertThat(cursor.get().lastPostedAt()).isEqualTo(POSTED_AT_1);
        assertThat(cursor.get().lastListingId()).isEqualTo(LISTING_ID_1);
        assertThat(cursor.get().updatedAt()).isPositive();
    }

    @Test
    void save_overwritesExistingCursor() {
        store.save(TENANT_B, POSTED_AT_1, LISTING_ID_1);
        store.save(TENANT_B, POSTED_AT_2, LISTING_ID_2);

        var cursor = store.load(TENANT_B);

        assertThat(cursor).isPresent();
        assertThat(cursor.get().lastPostedAt()).isEqualTo(POSTED_AT_2);
        assertThat(cursor.get().lastListingId()).isEqualTo(LISTING_ID_2);
    }

    @Test
    void load_returnsDifferentCursors_forDifferentTenants() {
        store.save(TENANT_A, POSTED_AT_1, LISTING_ID_1);
        store.save(TENANT_B, POSTED_AT_2, LISTING_ID_2);

        var cursorA = store.load(TENANT_A);
        var cursorB = store.load(TENANT_B);

        assertThat(cursorA).isPresent();
        assertThat(cursorA.get().lastListingId()).isEqualTo(LISTING_ID_1);
        assertThat(cursorB).isPresent();
        assertThat(cursorB.get().lastListingId()).isEqualTo(LISTING_ID_2);
    }
}
