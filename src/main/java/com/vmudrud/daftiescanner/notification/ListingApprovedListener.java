package com.vmudrud.daftiescanner.notification;

import com.vmudrud.daftiescanner.common.event.NewListingsFoundEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(EmailNotificationGuard.class)
public class ListingApprovedListener {

    private final EmailNotificationGuard emailGuard;

    @Async
    @EventListener
    public void onNewListings(NewListingsFoundEvent event) {
        if (event.listings().isEmpty()) {
            return;
        }
        int notified = emailGuard.tryNotify(event.tenant(), event.listings());
        log.debug("tenant={} notified={} of {} listings",
                event.tenant().id(), notified, event.listings().size());
    }
}
