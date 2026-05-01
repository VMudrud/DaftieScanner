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
    private static final String HEADER_VERSION = "version";
    private static final String HEADER_PRIORITY = "priority";
    private static final String HEADER_SEC_CH_UA = "sec-ch-ua";
    private static final String HEADER_SEC_CH_UA_MOBILE = "sec-ch-ua-mobile";
    private static final String HEADER_SEC_CH_UA_PLATFORM = "sec-ch-ua-platform";
    private static final String HEADER_SEC_FETCH_DEST = "sec-fetch-dest";
    private static final String HEADER_SEC_FETCH_MODE = "sec-fetch-mode";
    private static final String HEADER_SEC_FETCH_SITE = "sec-fetch-site";

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36";
    private static final String ACCEPT = "application/json";
    private static final String ACCEPT_LANGUAGE = "en-IE,en;q=0.9";

    // JDK HttpClient does not auto-decompress responses; request uncompressed payloads
    // so Jackson can parse them directly. Bandwidth is negligible for paged listings.
    private static final String ACCEPT_ENCODING = "identity";
    private static final String REFERER = "https://www.daft.ie/";
    private static final String ORIGIN = "https://www.daft.ie";
    private static final String BRAND = "daft";
    private static final String PLATFORM = "web";
    private static final String VERSION = "0";
    private static final String CACHE_CONTROL = "no-cache, no-store";
    private static final String PRAGMA = "no-cache";
    private static final String EXPIRES = "0";
    private static final String PRIORITY = "u=1, i";

    private static final String SEC_CH_UA = "\"Google Chrome\";v=\"147\", \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"147\"";
    private static final String SEC_CH_UA_MOBILE = "?0";
    private static final String SEC_CH_UA_PLATFORM = "\"Windows\"";
    private static final String SEC_FETCH_DEST = "empty";
    private static final String SEC_FETCH_MODE = "cors";
    private static final String SEC_FETCH_SITE = "same-site";

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
                .defaultHeader(HttpHeaders.ACCEPT_ENCODING, ACCEPT_ENCODING)
                .defaultHeader(HttpHeaders.REFERER, REFERER)
                .defaultHeader(HttpHeaders.ORIGIN, ORIGIN)
                .defaultHeader(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL)
                .defaultHeader(HttpHeaders.PRAGMA, PRAGMA)
                .defaultHeader(HttpHeaders.EXPIRES, EXPIRES)
                .defaultHeader(HEADER_BRAND, BRAND)
                .defaultHeader(HEADER_PLATFORM, PLATFORM)
                .defaultHeader(HEADER_VERSION, VERSION)
                .defaultHeader(HEADER_PRIORITY, PRIORITY)
                .defaultHeader(HEADER_SEC_CH_UA, SEC_CH_UA)
                .defaultHeader(HEADER_SEC_CH_UA_MOBILE, SEC_CH_UA_MOBILE)
                .defaultHeader(HEADER_SEC_CH_UA_PLATFORM, SEC_CH_UA_PLATFORM)
                .defaultHeader(HEADER_SEC_FETCH_DEST, SEC_FETCH_DEST)
                .defaultHeader(HEADER_SEC_FETCH_MODE, SEC_FETCH_MODE)
                .defaultHeader(HEADER_SEC_FETCH_SITE, SEC_FETCH_SITE)
                .build();
        return new DaftClient(restClient);
    }
}
