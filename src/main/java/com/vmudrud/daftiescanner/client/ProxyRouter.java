package com.vmudrud.daftiescanner.client;

import org.springframework.http.client.ClientHttpRequestFactory;

import java.time.Duration;

public interface ProxyRouter {

    ClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout);
}
