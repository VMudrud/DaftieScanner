package com.vmudrud.daftiescanner.scanner;

import com.vmudrud.daftiescanner.config.dto.Tenant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.List;

@Configuration
@EnableScheduling
@ConditionalOnExpression("!'${daft.dynamo.seen-table:}'.isBlank()")
class ScannerConfiguration {

    @Bean(destroyMethod = "shutdown")
    ThreadPoolTaskScheduler scannerTaskScheduler(@Qualifier("tenants") List<Tenant> tenants) {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(Math.max(tenants.size(), 1));
        scheduler.setThreadNamePrefix("scanner-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }
}
