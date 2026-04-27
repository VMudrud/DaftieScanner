package com.vmudrud.daftiescanner.notifier;

import com.vmudrud.daftiescanner.client.dto.ListingResult;
import com.vmudrud.daftiescanner.config.dto.Tenant;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
class NotifierRouter implements Notifier {

    private final LoggingNotifier loggingNotifier;

    NotifierRouter(LoggingNotifier loggingNotifier) {
        this.loggingNotifier = loggingNotifier;
    }

    @Override
    public void notify(Tenant tenant, ListingResult listing) {
        resolve(tenant, listing).notify(tenant, listing);
    }

    //TODO Add resolve logic for after different notifiers will be added (M11)
    private Notifier resolve(Tenant tenant, ListingResult listing) {
        return loggingNotifier;
    }
}
