package com.vmudrud.daftiescanner.common.event;

import com.vmudrud.daftiescanner.common.listing.ListingResult;
import com.vmudrud.daftiescanner.common.tenant.Tenant;

import java.util.List;

public record NewListingsFoundEvent(Tenant tenant, List<ListingResult> listings) {}
