package com.vmudrud.daftiescanner.scanner;

import java.time.Duration;
import java.time.Instant;

class TenantBackoff {

    private static final Duration[] STEPS = {
        Duration.ofHours(1), Duration.ofHours(4), Duration.ofHours(24)
    };

    private int level = 0;
    private Instant blockedUntil = Instant.EPOCH;

    void recordBlock() {
        blockedUntil = Instant.now().plus(STEPS[level]);
        if (level < STEPS.length - 1) {
            level++;
        }
    }

    void recordRateLimit() {
        blockedUntil = Instant.now().plus(STEPS[0]);
    }

    void reset() {
        level = 0;
        blockedUntil = Instant.EPOCH;
    }

    boolean isBlocked() {
        return Instant.now().isBefore(blockedUntil);
    }

    Instant blockedUntil() {
        return blockedUntil;
    }

    int level() {
        return level;
    }
}
