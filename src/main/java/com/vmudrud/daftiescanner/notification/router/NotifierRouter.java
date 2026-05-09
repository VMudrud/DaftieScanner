package com.vmudrud.daftiescanner.notification.router;

import com.vmudrud.daftiescanner.common.listing.ListingResult;
import com.vmudrud.daftiescanner.common.tenant.Tenant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Primary
@Component
class NotifierRouter implements Notifier {

    private final Map<String, Notifier> byChannel;
    private final Notifier fallback;

    NotifierRouter(List<Notifier> notifiers) {
        this.byChannel = notifiers.stream()
                .filter(n -> !(n instanceof NotifierRouter))
                .collect(Collectors.toMap(Notifier::channel, Function.identity()));
        this.fallback = byChannel.getOrDefault(LoggingNotifier.CHANNEL,
                notifiers.stream()
                        .filter(n -> !(n instanceof NotifierRouter))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No Notifier beans registered")));
    }

    @Override
    public String channel() {
        return "router";
    }

    @Override
    public void notify(Tenant tenant, List<ListingResult> listings) {
        List<String> channels = tenant.notifiers();
        if (channels == null || channels.isEmpty()) {
            fallback.notify(tenant, listings);
            return;
        }
        channels.forEach(ch -> {
            var notifier = byChannel.get(ch);
            if (notifier == null) {
                log.warn("tenant={} requested unknown channel='{}' — skipping", tenant.id(), ch);
            } else {
                notifier.notify(tenant, listings);
            }
        });
    }
}
