package com.vmudrud.daftiescanner.notification;

import com.vmudrud.daftiescanner.common.event.NewListingsFoundEvent;
import com.vmudrud.daftiescanner.common.listing.ListingResult;
import com.vmudrud.daftiescanner.common.tenant.FilterSpec;
import com.vmudrud.daftiescanner.common.tenant.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ListingApprovedListenerTest {

    @Mock
    NotificationGuard guard;

    private ListingApprovedListener listener;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        listener = new ListingApprovedListener(guard);
        tenant = new Tenant("1", true, "test@example.com",
                new FilterSpec("residential-to-rent",
                        new FilterSpec.Range(1200, 2300),
                        new FilterSpec.Range(1, 3),
                        List.of("42"), List.of()),
                List.of("email"));
    }

    @Test
    void onNewListings_happyPath_delegatesToGuardForAllListings() {
        var listings = List.of(
                listing(1001L, "Apt A"),
                listing(1002L, "Apt B")
        );

        listener.onNewListings(new NewListingsFoundEvent(tenant, listings));

        verify(guard).notify(tenant, listings);
    }

    @Test
    void onNewListings_emptyList_guardNotCalled() {
        listener.onNewListings(new NewListingsFoundEvent(tenant, List.of()));

        verify(guard, never()).notify(any(), any());
    }

    private ListingResult listing(long id, String title) {
        return new ListingResult(id, title, 1_700_000_000_000L, "€2,000 per month",
                null, null, null, "/listing/" + id, null, null, null, null, null, null, null);
    }
}
