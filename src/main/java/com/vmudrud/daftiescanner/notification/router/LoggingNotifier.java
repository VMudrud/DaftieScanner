package com.vmudrud.daftiescanner.notification.router;

import com.vmudrud.daftiescanner.common.listing.ListingResult;
import com.vmudrud.daftiescanner.common.tenant.Tenant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
class LoggingNotifier implements Notifier {

    private static final String DAFT_BASE = "https://www.daft.ie";

    public static final String CHANNEL = "logging";

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public void notify(Tenant tenant, List<ListingResult> listings) {
        listings.forEach(listing -> log.info("NOTIFY tenant={} id={} title={} price={} url={}{}",
                tenant.id(), listing.id(), listing.title(), listing.price(),
                DAFT_BASE, listing.seoFriendlyPath()));
    }
}
