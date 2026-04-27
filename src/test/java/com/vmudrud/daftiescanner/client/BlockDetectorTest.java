package com.vmudrud.daftiescanner.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class BlockDetectorTest {

    private BlockDetector detector;

    @BeforeEach
    void setUp() {
        detector = new BlockDetector();
    }

    @Test
    void classify_403_returnsBlocked() {
        var ex = HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden", null, null, null);
        assertThat(detector.classify(ex)).isEqualTo(BlockStatus.BLOCKED);
    }

    @Test
    void classify_429_returnsRateLimited() {
        var ex = HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", null, null, null);
        assertThat(detector.classify(ex)).isEqualTo(BlockStatus.RATE_LIMITED);
    }

    @Test
    void classify_200WithCfBrowserVerification_returnsBlocked() {
        assertThat(detector.classifyStatus(200, "<div id=\"cf-browser-verification\">"))
                .isEqualTo(BlockStatus.BLOCKED);
    }

    @Test
    void classify_200WithCfChl_returnsBlocked() {
        assertThat(detector.classifyStatus(200, "action=\"/cdn-cgi/challenge-platform/cf_chl\""))
                .isEqualTo(BlockStatus.BLOCKED);
    }

    @Test
    void classify_500_returnsUnknown() {
        var ex = HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", null, null, null);
        assertThat(detector.classify(ex)).isEqualTo(BlockStatus.UNKNOWN);
    }

    @Test
    void classify_resourceAccessException_returnsUnknown() {
        var ex = new ResourceAccessException("Connection refused", new IOException("Connection refused"));
        assertThat(detector.classify(ex)).isEqualTo(BlockStatus.UNKNOWN);
    }

    @Test
    void classifyStatus_nullBody_doesNotThrow() {
        assertThat(detector.classifyStatus(200, null)).isEqualTo(BlockStatus.UNKNOWN);
    }

    @Test
    void classifyStatus_emptyBody_returnsUnknown() {
        assertThat(detector.classifyStatus(200, "")).isEqualTo(BlockStatus.UNKNOWN);
    }

    @Test
    void classify_403WithCfBody_returnsBlockedByStatus() {
        var ex = HttpClientErrorException.create(
                HttpStatus.FORBIDDEN, "Forbidden",
                null,
                "cf-browser-verification".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        assertThat(detector.classify(ex)).isEqualTo(BlockStatus.BLOCKED);
    }
}
