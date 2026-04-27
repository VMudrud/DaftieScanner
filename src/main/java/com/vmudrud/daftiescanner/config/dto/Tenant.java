package com.vmudrud.daftiescanner.config.dto;

public record Tenant(
        String id,
        boolean enabled,
        String email,
        FilterSpec filter
) {}
