package com.vmudrud.daftiescanner.config;

import lombok.experimental.UtilityClass;

@UtilityClass
public class TenantSlotValidator {

    private static final String ENV_PREFIX = "TENANT_";
    private static final String REQUIRED_SUFFIX = " is required for an enabled tenant";

    public void validate(String id, TenantConfiguration.TenantSlot slot) {
        String prefix = ENV_PREFIX + id.toUpperCase() + "_";
        require(slot.getEmail() != null && !slot.getEmail().isBlank(), prefix + "EMAIL");
        require(slot.getRentalPriceMax() > 0, prefix + "RENTAL_PRICE_MAX");
        require(slot.getNumBedsMax() > 0, prefix + "NUM_BEDS_MAX");
        require(!slot.getStoredShapeIds().isEmpty(), prefix + "STORED_SHAPE_IDS");
    }

    private void require(boolean condition, String field) {
        if (!condition) throw new IllegalStateException(field + REQUIRED_SUFFIX);
    }
}
