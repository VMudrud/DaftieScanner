package com.vmudrud.daftiescanner.notifier;

import com.vmudrud.daftiescanner.client.dto.ListingResult;
import com.vmudrud.daftiescanner.config.dto.FilterSpec;
import com.vmudrud.daftiescanner.config.dto.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotifierRouterTest {

    @Mock
    LoggingNotifier loggingNotifier;

    private NotifierRouter router;
    private Tenant tenant;
    private ListingResult listing;

    @BeforeEach
    void setUp() {
        router = new NotifierRouter(loggingNotifier);
        tenant = new Tenant("1", true, "test@example.com",
                new FilterSpec("residential-to-rent",
                        new FilterSpec.Range(1200, 2300),
                        new FilterSpec.Range(1, 3),
                        List.of("42")));
        listing = new ListingResult(1L, "Title", 1700000000000L, "€2,000 per month",
                null, null, null, "/listing/1", null, null, null, null, null, null, null);
    }

    @Test
    void notify_delegatesToLoggingNotifier() {
        router.notify(tenant, listing);

        verify(loggingNotifier, times(1)).notify(tenant, listing);
    }

    @Test
    void notify_loggingNotifierThrows_propagatesException() {
        doThrow(new RuntimeException("notify failed")).when(loggingNotifier).notify(tenant, listing);

        assertThatThrownBy(() -> router.notify(tenant, listing))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("notify failed");
    }
}
