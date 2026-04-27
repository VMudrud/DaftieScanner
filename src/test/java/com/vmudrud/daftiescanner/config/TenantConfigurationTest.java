package com.vmudrud.daftiescanner.config;

import com.vmudrud.daftiescanner.config.dto.FilterSpec;
import com.vmudrud.daftiescanner.config.dto.Tenant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TenantConfigurationTest {

    @Autowired
    @Qualifier("tenants")
    private List<Tenant> tenants;

    @Test
    void loads_only_enabled_tenants() {
        assertThat(tenants).hasSize(1);

        Tenant t = tenants.get(0);
        assertThat(t.id()).isEqualTo("1");
        assertThat(t.email()).isEqualTo("test@example.com");
        assertThat(t.filter().section()).isEqualTo("residential-to-rent");
        assertThat(t.filter().rentalPrice()).isEqualTo(new FilterSpec.Range(1200, 2300));
        assertThat(t.filter().numBeds()).isEqualTo(new FilterSpec.Range(1, 3));
        assertThat(t.filter().storedShapeIds()).containsExactlyInAnyOrder("42", "43");
    }
}
