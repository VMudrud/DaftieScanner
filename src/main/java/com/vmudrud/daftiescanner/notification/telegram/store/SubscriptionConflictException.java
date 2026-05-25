package com.vmudrud.daftiescanner.notification.telegram.store;

public class SubscriptionConflictException extends RuntimeException {

    public enum Reason {
        EMAIL_ALREADY_CLAIMED,
        CHAT_HAS_NO_SUBSCRIPTION,
        CHAT_ALREADY_SUBSCRIBED,
        EMAIL_NOT_REGISTERED,
        EMAIL_INVALID_FORMAT
    }

    private final Reason reason;

    public SubscriptionConflictException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
