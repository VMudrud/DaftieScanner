package com.vmudrud.daftiescanner.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
class DaftClientConfiguration {

    private static final String BASE_URL = "https://gateway.daft.ie";

    private static final String HEADER_BRAND = "brand";
    private static final String HEADER_PLATFORM = "platform";

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";
    private static final String ACCEPT = "application/json, text/plain, */*";
    private static final String ACCEPT_LANGUAGE = "en-IE,en;q=0.9,en-US;q=0.8";
    private static final String REFERER = "https://www.daft.ie/property-for-rent/ireland";
    private static final String ORIGIN = "https://www.daft.ie";
    private static final String BRAND = "daft";
    private static final String PLATFORM = "web";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    @Bean
    public DaftClient daftClient(ProxyRouter proxyRouter) {
        var factory = proxyRouter.requestFactory(CONNECT_TIMEOUT, READ_TIMEOUT);
        var restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .defaultHeader(HttpHeaders.ACCEPT, ACCEPT)
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, ACCEPT_LANGUAGE)
                .defaultHeader(HttpHeaders.REFERER, REFERER)
                .defaultHeader(HttpHeaders.ORIGIN, ORIGIN)
                .defaultHeader(HEADER_BRAND, BRAND)
                .defaultHeader(HEADER_PLATFORM, PLATFORM)
                .build();
        return new DaftClient(restClient);
    }
}
