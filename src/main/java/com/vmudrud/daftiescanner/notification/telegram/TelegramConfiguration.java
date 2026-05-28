package com.vmudrud.daftiescanner.notification.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableScheduling
@ConditionalOnTelegramEnabled
class TelegramConfiguration {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(35);

    @Value("${daft.telegram.api-base:https://api.telegram.org}")
    private String apiBase;

    @Value("${daft.telegram.token}")
    private String token;

    @Bean
    RestClient telegramRestClient() {
        // HTTP/1.1 avoids a JDK HTTP/2 race where a half-closed keep-alive stream
        // surfaces as EOFException mid-response from api.telegram.org.
        var httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl(apiBase + "/bot" + token)
                .build();
    }

    @Bean(destroyMethod = "shutdown")
    ThreadPoolTaskScheduler telegramTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("telegram-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }
}
