package com.vmudrud.daftiescanner.notifier;

import com.vmudrud.daftiescanner.client.dto.ListingResult;
import com.vmudrud.daftiescanner.config.dto.Tenant;

public interface Notifier {
    void notify(Tenant tenant, ListingResult listing);
}
