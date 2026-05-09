package com.vmudrud.daftiescanner.notification.router;

import com.vmudrud.daftiescanner.common.listing.ListingResult;
import com.vmudrud.daftiescanner.common.tenant.Tenant;

import java.util.List;

public interface Notifier {
    String channel();
    void notify(Tenant tenant, List<ListingResult> listings);
}
