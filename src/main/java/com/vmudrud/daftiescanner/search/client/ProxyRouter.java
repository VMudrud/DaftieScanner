package com.vmudrud.daftiescanner.search.client;

import org.springframework.http.client.ClientHttpRequestFactory;

import java.time.Duration;

public interface ProxyRouter {

    ClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout);
}
