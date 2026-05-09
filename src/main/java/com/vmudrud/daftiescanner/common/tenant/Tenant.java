package com.vmudrud.daftiescanner.common.tenant;

import java.util.List;

public record Tenant(
        String id,
        boolean enabled,
        String email,
        FilterSpec filter,
        List<String> notifiers
) {}
