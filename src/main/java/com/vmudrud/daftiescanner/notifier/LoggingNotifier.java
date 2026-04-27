package com.vmudrud.daftiescanner.notifier;

import com.vmudrud.daftiescanner.client.dto.ListingResult;
import com.vmudrud.daftiescanner.config.dto.Tenant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
class LoggingNotifier implements Notifier {

    private static final String DAFT_BASE = "https://www.daft.ie";

    @Override
    public void notify(Tenant tenant, ListingResult listing) {
        log.info("NOTIFY tenant={} id={} title={} price={} url={}{}",
                tenant.id(), listing.id(), listing.title(), listing.price(),
                DAFT_BASE, listing.seoFriendlyPath());
    }
}
