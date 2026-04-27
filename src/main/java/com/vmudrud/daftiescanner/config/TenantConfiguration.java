package com.vmudrud.daftiescanner.config;

import com.vmudrud.daftiescanner.config.dto.FilterSpec;
import com.vmudrud.daftiescanner.config.dto.Tenant;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Configuration
@Slf4j
class TenantConfiguration {

    private static final String PROP_ACTIVE = "tenants.active";
    private static final String PROP_SLOT_PREFIX = "tenant.";

    @Bean("tenants")
    public List<Tenant> tenants(Environment environment) {
        var binder = Binder.get(environment);
        var activeIds = bindActiveIds(binder);
        assertActiveConfigured(activeIds);

        var result = new ArrayList<Tenant>();
        activeIds.forEach(id -> buildTenant(binder, id).ifPresent(result::add));

        assertNonEmpty(result);
        logLoaded(result);
        return result;
    }

    private List<String> bindActiveIds(Binder binder) {
        return binder.bind(PROP_ACTIVE, Bindable.listOf(String.class)).orElse(List.of());
    }

    private Optional<Tenant> buildTenant(Binder binder, String id) {
        var slot = binder.bind(PROP_SLOT_PREFIX + id, TenantSlot.class).orElseGet(TenantSlot::new);
        if (!slot.isEnabled()) return Optional.empty();
        TenantSlotValidator.validate(id, slot);
        return Optional.of(new Tenant(id, true, slot.getEmail(), toFilterSpec(slot)));
    }

    private FilterSpec toFilterSpec(TenantSlot slot) {
        return new FilterSpec(
                slot.getSection(),
                new FilterSpec.Range(slot.getRentalPriceMin(), slot.getRentalPriceMax()),
                new FilterSpec.Range(slot.getNumBedsMin(), slot.getNumBedsMax()),
                slot.getStoredShapeIds()
        );
    }

    private void assertActiveConfigured(List<String> activeIds) {
        if (activeIds.isEmpty()) {
            throw new IllegalStateException("No active tenants configured. Set TENANTS_ACTIVE.");
        }
    }

    private void assertNonEmpty(List<Tenant> tenants) {
        if (tenants.isEmpty()) {
            throw new IllegalStateException(
                    "No enabled tenants in TENANTS_ACTIVE list. Set TENANT_N_ENABLED=true.");
        }
    }

    private void logLoaded(List<Tenant> tenants) {
        log.info("Loaded {} enabled tenant(s): [{}]",
                tenants.size(),
                tenants.stream().map(Tenant::id).collect(Collectors.joining(", ")));
    }

    @Data
    public static class TenantSlot {
        private static final String DEFAULT_SECTION = "residential-to-rent";

        private boolean enabled;
        private String email;
        private String section = DEFAULT_SECTION;
        private int rentalPriceMin;
        private int rentalPriceMax;
        private int numBedsMin;
        private int numBedsMax;
        private List<String> storedShapeIds = List.of();
    }
}
