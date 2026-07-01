package com.vmudrud.daftiescanner.notification.router;

import com.vmudrud.daftiescanner.common.listing.ListingResult;
import com.vmudrud.daftiescanner.common.tenant.FilterSpec;
import com.vmudrud.daftiescanner.common.tenant.Tenant;
import com.vmudrud.daftiescanner.notification.smtp.SmtpEmailNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotifierRouterTest {

    @Mock
    LoggingNotifier loggingNotifier;

    @Mock
    SmtpEmailNotifier smtpEmailNotifier;

    private NotifierRouter router;
    private List<ListingResult> listings;

    @BeforeEach
    void setUp() {
        when(loggingNotifier.channel()).thenReturn(LoggingNotifier.CHANNEL);
        when(smtpEmailNotifier.channel()).thenReturn(SmtpEmailNotifier.CHANNEL);
        router = new NotifierRouter(List.of(loggingNotifier, smtpEmailNotifier));
        // channel() is called during construction to build the channel map — reset invocation tracking
        clearInvocations(loggingNotifier, smtpEmailNotifier);
        listings = List.of(new ListingResult(1L, "Title", 1700000000000L, "€2,000 per month",
                null, null, null, "/listing/1", null, null, null, null, null, null, null));
    }

    @Test
    void notify_tenantWithEmailChannel_onlySmtpCalled() {
        var tenant = tenant(List.of("email"));

        router.notify(tenant, listings);

        verify(smtpEmailNotifier).notify(tenant, listings);
        verifyNoInteractions(loggingNotifier);
    }

    @Test
    void notify_tenantWithLoggingChannel_onlyLoggingCalled() {
        var tenant = tenant(List.of("logging"));

        router.notify(tenant, listings);

        verify(loggingNotifier).notify(tenant, listings);
        verifyNoInteractions(smtpEmailNotifier);
    }

    @Test
    void notify_tenantWithBothChannels_bothCalled() {
        var tenant = tenant(List.of("email", "logging"));

        router.notify(tenant, listings);

        verify(smtpEmailNotifier).notify(tenant, listings);
        verify(loggingNotifier).notify(tenant, listings);
    }

    @Test
    void notify_tenantWithEmptyChannels_fallsBackToLogging() {
        var tenant = tenant(List.of());

        router.notify(tenant, listings);

        verify(loggingNotifier).notify(tenant, listings);
        verifyNoInteractions(smtpEmailNotifier);
    }

    @Test
    void notify_tenantWithNullChannels_fallsBackToLogging() {
        var tenant = tenant(null);

        router.notify(tenant, listings);

        verify(loggingNotifier).notify(tenant, listings);
        verifyNoInteractions(smtpEmailNotifier);
    }

    @Test
    void notify_tenantWithUnknownChannel_logsWarnAndSkips() {
        var tenant = tenant(List.of("sms"));

        // Should not throw, should not call any notifier
        router.notify(tenant, listings);

        verifyNoInteractions(loggingNotifier);
        verifyNoInteractions(smtpEmailNotifier);
    }

    private Tenant tenant(List<String> notifiers) {
        return new Tenant("1", true, "test@example.com",
                new FilterSpec("residential-to-rent",
                        new FilterSpec.Range(1200, 2300),
                        new FilterSpec.Range(1, 3),
                        List.of("42"), List.of(), List.of()),
                notifiers);
    }
}
