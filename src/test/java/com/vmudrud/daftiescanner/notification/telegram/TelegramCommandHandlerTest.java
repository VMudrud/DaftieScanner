package com.vmudrud.daftiescanner.notification.telegram;

import com.vmudrud.daftiescanner.notification.telegram.dto.TelegramUpdate;
import com.vmudrud.daftiescanner.notification.telegram.store.SubscriptionConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramCommandHandlerTest {

    private static final long CHAT_ID = 9001L;
    private static final String CHAT_ID_STR = "9001";

    @Mock
    TelegramSubscriptionService service;

    @Mock
    TelegramBotClient bot;

    private TelegramCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TelegramCommandHandler(service, bot);
        ReflectionTestUtils.setField(handler, "adminContact", "@TestAdmin");
    }

    @Test
    void start_repliesWithHelp() {
        handler.handle(update("/start"));

        var captor = ArgumentCaptor.forClass(String.class);
        verify(bot).sendPlainText(eq(CHAT_ID_STR), captor.capture());
        assertThat(captor.getValue())
                .contains("/subscribe")
                .contains("/help")
                .contains("Admin: @TestAdmin");
    }

    @Test
    void help_repliesWithHelp() {
        handler.handle(update("/help"));

        verify(bot).sendPlainText(eq(CHAT_ID_STR), contains("/subscribe"));
    }

    @Test
    void subscribe_callsServiceAndReplies() {
        handler.handle(update("/subscribe foo@bar.com"));

        verify(service).subscribe(CHAT_ID_STR, "foo@bar.com");
        verify(bot).sendPlainText(eq(CHAT_ID_STR), contains("Subscribed"));
    }

    @Test
    void subscribe_emailAlreadyClaimed_repliesWithReason() {
        doThrow(new SubscriptionConflictException(SubscriptionConflictException.Reason.EMAIL_ALREADY_CLAIMED))
                .when(service).subscribe(CHAT_ID_STR, "foo@bar.com");

        handler.handle(update("/subscribe foo@bar.com"));

        verify(bot).sendPlainText(eq(CHAT_ID_STR), contains("already linked"));
    }

    @Test
    void change_callsServiceAndReplies() {
        handler.handle(update("/change new@example.com"));

        verify(service).changeEmail(CHAT_ID_STR, "new@example.com");
        verify(bot).sendPlainText(eq(CHAT_ID_STR), contains("updated"));
    }

    @Test
    void email_subscribed_repliesWithCurrent() {
        when(service.currentEmail(CHAT_ID_STR)).thenReturn(Optional.of("a@b.com"));

        handler.handle(update("/email"));

        verify(bot).sendPlainText(eq(CHAT_ID_STR), contains("a@b.com"));
    }

    @Test
    void email_notSubscribed_repliesNotSubscribed() {
        when(service.currentEmail(CHAT_ID_STR)).thenReturn(Optional.empty());

        handler.handle(update("/email"));

        verify(bot).sendPlainText(eq(CHAT_ID_STR), contains("not subscribed"));
    }

    @Test
    void unsubscribe_callsServiceAndReplies() {
        handler.handle(update("/unsubscribe"));

        verify(service).unsubscribe(CHAT_ID_STR);
        verify(bot).sendPlainText(eq(CHAT_ID_STR), contains("Unsubscribed"));
    }

    @Test
    void unknownCommand_repliesUnknown() {
        handler.handle(update("/banana"));

        verify(bot).sendPlainText(eq(CHAT_ID_STR), contains("Unknown"));
        verifyNoInteractions(service);
    }

    @Test
    void noMessage_ignored() {
        var update = new TelegramUpdate(1, null);

        handler.handle(update);

        verifyNoInteractions(bot, service);
    }

    @Test
    void serviceThrowsGenericException_repliesGenericError() {
        doThrow(new RuntimeException("boom")).when(service).subscribe(any(), any());

        handler.handle(update("/subscribe foo@bar.com"));

        verify(bot).sendPlainText(eq(CHAT_ID_STR), contains("Something went wrong"));
    }

    private TelegramUpdate update(String text) {
        return new TelegramUpdate(42L, new TelegramUpdate.Message(1, new TelegramUpdate.Chat(CHAT_ID), text,
                new TelegramUpdate.From(CHAT_ID, "tester")));
    }
}
