package com.vmudrud.daftiescanner.notification.telegram;

import com.vmudrud.daftiescanner.common.tenant.FilterSpec;
import com.vmudrud.daftiescanner.common.tenant.Tenant;
import com.vmudrud.daftiescanner.notification.telegram.store.SubscriptionConflictException;
import com.vmudrud.daftiescanner.notification.telegram.store.SubscriptionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramSubscriptionServiceTest {

    private static final String CHAT_ID = "9001";
    private static final String KNOWN_EMAIL = "tenant1@example.com";
    private static final String OTHER_KNOWN_EMAIL = "tenant2@example.com";

    @Mock
    SubscriptionStore store;

    private TelegramSubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new TelegramSubscriptionService(
                List.of(tenant("1", KNOWN_EMAIL), tenant("2", OTHER_KNOWN_EMAIL)),
                store);
    }

    @Test
    void subscribe_happyPath_claimsAndLogs() {
        service.subscribe(CHAT_ID, KNOWN_EMAIL);

        verify(store).claim(CHAT_ID, KNOWN_EMAIL);
    }

    @Test
    void subscribe_emailUnknown_throwsNotRegistered() {
        assertThatThrownBy(() -> service.subscribe(CHAT_ID, "stranger@example.com"))
                .isInstanceOf(SubscriptionConflictException.class)
                .extracting("reason")
                .isEqualTo(SubscriptionConflictException.Reason.EMAIL_NOT_REGISTERED);
        verifyNoInteractions(store);
    }

    @Test
    void subscribe_emailInvalidFormat_throwsInvalidFormat() {
        assertThatThrownBy(() -> service.subscribe(CHAT_ID, "not-an-email"))
                .isInstanceOf(SubscriptionConflictException.class)
                .extracting("reason")
                .isEqualTo(SubscriptionConflictException.Reason.EMAIL_INVALID_FORMAT);
        verifyNoInteractions(store);
    }

    @Test
    void subscribe_storeReportsChatAlreadySubscribed_propagates() {
        doThrow(new SubscriptionConflictException(SubscriptionConflictException.Reason.CHAT_ALREADY_SUBSCRIBED))
                .when(store).claim(CHAT_ID, KNOWN_EMAIL);

        assertThatThrownBy(() -> service.subscribe(CHAT_ID, KNOWN_EMAIL))
                .isInstanceOf(SubscriptionConflictException.class)
                .extracting("reason")
                .isEqualTo(SubscriptionConflictException.Reason.CHAT_ALREADY_SUBSCRIBED);
    }

    @Test
    void changeEmail_happyPath_callsStoreChange() {
        when(store.emailByChatId(CHAT_ID)).thenReturn(Optional.of(KNOWN_EMAIL));

        service.changeEmail(CHAT_ID, OTHER_KNOWN_EMAIL);

        verify(store).change(CHAT_ID, KNOWN_EMAIL, OTHER_KNOWN_EMAIL);
    }

    @Test
    void changeEmail_sameEmail_isNoop() {
        when(store.emailByChatId(CHAT_ID)).thenReturn(Optional.of(KNOWN_EMAIL));

        service.changeEmail(CHAT_ID, KNOWN_EMAIL);

        verify(store, never()).change(any(), any(), any());
    }

    @Test
    void changeEmail_notSubscribed_throwsNoSubscription() {
        when(store.emailByChatId(CHAT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeEmail(CHAT_ID, KNOWN_EMAIL))
                .isInstanceOf(SubscriptionConflictException.class)
                .extracting("reason")
                .isEqualTo(SubscriptionConflictException.Reason.CHAT_HAS_NO_SUBSCRIPTION);
    }

    @Test
    void changeEmail_newEmailNotRegistered_throwsNotRegistered() {
        assertThatThrownBy(() -> service.changeEmail(CHAT_ID, "stranger@example.com"))
                .isInstanceOf(SubscriptionConflictException.class)
                .extracting("reason")
                .isEqualTo(SubscriptionConflictException.Reason.EMAIL_NOT_REGISTERED);
        verifyNoInteractions(store);
    }

    @Test
    void unsubscribe_happyPath_callsStoreRelease() {
        when(store.emailByChatId(CHAT_ID)).thenReturn(Optional.of(KNOWN_EMAIL));

        service.unsubscribe(CHAT_ID);

        verify(store).release(CHAT_ID, KNOWN_EMAIL);
    }

    @Test
    void unsubscribe_notSubscribed_throwsNoSubscription() {
        when(store.emailByChatId(CHAT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unsubscribe(CHAT_ID))
                .isInstanceOf(SubscriptionConflictException.class)
                .extracting("reason")
                .isEqualTo(SubscriptionConflictException.Reason.CHAT_HAS_NO_SUBSCRIPTION);
    }

    @Test
    void currentEmail_returnsStoreLookup() {
        when(store.emailByChatId(CHAT_ID)).thenReturn(Optional.of(KNOWN_EMAIL));

        assertThat(service.currentEmail(CHAT_ID)).contains(KNOWN_EMAIL);
    }

    private Tenant tenant(String id, String email) {
        return new Tenant(id, true, email,
                new FilterSpec("residential-to-rent",
                        new FilterSpec.Range(1, 9999),
                        new FilterSpec.Range(1, 5),
                        List.of("42"), List.of()),
                List.of("telegram"));
    }
}
