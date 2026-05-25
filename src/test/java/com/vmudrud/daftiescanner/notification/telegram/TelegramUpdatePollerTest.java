package com.vmudrud.daftiescanner.notification.telegram;

import com.vmudrud.daftiescanner.notification.telegram.dto.TelegramUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramUpdatePollerTest {

    @Mock
    TelegramBotClient bot;
    @Mock
    TelegramCommandHandler handler;
    @Mock
    ThreadPoolTaskScheduler telegramTaskScheduler;

    @InjectMocks
    TelegramUpdatePoller poller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(poller, "pollTimeoutSeconds", 25);
    }

    @Test
    void pollOnce_emptyUpdates_noHandlerCallsNoOffsetChange() {
        when(bot.getUpdates(isNull(), eq(25))).thenReturn(List.of());

        poller.pollOnce();
        poller.pollOnce();

        verify(bot, times(2)).getUpdates(isNull(), eq(25));
        verifyNoInteractions(handler);
    }

    @Test
    void pollOnce_twoUpdates_handlerCalledTwiceAndOffsetAdvances() {
        var u1 = new TelegramUpdate(100L, msg(1L, "/start"));
        var u2 = new TelegramUpdate(101L, msg(2L, "/help"));
        when(bot.getUpdates(isNull(), eq(25))).thenReturn(List.of(u1, u2));

        poller.pollOnce();

        verify(handler).handle(u1);
        verify(handler).handle(u2);

        // next poll should pass offset = lastUpdateId + 1 = 102
        when(bot.getUpdates(eq(102L), eq(25))).thenReturn(List.of());
        poller.pollOnce();
        verify(bot).getUpdates(eq(102L), eq(25));
    }

    @Test
    void pollOnce_telegramApiException_swallowedNoOffsetChange() {
        when(bot.getUpdates(isNull(), eq(25)))
                .thenThrow(new TelegramApiException(502, "bad gateway"));

        poller.pollOnce();
        // Next call still uses null offset since previous failed
        poller.pollOnce();

        verify(bot, times(2)).getUpdates(isNull(), eq(25));
    }

    private TelegramUpdate.Message msg(long messageId, String text) {
        return new TelegramUpdate.Message(messageId, new TelegramUpdate.Chat(9001L), text,
                new TelegramUpdate.From(9001L, "tester"));
    }
}
