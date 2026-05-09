package com.vmudrud.daftiescanner.notification;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class NotificationConfiguration {

    @Bean("notificationTaskExecutor")
    TaskExecutor notificationTaskExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setThreadNamePrefix("notify-thread-pool-");
        executor.initialize();
        return executor;
    }
}
