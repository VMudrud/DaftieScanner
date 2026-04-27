package com.vmudrud.daftiescanner.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class BlockDetector {

    private static final String CF_BROWSER_VERIFICATION = "cf-browser-verification";
    private static final String CF_CHL = "cf_chl";
    private static final int STATUS_RATE_LIMITED = 429;
    private static final int STATUS_FORBIDDEN = 403;

    public BlockStatus classify(Exception e) {
        return switch (e) {
            case HttpClientErrorException httpEx ->
                    classifyStatus(httpEx.getStatusCode().value(), httpEx.getResponseBodyAsString());
            case RestClientResponseException ignored -> BlockStatus.UNKNOWN;
            default -> BlockStatus.UNKNOWN;
        };
    }

    public BlockStatus classifyStatus(int statusCode, String body) {
        if (statusCode == STATUS_RATE_LIMITED) return BlockStatus.RATE_LIMITED;
        if (statusCode == STATUS_FORBIDDEN) return BlockStatus.BLOCKED;
        if (body != null && (body.contains(CF_BROWSER_VERIFICATION) || body.contains(CF_CHL))) {
            return BlockStatus.BLOCKED;
        }
        return BlockStatus.UNKNOWN;
    }
}
