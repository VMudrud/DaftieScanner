package com.vmudrud.daftiescanner.notification.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnTelegramEnabled
class TelegramUpdatePoller implements SchedulingConfigurer {

    private static final Duration FAIL_BACKOFF = Duration.ofSeconds(10);

    @Value("${daft.telegram.poll-timeout-seconds:25}")
    private int pollTimeoutSeconds;

    private final TelegramBotClient bot;
    private final TelegramCommandHandler handler;
    private final ThreadPoolTaskScheduler telegramTaskScheduler;

    private volatile Long nextOffset = null;
    private volatile Instant nextRunAfter = Instant.EPOCH;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setScheduler(telegramTaskScheduler);
        registrar.addTriggerTask(this::pollOnce, ctx -> {
            var last = ctx.lastCompletion();
            var base = last != null ? last : Instant.now();
            return nextRunAfter.isAfter(base) ? nextRunAfter : base;
        });
        log.info("Scheduled Telegram update poller (timeout={}s)", pollTimeoutSeconds);
    }

    void pollOnce() {
        try {
            var updates = bot.getUpdates(nextOffset, pollTimeoutSeconds);
            for (var update : updates) {
                handler.handle(update);
                nextOffset = update.updateId() + 1;
            }
            nextRunAfter = Instant.EPOCH;
        } catch (TelegramApiException e) {
            log.warn("Telegram getUpdates failed status={}: {}", e.statusCode(), e.getMessage());
            nextRunAfter = Instant.now().plus(FAIL_BACKOFF);
        } catch (Exception e) {
            log.error("Unexpected error polling Telegram updates: {}", e.getMessage(), e);
            nextRunAfter = Instant.now().plus(FAIL_BACKOFF);
        }
    }
}
